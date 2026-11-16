package rustcraft.guns;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.EffectInstance;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.network.PacketDistributor;

/**
 * Серверная (авторитарная) логика оружия: проверка темпа стрельбы, хитскан, урон, перезарядка.
 * Регистрируется на MinecraftForge.EVENT_BUS в GunsModule.
 */
public final class GunServer {
    private GunServer() {}

    private static final class State {
        long nextShot;
        long lastShot;
        float bloom;
        int reloadSlot = -1;
        ItemStack reloadStack = ItemStack.EMPTY;
        long reloadEnd;
        int reloadTotal;
        long cycleSoundAt = -1;
        SoundEvent cycleSound;
        double lx, ly, lz;
        boolean hasPos;
        double speed;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(PlayerEntity p) {
        return STATES.computeIfAbsent(p.getUniqueID(), k -> new State());
    }


    static void handleFire(ServerPlayerEntity p, float yaw, float pitch, boolean aiming) {
        if (!p.isAlive() || p.isSpectator() || p.isHandActive()) return;
        ItemStack stack = p.getHeldItemMainhand();
        if (!(stack.getItem() instanceof GunItem)) return;
        GunStats st = ((GunItem) stack.getItem()).getStats();
        State s = state(p);
        ServerWorld world = p.getServerWorld();
        long now = world.getGameTime();

        if (s.reloadSlot >= 0) return;
        int ammo = GunItem.getAmmo(stack);
        if (ammo <= 0) return;
        // темп стрельбы: допускаем 1 тик сетевого джиттера, но средний темп не превышает заданный
        if (now + 1 < s.nextShot) return;
        s.nextShot = Math.max(now, s.nextShot) + st.fireInterval;
        if (now - s.lastShot <= st.fireInterval + 3) {
            s.bloom = Math.min(st.maxBloom, s.bloom + st.bloomPerShot);
        } else {
            s.bloom = 0;
        }
        s.lastShot = now;

        // направление: доверяем клиенту только в пределах 40° от серверного взгляда
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(MathHelper.wrapDegrees(yaw - p.rotationYaw)) > 40.0F
                || Math.abs(pitch - p.rotationPitch) > 40.0F) {
            yaw = p.rotationYaw;
            pitch = p.rotationPitch;
        }
        pitch = MathHelper.clamp(pitch, -90.0F, 90.0F);

        GunItem.setAmmo(stack, ammo - 1);

        float spread = st.hipSpread;
        if (aiming) spread *= st.adsMul;
        if (p.isSneaking()) spread *= 0.8F;
        if (!p.isOnGround() && !p.isPassenger() && !p.abilities.isFlying) spread *= 2.0F;
        if (s.speed > 0.12 && !p.isPassenger()) spread *= 1.5F;
        spread += s.bloom * (aiming ? 0.5F : 1.0F);

        Random rand = p.getRNG();
        world.playSound(null, p.getPosX(), p.getPosY(), p.getPosZ(), st.shotSound, SoundCategory.PLAYERS,
                st.shotVolume, st.shotPitch * (0.95F + rand.nextFloat() * 0.1F));
        world.playSound(null, p.getPosX(), p.getPosY(), p.getPosZ(), st.shotSound2, SoundCategory.PLAYERS,
                0.35F, st.shotPitch2 * (0.95F + rand.nextFloat() * 0.1F));
        if (st == GunStats.BOLT_RIFLE) {
            s.cycleSound = SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN;
            s.cycleSoundAt = now + 10;
        } else if (st == GunStats.PUMP_SHOTGUN) {
            s.cycleSound = SoundEvents.BLOCK_PISTON_CONTRACT;
            s.cycleSoundAt = now + 7;
        }

        Vector3d eye = p.getEyePosition(1.0F);

        if (st.isRocket()) {
            Vector3d dir = Vector3d.fromPitchYaw(pitch, yaw);
            RocketEntity rocket = new RocketEntity(world, p);
            rocket.setPosition(eye.x + dir.x * 0.8, eye.y - 0.15 + dir.y * 0.8, eye.z + dir.z * 0.8);
            rocket.shoot(dir.x, dir.y, dir.z, 1.9F, spread);
            world.addEntity(rocket);
            GunNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                    new GunNetwork.ShotFxMsg(p.getEntityId(), st.id, eye.x, eye.y, eye.z, new double[0], new byte[0]));
            return;
        }

        int n = st.pellets;
        double[] ends = new double[n * 3];
        byte[] types = new byte[n];
        Map<Entity, float[]> hits = new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            float py = pitch + (float) (rand.nextGaussian() * spread * 0.5);
            float yy = yaw + (float) (rand.nextGaussian() * spread * 0.5);
            Vector3d dir = Vector3d.fromPitchYaw(py, yy);
            double range = loadedRange(world, eye, dir, st.range);
            Vector3d end = eye.add(dir.scale(range));

