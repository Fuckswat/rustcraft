package rustcraft.bots;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import rustcraft.RustCraft;

/**
 * Характеристики оружия ботов. Само оружие (предмет) берётся из модуля guns по строковому ID
 * во время выполнения; если его нет — в руки выдаётся ванильный арбалет, а стрельба всё равно
 * идёт по этим параметрам (хитскан).
 */
public enum BotWeapon {
    //        itemId          dmg  pel int bMin bMax mag rld spread range pMin pMax prefMin prefMax sound
    MP5      ("mp5",          3.0F, 1,  2,  4,   7,  30, 50, 2.0F,  32,  14,  26,  7,  17, 0),
    AK47     ("ak47",         3.6F, 1,  3,  3,   5,  30, 60, 1.5F,  40,  16,  30,  9,  20, 1),
    HEAVY_AK ("ak47",         4.6F, 1,  3,  6,  10,  60, 85, 2.1F,  40,  10,  20,  9,  20, 1),
    SHOTGUN  ("pump_shotgun", 1.6F, 8, 18,  1,   1,   6, 70, 5.5F,  22,   0,   0,  4,  11, 2);

    public final String itemId;
    /** Урон одной пули/дробины (до множителя сложности). */
    public final float damage;
    /** Количество дробин за выстрел (1 у автоматов). */
    public final int pellets;
    /** Тиков между выстрелами внутри очереди. */
    public final int shotInterval;
    public final int burstMin;
    public final int burstMax;
    public final int magSize;
    public final int reloadTicks;
    /** Базовый разброс в градусах. */
    public final float spread;
    /** Максимальная дальность стрельбы, блоков. */
    public final int range;
    /** Пауза между очередями (тики). */
    public final int pauseMin;
    public final int pauseMax;
    /** Предпочитаемая дистанция боя. */
    public final int prefMin;
    public final int prefMax;
    /** 0 = ПП, 1 = винтовка, 2 = дробовик (для звука). */
    public final int soundKind;

    BotWeapon(String itemId, float damage, int pellets, int shotInterval, int burstMin, int burstMax,
              int magSize, int reloadTicks, float spread, int range, int pauseMin, int pauseMax,
              int prefMin, int prefMax, int soundKind) {
        this.itemId = itemId;
        this.damage = damage;
        this.pellets = pellets;
        this.shotInterval = shotInterval;
        this.burstMin = burstMin;
        this.burstMax = burstMax;
        this.magSize = magSize;
        this.reloadTicks = reloadTicks;
        this.spread = spread;
        this.range = range;
        this.pauseMin = pauseMin;
        this.pauseMax = pauseMax;
        this.prefMin = prefMin;
        this.prefMax = prefMax;
        this.soundKind = soundKind;
    }

    /** Предмет оружия из модуля guns или арбалет, если модуль не загружен. */
    public ItemStack createStack() {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(RustCraft.MODID, itemId));
        if (item == null || item == Items.AIR) {
            return new ItemStack(Items.CROSSBOW);
        }
        return new ItemStack(item);
    }

    public static BotWeapon byOrdinal(int i) {
        BotWeapon[] v = values();
        return i >= 0 && i < v.length ? v[i] : AK47;
    }
}
