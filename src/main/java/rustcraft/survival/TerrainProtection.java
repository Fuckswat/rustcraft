package rustcraft.survival;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import rustcraft.world.map.MapData;
import rustcraft.world.map.MapLayout;

/** Только созданный остров: камень под ногами не заменяет ресурсные узлы. Другие миры не затронуты. */
public final class TerrainProtection {
    private TerrainProtection() {}
    private static boolean inMap(ServerWorld w, BlockPos p) {
        MapData d = MapData.get(w);
        if (!d.built || d.version < 2) return false;
        int x=p.getX()-d.center.getX(), z=p.getZ()-d.center.getZ();
        return x>=MapLayout.MIN_X&&x<=MapLayout.MAX_X&&z>=MapLayout.MIN_Z&&z<=MapLayout.MAX_Z;
    }
    private static boolean natural(Block b) {
        return b == Blocks.STONE || b == Blocks.DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.SAND || b == Blocks.GRAVEL ||
                b == Blocks.SNOW_BLOCK || b == Blocks.COAL_ORE || b == Blocks.IRON_ORE || b == Blocks.GOLD_ORE ||
                b == Blocks.DIAMOND_ORE || b == Blocks.REDSTONE_ORE || b == Blocks.LAPIS_ORE || b == Blocks.EMERALD_ORE ||
                b == Blocks.GRANITE || b == Blocks.ANDESITE || b == Blocks.DIORITE || b == Blocks.BEDROCK;
    }
    private static boolean protectedAt(ServerWorld w, BlockPos p) {
        return inMap(w,p) && natural(w.getBlockState(p).getBlock()) && !Edits.get(w).placed.contains(p.toLong());
    }
    @SubscribeEvent public static void click(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getWorld() instanceof ServerWorld) || e.getPlayer().isCreative()) return;
        if (protectedAt((ServerWorld)e.getWorld(), e.getPos())) {
            e.setCanceled(true);
            if (e.getPlayer().ticksExisted % 20 == 0 || e.getPlayer().getPersistentData().getLong("RustGroundHint") < e.getWorld().getGameTime()) {
                e.getPlayer().sendStatusMessage(new TranslationTextComponent("msg.rustcraft.harvest.terrain"), true);
                e.getPlayer().getPersistentData().putLong("RustGroundHint", e.getWorld().getGameTime()+40);
            }
        }
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) {
        if (!(e.getWorld() instanceof ServerWorld)) return;
        ServerWorld w=(ServerWorld)e.getWorld();
        if (!e.getPlayer().isCreative() && protectedAt(w,e.getPos())) { e.setCanceled(true); return; }
        Edits d=Edits.get(w); if (d.placed.remove(e.getPos().toLong())) d.markDirty();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void place(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getWorld() instanceof ServerWorld) || !(e.getEntity() instanceof PlayerEntity)) return;
        ServerWorld w=(ServerWorld)e.getWorld();
        if (!inMap(w,e.getPos())) return;
        Edits d=Edits.get(w);
        if (e instanceof BlockEvent.EntityMultiPlaceEvent) {
            ((BlockEvent.EntityMultiPlaceEvent)e).getReplacedBlockSnapshots().forEach(s -> d.placed.add(s.getPos().toLong()));
        } else d.placed.add(e.getPos().toLong());
        d.markDirty();
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate e) {
        if (e.getWorld() instanceof ServerWorld) e.getAffectedBlocks().removeIf(p -> protectedAt((ServerWorld)e.getWorld(),p));
    }
    public static final class Edits extends WorldSavedData {
        final Set<Long> placed = new HashSet<>();
        public Edits() { super("rustcraft_terrain_edits"); }
        static Edits get(ServerWorld w) { return w.getSavedData().getOrCreate(Edits::new,"rustcraft_terrain_edits"); }
        @Override public void read(CompoundNBT n) { placed.clear(); for (long p : n.getLongArray("Placed")) placed.add(p); }
        @Override public CompoundNBT write(CompoundNBT n) { n.putLongArray("Placed", new java.util.ArrayList<>(placed)); return n; }
    }
}
