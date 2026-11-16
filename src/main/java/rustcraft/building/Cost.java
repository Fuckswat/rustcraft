package rustcraft.building;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Стоимость улучшения/починки в ресурсах из инвентаря игрока. Межмодульные предметы — по строковому ID. */
public final class Cost {
    private Cost() {}

    public static final class Option {
        final Predicate<ItemStack> match;
        final int count;
        final String nameKey;

        Option(Predicate<ItemStack> match, int count, String nameKey) {
            this.match = match;
            this.count = count;
            this.nameKey = nameKey;
        }

        public IFormattableTextComponent describe(int amount) {
            return new StringTextComponent(amount + "x ").append(new TranslationTextComponent(nameKey));
        }
    }

    /** Предмет rustcraft:<id> из другого модуля, или null если модуль отсутствует. */
    public static Item modItem(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("rustcraft", id));
        return item == null || item == Items.AIR ? null : item;
    }

    private static Option item(Item item, int count) {
        final Item it = item;
        return new Option(s -> s.getItem() == it, count, item.getTranslationKey());
    }

    private static void addMod(List<Option> list, String id, int count) {
        Item item = modItem(id);
        if (item != null) list.add(item(item, count));
    }

    /** Цена улучшения стены ДО указанного материала. */
    public static List<Option> upgradeTo(StructureKind.Mat mat) {
        List<Option> list = new ArrayList<Option>();
        switch (mat) {
            case STONE:
                addMod(list, "stones", 40);
                if (list.isEmpty()) list.add(item(Items.COBBLESTONE, 40));
                break;
            case METAL:
                addMod(list, "metal_fragments", 20);
                if (list.isEmpty()) list.add(item(Items.IRON_INGOT, 3));
                break;
            case ARMORED:
                addMod(list, "hqm", 3);
                if (list.isEmpty()) list.add(item(Items.DIAMOND, 1));
                break;
            default:
                addMod(list,"wood",20);
                if(list.isEmpty()) list.add(new Option(s -> s.getItem().isIn(ItemTags.PLANKS), 20, "msg.rustcraft.building.planks"));
        }
        return list;
    }

    /** Цена полной починки (масштабируется по недостающему HP). */
    public static List<Option> repairFull(StructureKind.Mat mat) {
        List<Option> list = new ArrayList<Option>();
        switch (mat) {
            case WOOD:
                addMod(list,"wood",15);
                if(list.isEmpty()) list.add(new Option(s -> s.getItem().isIn(ItemTags.PLANKS),15,"msg.rustcraft.building.planks"));
                break;
            case STONE:
                addMod(list,"stones",25);
                if(list.isEmpty()) list.add(item(Items.COBBLESTONE,25));
                break;
            case METAL:
                addMod(list, "metal_fragments", 15);
                if(list.isEmpty()) list.add(item(Items.IRON_INGOT,2));
                break;
            default:
                addMod(list, "hqm", 2);
                if (list.isEmpty()) list.add(item(Items.DIAMOND, 1));
        }
        return list;
    }

    public static int countIn(PlayerEntity player, Option opt) {
        int n = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (!s.isEmpty() && opt.match.test(s)) n += s.getCount();
        }
        return n;
    }

    public static void consume(PlayerEntity player, Option opt, int amount) {
        for (int i = 0; i < player.inventory.getSizeInventory() && amount > 0; i++) {
            ItemStack s = player.inventory.getStackInSlot(i);
            if (!s.isEmpty() && opt.match.test(s)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
        player.inventory.markDirty();
    }

    /**
     * Пытается оплатить: выбирает первый вариант, на который хватает ресурсов (multiplier 0..1 масштабирует цену).
     * Возвращает null при успехе, иначе описание недостающего.
     */
    public static IFormattableTextComponent tryPay(PlayerEntity player, List<Option> options, float multiplier) {
        if (player.isCreative()) return null;
        for (Option opt : options) {
            int need = Math.max(1, (int) Math.ceil(opt.count * multiplier));
            if (countIn(player, opt) >= need) {
                consume(player, opt, need);
                return null;
            }
        }
        IFormattableTextComponent text = new StringTextComponent("");
        for (int i = 0; i < options.size(); i++) {
            Option opt = options.get(i);
            if (i > 0) text.append(new TranslationTextComponent("msg.rustcraft.building.or"));
            text.append(opt.describe(Math.max(1, (int) Math.ceil(opt.count * multiplier))));
        }
        return text;
    }
}