            BlockRayTraceResult br = world.rayTraceBlocks(new RayTraceContext(eye, end,
                    RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, p));
            Vector3d stop = br.getType() == RayTraceResult.Type.MISS ? end : br.getHitVec();

            Entity target = null;
            Vector3d targetHit = null;
            double best = Double.MAX_VALUE;
            AxisAlignedBB area = new AxisAlignedBB(eye, stop).grow(1.0);
            List<Entity> list = world.getEntitiesInAABBexcluding(p, area,
                    e -> e != null && !e.isSpectator() && e.isAlive() && e.canBeCollidedWith());
            for (Entity e : list) {
                if (e.isRidingSameEntity(p)) continue; // своё транспортное средство / попутчики
                AxisAlignedBB bb = e.getBoundingBox().grow(e.getCollisionBorderSize());
                Vector3d hit;
                if (bb.contains(eye)) {
                    hit = eye;
                } else {
                    Optional<Vector3d> o = bb.rayTrace(eye, stop);
                    if (!o.isPresent()) continue;
                    hit = o.get();
                }
                double d = eye.squareDistanceTo(hit);
                if (d < best) {
                    best = d;
                    target = e;
                    targetHit = hit;
                }
            }

            Vector3d fxEnd;
            if (target != null) {
                double dist = Math.sqrt(best);
                float dmg = st.damage * st.falloff(dist);
                boolean head = false;
                if (target instanceof LivingEntity) {
                    double headY = target.getPosY() + target.getHeight() * 0.78;
                    if (targetHit.y >= headY && target.getHeight() > 0.9F) {
                        head = true;
                        float mul = st.headMul;
                        dmg *= mul;
                    }
                    dmg = ArmorProtection.absorb((LivingEntity)target,targetHit,dmg);
                }
                float[] acc = hits.get(target);
                if (acc == null) {
                    acc = new float[2];
                    hits.put(target, acc);
                }
                acc[0] += dmg;
                if (head) acc[1] = 1;
                fxEnd = targetHit;
                types[i] = head ? GunNetwork.ShotFxMsg.HIT_HEAD : GunNetwork.ShotFxMsg.HIT_ENTITY;
            } else if (br.getType() == RayTraceResult.Type.BLOCK) {
                fxEnd = br.getHitVec();
                types[i] = GunNetwork.ShotFxMsg.HIT_BLOCK;
            } else {
                fxEnd = end;
                types[i] = GunNetwork.ShotFxMsg.HIT_NONE;
            }
            ends[i * 3] = fxEnd.x;
            ends[i * 3 + 1] = fxEnd.y;
            ends[i * 3 + 2] = fxEnd.z;
        }

        for (Map.Entry<Entity, float[]> en : hits.entrySet()) {
            applyBulletDamage(p, en.getKey(), en.getValue()[0], st);
        }

        GunNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new GunNetwork.ShotFxMsg(p.getEntityId(), st.id, eye.x, eye.y, eye.z, ends, types));
    }

    /** Урон пули: без неуязвимости между попаданиями и без отбрасывания (как в Rust). */
    private static void applyBulletDamage(ServerPlayerEntity shooter, Entity target, float dmg, GunStats st) {
        if (dmg <= 0.0F) return;
        target.hurtResistantTime = 0;
        Vector3d motion = target.getMotion();
        boolean velocityChanged = target.velocityChanged;
        boolean hurt = target.attackEntityFrom(GunDamage.bullet(shooter,target), dmg);
        if (hurt && target instanceof LivingEntity) {
            target.setMotion(motion);
            target.velocityChanged = velocityChanged;
            LivingEntity living = (LivingEntity) target;
            if (living.isAlive() && shooter.getRNG().nextFloat() < st.bleedChance) {
                EffectInstance cur = living.getActivePotionEffect(GunsModule.BLEEDING.get());
                int duration = 300 + (cur != null ? Math.min(cur.getDuration(), 300) : 0);
                living.addPotionEffect(new EffectInstance(GunsModule.BLEEDING.get(), duration, 0, false, true));
            }
        }
    }

    /** Не трассируем в незагруженные чанки (rayTraceBlocks иначе загрузил бы их). */
    private static double loadedRange(ServerWorld world, Vector3d eye, Vector3d dir, double range) {
        double d = range;
        while (d > 8.0 && !world.isBlockLoaded(new BlockPos(eye.add(dir.scale(d))))) {
            d -= 16.0;
        }
        return Math.max(d, 4.0);
    }


    static void handleReload(ServerPlayerEntity p) {
        if (!p.isAlive() || p.isSpectator()) return;
        ItemStack stack = p.getHeldItemMainhand();
        if (!(stack.getItem() instanceof GunItem)) return;
        GunStats st = ((GunItem) stack.getItem()).getStats();
        State s = state(p);
        if (s.reloadSlot >= 0) return;
        if (GunItem.getAmmo(stack) >= st.magSize) return;
        Item ammoItem = GunsItems.ammoItemFor(st);
        if (!p.abilities.isCreativeMode && count(p, ammoItem) <= 0) {
            p.sendStatusMessage(new TranslationTextComponent("message.rustcraft.guns.no_ammo",
                    new TranslationTextComponent(ammoItem.getTranslationKey())), true);
            return;
        }
        long now = p.world.getGameTime();
        s.reloadSlot = p.inventory.currentItem;
        s.reloadStack = stack;
        s.reloadEnd = now + st.reloadTicks;
        s.reloadTotal = st.reloadTicks;
        GunItem.setReload(stack, now, s.reloadEnd);
        p.world.playSound(null, p.getPosX(), p.getPosY(), p.getPosZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON,
                SoundCategory.PLAYERS, 0.8F, 1.3F);
    }

    private static int count(PlayerEntity p, Item item) {
        int c = 0;
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) c += s.getCount();
        }
        return c;
    }

    private static int consume(PlayerEntity p, Item item, int wanted) {
        int taken = 0;
        for (int i = 0; i < p.inventory.getSizeInventory() && taken < wanted; i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) {
                int t = Math.min(wanted - taken, s.getCount());
                s.shrink(t);
                taken += t;
            }
        }
        return taken;
    }

    private static void tickReload(ServerPlayerEntity p, State s, long now) {
        if (s.reloadSlot < 0) {
            ItemStack main = p.getHeldItemMainhand();
            if (main.getItem() instanceof GunItem && GunItem.isReloading(main)) {
                GunItem.clearReload(main); // «зависшая» перезарядка (перезаход, смерть и т.п.)
            }
            return;
        }
        ItemStack stack = p.inventory.getStackInSlot(s.reloadSlot);
        if (p.inventory.currentItem != s.reloadSlot || stack != s.reloadStack
                || !(stack.getItem() instanceof GunItem) || !p.isAlive()) {
            if (!s.reloadStack.isEmpty()) GunItem.clearReload(s.reloadStack);
            s.reloadSlot = -1;
            s.reloadStack = ItemStack.EMPTY;
            return;
        }
        GunStats st = ((GunItem) stack.getItem()).getStats();
        if (now == s.reloadEnd - s.reloadTotal / 2) {
            p.world.playSound(null, p.getPosX(), p.getPosY(), p.getPosZ(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
                    SoundCategory.PLAYERS, 0.5F, 1.7F);
        }
        if (now < s.reloadEnd) return;
        int ammo = GunItem.getAmmo(stack);
        int need = Math.max(0, st.magSize - ammo);
        int got = p.abilities.isCreativeMode ? need : consume(p, GunsItems.ammoItemFor(st), need);
        GunItem.setAmmo(stack, ammo + got);
        GunItem.clearReload(stack);
        s.reloadSlot = -1;
        s.reloadStack = ItemStack.EMPTY;
        p.world.playSound(null, p.getPosX(), p.getPosY(), p.getPosZ(), SoundEvents.BLOCK_PISTON_CONTRACT,
                SoundCategory.PLAYERS, 0.6F, 1.8F);
    }


    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;
        if (!(event.player instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity p = (ServerPlayerEntity) event.player;
        State s = STATES.get(p.getUniqueID());
        if (s == null) {
            ItemStack main = p.getHeldItemMainhand();
            if (!(main.getItem() instanceof GunItem)) return; // состояние только для тех, кто брал оружие
            s = state(p);
        }
        long now = p.world.getGameTime();
        if (s.hasPos) {
            double dx = p.getPosX() - s.lx, dz = p.getPosZ() - s.lz;
            s.speed = Math.sqrt(dx * dx + dz * dz);
        }
        s.lx = p.getPosX(); s.ly = p.getPosY(); s.lz = p.getPosZ();
        s.hasPos = true;
        tickReload(p, s, now);
        if (s.cycleSoundAt >= 0 && now >= s.cycleSoundAt) {
            s.cycleSoundAt = -1;
            if (s.cycleSound != null) {
                p.world.playSound(null, p.getPosX(), p.getPosY(), p.getPosZ(), s.cycleSound, SoundCategory.PLAYERS, 0.5F, 1.6F);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        State s = STATES.remove(event.getPlayer().getUniqueID());
        if (s != null && s.reloadSlot >= 0) {
            if (!s.reloadStack.isEmpty()) GunItem.clearReload(s.reloadStack);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        State s = STATES.remove(event.getOriginal().getUniqueID());
        if (s != null && !s.reloadStack.isEmpty()) GunItem.clearReload(s.reloadStack);
    }
}
