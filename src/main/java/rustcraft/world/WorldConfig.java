package rustcraft.world;

import net.minecraftforge.common.ForgeConfigSpec;

/** Настройки модуля world (config/rustcraft-world.toml). */
public final class WorldConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue AUTO_MAP;
    public static final ForgeConfigSpec.BooleanValue MONUMENT_PROTECTION;
    public static final ForgeConfigSpec.IntValue BUILD_BUDGET_MS;
    public static final ForgeConfigSpec.IntValue CRATE_REFILL_MINUTES;
    public static final ForgeConfigSpec.IntValue BARREL_RESPAWN_MINUTES;
    public static final ForgeConfigSpec.IntValue HACK_SECONDS;
    public static final ForgeConfigSpec.IntValue LOCKED_CRATE_RESET_MINUTES;
    public static final ForgeConfigSpec.IntValue NPC_RESPAWN_SECONDS;
    public static final ForgeConfigSpec.IntValue NPC_RESPAWN_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<String> FALLBACK_NPC;
    public static final ForgeConfigSpec.BooleanValue SULFUR_ORE_GEN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("map");
        AUTO_MAP = b.comment("rustcraft.autoMap: build the RustCraft map automatically on the first start of a NEW world")
                .define("autoMap", true);
        MONUMENT_PROTECTION = b.comment("Survival players cannot break blocks inside monuments; explosions do not damage monuments")
                .define("monumentProtection", true);
        BUILD_BUDGET_MS = b.comment("Milliseconds per server tick spent on map construction")
                .defineInRange("buildBudgetMs", 10, 2, 40);
        b.pop();
        b.push("loot");
        CRATE_REFILL_MINUTES = b.defineInRange("crateRefillMinutes", 10, 1, 1440);
        BARREL_RESPAWN_MINUTES = b.defineInRange("barrelRespawnMinutes", 8, 1, 1440);
        HACK_SECONDS = b.comment("Locked crate hacking time").defineInRange("hackSeconds", 120, 5, 3600);
        LOCKED_CRATE_RESET_MINUTES = b.defineInRange("lockedCrateResetMinutes", 30, 1, 1440);
        b.pop();
        b.push("npc");
        NPC_RESPAWN_SECONDS = b.defineInRange("npcRespawnSeconds", 150, 5, 36000);
        NPC_RESPAWN_MULTIPLIER = b.comment("Множитель интервала восстановления учёных: 4 = появляются в четыре раза реже")
                .defineInRange("respawnIntervalMultiplier", 4, 1, 100);
        FALLBACK_NPC = b.comment("Entity used by NPC spawners when the configured entity (e.g. rustcraft:scientist) is missing. Empty = none")
                .define("fallbackNpc", "minecraft:pillager");
        b.pop();
        b.push("worldgen");
        SULFUR_ORE_GEN = b.define("sulfurOre", true);
        b.pop();
        SPEC = b.build();
    }

    private WorldConfig() {}
}
