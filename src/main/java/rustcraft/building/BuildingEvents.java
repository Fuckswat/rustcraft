package rustcraft.building;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

/** Слушатели Forge-шины: взрывы, право строительства, HUD молотка. */
public final class BuildingEvents {
    private BuildingEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        RaidDamage.onDetonate(event);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof PlayerEntity)) return;
        if (!(event.getWorld() instanceof World) || ((World) event.getWorld()).isRemote) return;
        PlayerEntity player = (PlayerEntity) event.getEntity();
        if (!BuildPrivilege.hasPrivilege(player, (World) event.getWorld(), event.getPos())) {
            event.setCanceled(true);
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.building_blocked").mergeStyle(TextFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        PlayerEntity player = event.getPlayer();
        if (player == null || !(event.getWorld() instanceof World) || ((World) event.getWorld()).isRemote) return;
        if (!BuildPrivilege.hasPrivilege(player, (World) event.getWorld(), event.getPos())) {
            event.setCanceled(true);
            BuildingLogic.notify(player, new TranslationTextComponent("msg.rustcraft.building.building_blocked").mergeStyle(TextFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        PlayerEntity player = event.player;
        if (player.ticksExisted % 5 != 0) return;
        if (!(player.getHeldItemMainhand().getItem() instanceof HammerItem)) return;
        if (BuildingLogic.recentlyNotified(player)) return;
        RayTraceResult hit = player.pick(5.0D, 1.0F, false);
        if (hit.getType() != RayTraceResult.Type.BLOCK) return;
        IFormattableTextComponent text = BuildingLogic.describe(player.world, ((BlockRayTraceResult) hit).getPos());
        if (text != null) {
            player.sendStatusMessage(text, true);
        }
    }
}
