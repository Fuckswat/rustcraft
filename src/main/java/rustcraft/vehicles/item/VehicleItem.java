package rustcraft.vehicles.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import rustcraft.vehicles.entity.AbstractVehicleEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * Предмет-размещатель транспорта: ПКМ по земле (вертолёты) или по воде (лодка).
 * Транспорт ставится носом туда, куда смотрит игрок. Топливо/прочность хранятся в NBT "VehicleData".
 */
public class VehicleItem extends Item {
    private final Supplier<? extends EntityType<? extends AbstractVehicleEntity>> type;
    private final boolean boat;

    public VehicleItem(Properties props, Supplier<? extends EntityType<? extends AbstractVehicleEntity>> type, boolean boat) {
        super(props);
        this.type = type;
        this.boat = boat;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getHeldItem(hand);
        BlockRayTraceResult hit = rayTrace(world, player, boat ? RayTraceContext.FluidMode.ANY : RayTraceContext.FluidMode.NONE);
        if (hit.getType() != RayTraceResult.Type.BLOCK) {
            return ActionResult.resultPass(stack);
        }
        if (player.isSpectator() || !world.isBlockModifiable(player, hit.getPos())
                || !player.canPlayerEdit(hit.getPos().offset(hit.getFace()), hit.getFace(), stack)) {
            return ActionResult.resultFail(stack);
        }
        Vector3d at = hit.getHitVec();
        if (player.getPositionVec().squareDistanceTo(at) > 49.0D) {
            return ActionResult.resultPass(stack);
        }
        AbstractVehicleEntity vehicle = type.get().create(world);
        if (vehicle == null) return ActionResult.resultFail(stack);
        double y = at.y;
        if (boat) {
            boolean water = world.getFluidState(hit.getPos()).isTagged(net.minecraft.tags.FluidTags.WATER);
            if (!water) {
                player.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_needs_water"), true);
                return ActionResult.resultFail(stack);
            }
            y = at.y - 0.3D;
        }
        boolean free = false;
        for (double dy = 0.0D; dy <= (boat ? 0.0D : 2.0D) && !free; dy += 0.5D) {
            vehicle.setLocationAndAngles(at.x, y + dy, at.z, player.rotationYaw, 0.0F);
            free = world.hasNoCollisions(vehicle, vehicle.getBoundingBox().shrink(0.1D));
        }
        vehicle.prevRotationYaw = player.rotationYaw;
        if (!free) {
            player.sendStatusMessage(new TranslationTextComponent("message.rustcraft.vehicle_no_space"), true);
            return ActionResult.resultFail(stack);
        }
        if (!world.isRemote) {
            vehicle.loadFromItem(stack);
            vehicle.setOwner(player.getUniqueID());
            if (!world.addEntity(vehicle)) return ActionResult.resultFail(stack);
            world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.NEUTRAL, 0.6F, 0.8F);
            if (!player.abilities.isCreativeMode) stack.shrink(1);
        }
        player.addStat(Stats.ITEM_USED.get(this));
        return ActionResult.func_233538_a_(stack, world.isRemote);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        CompoundNBT tag = stack.getChildTag("VehicleData");
        if (tag != null) {
            if (tag.contains("Fuel")) {
                tooltip.add(new TranslationTextComponent("tooltip.rustcraft.vehicle_fuel",
                        Math.round(tag.getFloat("Fuel") / 100.0F)).mergeStyle(TextFormatting.GRAY));
            }
            if (tag.contains("Health")) {
                tooltip.add(new TranslationTextComponent("tooltip.rustcraft.vehicle_health",
                        Math.round(tag.getFloat("Health"))).mergeStyle(TextFormatting.GRAY));
            }
        }
        tooltip.add(new TranslationTextComponent(boat ? "tooltip.rustcraft.vehicle_place_water" : "tooltip.rustcraft.vehicle_place_ground")
                .mergeStyle(TextFormatting.DARK_GRAY));
    }
}
