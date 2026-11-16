package rustcraft.survival;

import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraft.entity.SpawnReason;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class SurvivalEvents {
    private SurvivalEvents() {}
    @SubscribeEvent public static void naturalSpawn(LivingSpawnEvent.CheckSpawn e) {
        SpawnReason reason=e.getSpawnReason();
        if (reason != SpawnReason.NATURAL && reason != SpawnReason.CHUNK_GENERATION && reason != SpawnReason.REINFORCEMENT) return;
        if(e.getWorld() instanceof net.minecraft.world.server.ServerWorld) {
            net.minecraft.world.server.ServerWorld w=(net.minecraft.world.server.ServerWorld)e.getWorld();
            rustcraft.world.map.MapData d=rustcraft.world.map.MapData.get(w);
            net.minecraft.util.math.BlockPos p=e.getEntityLiving().getPosition();
            int x=p.getX()-d.center.getX(),z=p.getZ()-d.center.getZ();
            if(d.started&&d.version>=2&&rustcraft.world.map.MapLayout.isLand(x,z)
                    &&d.monumentAt(p)==null&&p.getY()<rustcraft.world.map.MapLayout.surfaceHeight(x,z)-1) {
                e.setResult(Event.Result.DENY);return;
            }
        }
        if (e.getEntityLiving().getRNG().nextDouble() >= SurvivalModule.NATURAL_SPAWN_CHANCE.get()) e.setResult(Event.Result.DENY);
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayerEntity)) return;
        ServerPlayerEntity p = (ServerPlayerEntity)e.getPlayer();
        CompoundNBT persisted = p.getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        if (!persisted.getBoolean("RustCraftStarter")) {
            ResourceNodeTile.give(p, SurvivalModule.ROCK.get(), 1);
            ResourceNodeTile.give(p, Items.TORCH, 1);
            persisted.putBoolean("RustCraftStarter", true);
            p.getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        }
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone e) {
        e.getPlayer().getPersistentData().put(PlayerEntity.PERSISTED_NBT_TAG,
                e.getOriginal().getPersistentData().getCompound(PlayerEntity.PERSISTED_NBT_TAG).copy());
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayerEntity) || e.getPlayer().isCreative()) return;
        ServerPlayerEntity p = (ServerPlayerEntity)e.getPlayer();
        for (int i = 0; i < p.inventory.getSizeInventory(); i++)
            if (p.inventory.getStackInSlot(i).getItem() instanceof HarvestToolItem) return;
        ResourceNodeTile.give(p, SurvivalModule.ROCK.get(), 1);
        ResourceNodeTile.give(p, Items.TORCH, 1);
    }
    @SubscribeEvent public static void click(PlayerInteractEvent.LeftClickBlock e) {
        if (e.getPlayer().isCreative()) return;
        if (ResourceNodes.root(e.getWorld(), e.getPos()) != null) e.setCanceled(true);
    }
    @SubscribeEvent public static void animalDrops(LivingDropsEvent e) {
        if (!(e.getEntityLiving() instanceof AnimalEntity) || e.getEntityLiving().isChild() || e.getEntityLiving().world.isRemote) return;
        if (!(e.getSource().getTrueSource() instanceof ServerPlayerEntity)) return;
        // Meat remains vanilla food. Fat and cloth feed the Rust fuel/medicine progression.
        int rate = Math.max(1, (int)Math.round(SurvivalModule.RATE.get()));
        e.getEntityLiving().entityDropItem(new ItemStack(SurvivalModule.ANIMAL_FAT.get(), 4*rate));
        e.getEntityLiving().entityDropItem(new ItemStack(SurvivalModule.CLOTH.get(), 2*rate));
    }
}
