package rustcraft.bots;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ILivingEntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.HurtByTargetGoal;
import net.minecraft.entity.ai.goal.LookAtGoal;
import net.minecraft.entity.ai.goal.LookRandomlyGoal;
import net.minecraft.entity.ai.goal.NearestAttackableTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.DamageSource;
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
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.IServerWorld;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.World;
import rustcraft.bots.ai.GuardPatrolGoal;
import rustcraft.bots.ai.ScientistCombatGoal;

/**
 * Общая логика учёных: оружие и магазин, «дом» (точка спавна), оповещение союзников,
 * радиопереговоры, постоянство (не деспавнятся).
 */
public abstract class AbstractScientistEntity extends MonsterEntity {
    private static final String NBT_HOME = "BotHome";
    private static final String NBT_WEAPON = "BotWeapon";
    private static final String NBT_AMMO = "BotAmmo";

    public static final double ALERT_RADIUS = 24.0D;
    /** Насколько далеко от дома можно уходить в погоне. */
    public static final double LEASH_RADIUS = 36.0D;

    @Nullable
    private BlockPos home;
    @Nullable
    private BotWeapon weapon;
    private int ammo = -1;
    private int reloadTicks;
    private boolean suppressAlert;
    private int calloutCooldown;
    /** Без инициализатора: присваивается в registerGoals() из конструктора MobEntity. */
    private ScientistCombatGoal combatGoal;

    protected AbstractScientistEntity(EntityType<? extends MonsterEntity> type, World world) {
        super(type, world);
        this.experienceValue = 10;
        setCanPickUpLoot(false);
        setPathPriority(PathNodeType.WATER, -1.0F);
        setPathPriority(PathNodeType.WATER_BORDER, 8.0F);
        setPathPriority(PathNodeType.DANGER_FIRE, -1.0F);
        setPathPriority(PathNodeType.DAMAGE_FIRE, -1.0F);
    }

    // абстрактное

    /** Выбор оружия при первом появлении. */
    protected abstract BotWeapon pickWeapon();

    /** Ключ для сообщения о смерти (scientist / heavy_scientist). */
    public abstract String getDeathMessageKind();

    /** Тяжёлый учёный ведёт подавляющий огонь по укрытию. */
    public boolean canSuppress() {
        return false;
    }

    /** Шанс выпадения оружия из рук. */
    protected abstract float weaponDropChance();

