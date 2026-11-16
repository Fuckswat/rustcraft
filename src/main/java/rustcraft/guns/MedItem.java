package rustcraft.guns;

import java.util.List;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.stats.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import rustcraft.RustCraft;

/** Бинт / шприц / аптечка: предмет с длительностью применения и анимацией. */
public class MedItem extends Item {
    public enum Kind { BANDAGE, SYRINGE, MEDKIT }

    private final Kind kind;

    public MedItem(Kind kind, int stack) {
        super(new Item.Properties().group(RustCraft.TAB).maxStackSize(stack));
        this.kind = kind;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        switch (kind) {
            case BANDAGE: return 40;  // 2 c
            case SYRINGE: return 24;  // 1.2 c
            default: return 70;       // 3.5 c
        }
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return kind == Kind.SYRINGE ? UseAction.DRINK : UseAction.BOW;
    }

    @Override
    public SoundEvent getDrinkSound() {
        return SoundEvents.BLOCK_HONEY_BLOCK_STEP;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getHeldItem(hand);
        boolean bleeding = player.isPotionActive(GunsModule.BLEEDING.get());
        if (kind == Kind.BANDAGE && !bleeding && player.getHealth() >= player.getMaxHealth()) {
            return ActionResult.resultFail(stack);
        }
        if (kind != Kind.BANDAGE && player.getHealth() >= player.getMaxHealth() && !bleeding) {
            return ActionResult.resultFail(stack);
        }
        player.setActiveHand(hand);
        return ActionResult.resultConsume(stack);
    }

    @Override
    public void onUse(World world, LivingEntity living, ItemStack stack, int count) {
        if (world.isRemote) return;
        int elapsed = getUseDuration(stack) - count;
        if (kind != Kind.SYRINGE && elapsed % 10 == 0) {
            world.playSound(null, living.getPosX(), living.getPosY(), living.getPosZ(),
                    kind == Kind.BANDAGE ? SoundEvents.BLOCK_WOOL_PLACE : SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                    SoundCategory.PLAYERS, 0.6F, 0.9F + world.rand.nextFloat() * 0.3F);
        }
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, LivingEntity living) {
        if (!world.isRemote) {
            switch (kind) {
                case BANDAGE:
                    living.removePotionEffect(GunsModule.BLEEDING.get());
                    living.heal(1.5F); // ~7 HP
                    break;
                case SYRINGE:
                    living.heal(3.0F); // 15 HP сразу
                    living.addPotionEffect(new EffectInstance(Effects.REGENERATION, 100, 1, false, true)); // ~ +20 HP за 5 c
                    break;
                default:
                    living.removePotionEffect(GunsModule.BLEEDING.get());
                    living.heal(2.0F);
                    living.addPotionEffect(new EffectInstance(Effects.REGENERATION, 300, 1, false, true)); // ~ +60 HP за 15 c
                    break;
            }
            world.playSound(null, living.getPosX(), living.getPosY(), living.getPosZ(),
                    kind == Kind.SYRINGE ? SoundEvents.ITEM_BOTTLE_EMPTY : SoundEvents.ITEM_ARMOR_EQUIP_GENERIC,
                    SoundCategory.PLAYERS, 0.8F, kind == Kind.SYRINGE ? 1.6F : 1.2F);
            if (living instanceof ServerPlayerEntity) {
                ((ServerPlayerEntity) living).addStat(Stats.ITEM_USED.get(this));
            }
        }
        if (!(living instanceof PlayerEntity) || !((PlayerEntity) living).abilities.isCreativeMode) {
            stack.shrink(1);
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<ITextComponent> tooltip, ITooltipFlag flag) {
        tooltip.add(new TranslationTextComponent("tooltip.rustcraft." + (kind == Kind.BANDAGE ? "bandage" : kind == Kind.SYRINGE ? "syringe" : "medkit"))
                .mergeStyle(TextFormatting.GRAY));
    }
}
