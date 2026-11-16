package rustcraft.building;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

/** Бросаемая рейдовая взрывчатка (C4, сумка, бобовка). ПКМ — бросить. */
public class ExplosiveItem extends Item {
    private final ExplosiveKind kind;

    public ExplosiveItem(ExplosiveKind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getHeldItem(hand);
        world.playSound(null, player.getPosX(), player.getPosY(), player.getPosZ(), SoundEvents.ENTITY_SNOWBALL_THROW,
                SoundCategory.PLAYERS, 0.6F, 0.5F + world.rand.nextFloat() * 0.2F);
        if (!world.isRemote) {
            ThrownExplosiveEntity e = new ThrownExplosiveEntity(kind.entityType(), player, world);
            ItemStack copy = stack.copy();
            copy.setCount(1);
            e.setItem(copy);
            e.func_234612_a_(player, player.rotationPitch, player.rotationYaw, 0.0F, kind.throwSpeed, 1.0F);
            world.addEntity(e);
        }
        player.getCooldownTracker().setCooldown(this, 15);
        if (!player.abilities.isCreativeMode) {
            stack.shrink(1);
        }
        return ActionResult.func_233538_a_(stack, world.isRemote);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.rustcraft." + (kind == ExplosiveKind.C4 ? "timed_explosive"
                : kind == ExplosiveKind.SATCHEL ? "satchel_charge" : "beancan_grenade")).mergeStyle(TextFormatting.GRAY));
    }
}