    /** Базовая высота голоса для радио. */
    protected abstract float voicePitch();


    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.combatGoal = new ScientistCombatGoal(this);
        this.goalSelector.addGoal(2, this.combatGoal);
        this.goalSelector.addGoal(5, new GuardPatrolGoal(this, 0.65D));
        this.goalSelector.addGoal(8, new LookAtGoal(this, PlayerEntity.class, 16.0F));
        this.goalSelector.addGoal(9, new LookRandomlyGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, AbstractScientistEntity.class));
        this.targetSelector.addGoal(2, new FindPlayerGoal(this));
    }

    /** Поиск игроков: с проверкой видимости, по высоте до 16 блоков (многоярусная вышка). */
    static class FindPlayerGoal extends NearestAttackableTargetGoal<PlayerEntity> {
        FindPlayerGoal(AbstractScientistEntity bot) {
            super(bot, PlayerEntity.class, 5, true, false, AbstractScientistEntity::isValidTarget);
            setUnseenMemoryTicks(300);
        }

        @Override
        protected AxisAlignedBB getTargetableArea(double distance) {
            return this.goalOwner.getBoundingBox().grow(distance, 16.0D, distance);
        }
    }

    public static boolean isValidTarget(@Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e instanceof AbstractScientistEntity) {
            return false;
        }
        if (e instanceof PlayerEntity) {
            PlayerEntity p = (PlayerEntity) e;
            return !p.isCreative() && !p.isSpectator();
        }
        return true;
    }

    @Override
    public void setAttackTarget(@Nullable LivingEntity target) {
        LivingEntity old = getAttackTarget();
        super.setAttackTarget(target);
        if (!this.world.isRemote && target != null && target != old && !suppressAlert && isValidTarget(target)) {
            alertAllies(target);
        }
    }

    /** Сообщить соседям (24 блока) о противнике. */
    public void alertAllies(LivingEntity target) {
        if (calloutCooldown <= 0) {
            radioCallout();
        }
        AxisAlignedBB box = getBoundingBox().grow(ALERT_RADIUS, 12.0D, ALERT_RADIUS);
        List<AbstractScientistEntity> allies = this.world.getEntitiesWithinAABB(AbstractScientistEntity.class, box,
                e -> e != this && e.isAlive() && !e.isAIDisabled());
        for (AbstractScientistEntity ally : allies) {
            if (ally.getAttackTarget() == null || !ally.getAttackTarget().isAlive()) {
                ally.receiveAlert(target);
            }
        }
    }

    void receiveAlert(LivingEntity target) {
        suppressAlert = true;
        try {
            setAttackTarget(target);
        } finally {
            suppressAlert = false;
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity attacker = source.getTrueSource();
        if (attacker instanceof AbstractScientistEntity) {
            return false; // без огня по своим
        }
        boolean result = super.attackEntityFrom(source, amount);
        if (result && !this.world.isRemote && attacker instanceof LivingEntity
                && isValidTarget((LivingEntity) attacker)) {
            LivingEntity la = (LivingEntity) attacker;
            if (getAttackTarget() == null) {
                setAttackTarget(la); // оповестит союзников
            } else {
                alertAllies(la);
            }
            if (combatGoal != null) {
                combatGoal.onHurt();
            }
        }
        return result;
    }

    // тик

    @Override
    public void livingTick() {
        super.livingTick();
        if (this.world.isRemote) {
            return;
        }
        if (home == null) {
            setHome(getPosition());
        }
        ensureWeapon();
        if (calloutCooldown > 0) {
            calloutCooldown--;
        }
        if (reloadTicks > 0) {
            reloadTicks--;
            if (reloadTicks == getWeapon().reloadTicks / 2) {
                playSound(SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE, 0.8F, 0.8F + this.rand.nextFloat() * 0.1F);
            }
            if (reloadTicks == 0) {
                ammo = getWeapon().magSize;
                playSound(SoundEvents.ITEM_CROSSBOW_LOADING_END, 0.9F, 0.75F);
                playSound(SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.6F, 1.3F);
            }
        } else if (getAttackTarget() == null && ammo < getWeapon().magSize && this.ticksExisted % 40 == 0) {
            startReload(); // тактическая перезарядка вне боя
        }
    }

    private void ensureWeapon() {
        if (weapon == null) {
            weapon = pickWeapon();
            ammo = weapon.magSize;
        }
        if (ammo < 0) {
            ammo = weapon.magSize;
        }
        if (getItemStackFromSlot(EquipmentSlotType.MAINHAND).isEmpty()) {
            setItemStackToSlot(EquipmentSlotType.MAINHAND, weapon.createStack());
            setDropChance(EquipmentSlotType.MAINHAND, weaponDropChance());
        }
    }

    // оружие

    public BotWeapon getWeapon() {
        if (weapon == null) {
            weapon = pickWeapon();
        }
        return weapon;
    }

    public int getAmmo() {
        return Math.max(ammo, 0);
    }

    public void consumeAmmo() {
        if (ammo > 0) {
            ammo--;
        }
    }

    public boolean isReloading() {
        return reloadTicks > 0;
    }

    public void startReload() {
        if (reloadTicks > 0 || ammo >= getWeapon().magSize) {
            return;
        }
        reloadTicks = getWeapon().reloadTicks;
        playSound(SoundEvents.ITEM_CROSSBOW_LOADING_START, 0.8F, 0.7F + this.rand.nextFloat() * 0.1F);
        if (getAttackTarget() != null && this.rand.nextInt(3) == 0) {
            radioCallout(); // «перезаряжаюсь!»
        }
    }

    /** Множитель урона пуль от сложности. */
    public float difficultyDamageMult() {
        Difficulty d = this.world.getDifficulty();
        if (d == Difficulty.EASY) {
            return 0.7F;
        }
        if (d == Difficulty.HARD) {
            return 1.2F;
        }
        return 1.0F;
    }

    /** Задержка реакции после обнаружения цели (0,5–1 с). */
    public int rollReactionDelay() {
        int t = 10 + this.rand.nextInt(11);
        Difficulty d = this.world.getDifficulty();
        if (d == Difficulty.EASY) {
            t += 10;
        } else if (d == Difficulty.HARD) {
            t -= 3;
        }
        return t;
    }

    /**
     * Видимая точка цели (голова/грудь/ноги) с линии глаз. null — цель полностью за укрытием.
     */
    @Nullable
    public Vector3d findVisibleAimPoint(LivingEntity target) {
        Vector3d eye = getEyePosition(1.0F);
        double h = target.getHeight();
        double[] fractions = {0.72D, 0.88D, 0.45D, 0.2D};
        for (double f : fractions) {
            Vector3d p = new Vector3d(target.getPosX(), target.getPosY() + h * f, target.getPosZ());
            if (hasClearShot(eye, p)) {
                return p;
            }
        }
        return null;
    }

    public boolean hasClearShot(Vector3d from, Vector3d to) {
        BlockRayTraceResult r = this.world.rayTraceBlocks(new RayTraceContext(from, to,
                RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, this));
        return r.getType() == RayTraceResult.Type.MISS;
    }

    // дом / безопасная земля

    public BlockPos getHome() {
        return home != null ? home : getPosition();
    }

    public void setHome(BlockPos pos) {
        this.home = pos.toImmutable();
        setHomePosAndDistance(this.home, (int) LEASH_RADIUS);
    }

    public double distanceSqFromHome(Vector3d pos) {
        BlockPos h = getHome();
        double dx = pos.x - (h.getX() + 0.5D);
        double dz = pos.z - (h.getZ() + 0.5D);
        double dy = (pos.y - h.getY()) * 0.5D;
        return dx * dx + dz * dz + dy * dy;
    }

    /** Можно ли стоять на этой позиции: твёрдая опора, нет жидкости, есть место. */
    public boolean isSafeStandPos(BlockPos feet) {
        World w = this.world;
        if (!w.isBlockLoaded(feet)) {
            return false;
        }
        BlockState below = w.getBlockState(feet.down());
        BlockState atFeet = w.getBlockState(feet);
        BlockState atHead = w.getBlockState(feet.up());
        if (!below.getFluidState().isEmpty() || !atFeet.getFluidState().isEmpty()) {
            return false;
        }
        if (below.getCollisionShape(w, feet.down()).isEmpty()) {
            return false;
        }
        return atFeet.getCollisionShape(w, feet).isEmpty() && atHead.getCollisionShape(w, feet.up()).isEmpty();
    }

    /**
     * Безопасен ли шаг в сторону движения strafe(forward, strafe): под ногами там должна быть опора
     * не глубже 2 блоков и не вода — чтобы не свалиться с вышки.
     */
    public boolean isSafeMove(float forward, float strafe) {
        if (forward == 0.0F && strafe == 0.0F) {
            return true;
        }
        float yawRad = this.rotationYaw * ((float) Math.PI / 180F);
        float sin = MathHelper.sin(yawRad);
        float cos = MathHelper.cos(yawRad);
        double dx = strafe * cos - forward * sin;
        double dz = forward * cos + strafe * sin;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4D) {
            return true;
        }
        dx = dx / len * 1.1D;
        dz = dz / len * 1.1D;
        BlockPos feet = new BlockPos(getPosX() + dx, getPosY() + 0.1D, getPosZ() + dz);
        World w = this.world;
        if (!w.getBlockState(feet).getCollisionShape(w, feet).isEmpty()) {
            return true; // упрёмся в стену — не опасно
        }
        for (int i = 1; i <= 2; i++) {
            BlockPos p = feet.down(i);
            BlockState s = w.getBlockState(p);
            if (!s.getFluidState().isEmpty()) {
                return false;
            }
            if (!s.getCollisionShape(w, p).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // спавн / постоянство

    @Nullable
    @Override
    public ILivingEntityData onInitialSpawn(IServerWorld world, DifficultyInstance difficulty, SpawnReason reason,
                                            @Nullable ILivingEntityData data, @Nullable CompoundNBT tag) {
        ILivingEntityData result = super.onInitialSpawn(world, difficulty, reason, data, tag);
        enablePersistence();
        setLeftHanded(false); // поза стрельбы рассчитана на правую руку
        setHome(getPosition());
        ensureWeapon();
        return result;
    }

    @Override
    public boolean canDespawn(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public float getBlockPathWeight(BlockPos pos, IWorldReader world) {
        return 0.0F; // не тянутся в темноту, как обычные монстры
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    public void writeAdditional(CompoundNBT tag) {
        super.writeAdditional(tag);
        if (home != null) {
            tag.putLong(NBT_HOME, home.toLong());
        }
        if (weapon != null) {
            tag.putInt(NBT_WEAPON, weapon.ordinal());
        }
        tag.putInt(NBT_AMMO, ammo);
    }

    @Override
    public void readAdditional(CompoundNBT tag) {
        super.readAdditional(tag);
        if (tag.contains(NBT_HOME)) {
            setHome(BlockPos.fromLong(tag.getLong(NBT_HOME)));
        }
        if (tag.contains(NBT_WEAPON)) {
            weapon = BotWeapon.byOrdinal(tag.getInt(NBT_WEAPON));
        }
        if (tag.contains(NBT_AMMO)) {
            ammo = tag.getInt(NBT_AMMO);
        }
        enablePersistence();
    }

    // звуки

    /** Короткий «радиообмен»: писк рации + искажённый голос. */
    public void radioCallout() {
        if (this.world.isRemote || isSilent()) {
            return;
        }
        calloutCooldown = 60;
        this.world.playSound(null, getPosX(), getPosYEye(), getPosZ(), SoundEvents.BLOCK_NOTE_BLOCK_BIT,
                SoundCategory.HOSTILE, 0.35F, 1.9F);
        this.world.playSound(null, getPosX(), getPosYEye(), getPosZ(), SoundEvents.ENTITY_PILLAGER_AMBIENT,
                SoundCategory.HOSTILE, 1.1F, voicePitch() + 0.1F + this.rand.nextFloat() * 0.1F);
    }

    @Override
    public void playAmbientSound() {
        if (this.rand.nextInt(3) == 0) {
            this.world.playSound(null, getPosX(), getPosYEye(), getPosZ(), SoundEvents.BLOCK_NOTE_BLOCK_BIT,
                    SoundCategory.HOSTILE, 0.25F, 1.6F + this.rand.nextFloat() * 0.4F);
        }
        super.playAmbientSound();
    }

    @Override
    public int getTalkInterval() {
        return 260;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.rand.nextInt(4) == 0 ? SoundEvents.ENTITY_VILLAGER_AMBIENT : SoundEvents.ENTITY_PILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_PILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_PILLAGER_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        return voicePitch() + (this.rand.nextFloat() - 0.5F) * 0.12F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    /** Получить сохранённый предмет в руке (для рендера). */
    public ItemStack getGunStack() {
        return getHeldItemMainhand();
    }
}
