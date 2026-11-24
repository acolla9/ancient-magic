package com.ancientstudios.ancientmagic;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side tuning for the staffs. File: config/ancientmagic-common.toml
 * Values are read every time a spell is cast, so changes made through the config screen apply immediately.
 */
public final class Config {
    /** How spell explosions treat blocks. */
    public enum BlockDamage {
        /** Explosions never break blocks (entities are still hit). */
        NONE,
        /** Explosions break blocks only if the mobGriefing gamerule is on. */
        MOB_GRIEFING,
        /** Explosions always break blocks (original behaviour). */
        ALWAYS
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- casting
    public static final ModConfigSpec.IntValue CAST_PERMISSION_LEVEL;
    public static final ModConfigSpec.DoubleValue COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ABILITIES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_DIMENSIONS;

    // ---------------------------------------------------------------- combat
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue TRUE_DAMAGE;
    public static final ModConfigSpec.BooleanValue AFFECT_PLAYERS;
    public static final ModConfigSpec.BooleanValue AFFECT_CREATIVE;
    public static final ModConfigSpec.BooleanValue SPARE_TEAMMATES;
    public static final ModConfigSpec.BooleanValue PROTECT_PETS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> IMMUNE_ENTITIES;

    // ---------------------------------------------------------------- world / performance
    public static final ModConfigSpec.EnumValue<BlockDamage> BLOCK_DAMAGE;
    public static final ModConfigSpec.IntValue BLOCK_DAMAGE_PERMISSION_LEVEL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCK_DAMAGE_DISABLED_ABILITIES;
    public static final ModConfigSpec.IntValue MAX_RANGE;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_ZONES;

    static {
        BUILDER.push("casting");
        CAST_PERMISSION_LEVEL = BUILDER
                .comment("Minimum permission level (0-4) a player needs to cast. 0 = anyone holding a staff, 2 = operators only.")
                .defineInRange("castPermissionLevel", 0, 0, 4);
        COOLDOWN_MULTIPLIER = BUILDER
                .comment("Multiplier applied to every ability cooldown. 2.0 = twice as long, 0 = no cooldown.")
                .defineInRange("cooldownMultiplier", 1.0, 0.0, 100.0);
        DISABLED_ABILITIES = BUILDER
                .comment("Abilities that cannot be cast, as \"staff:number\" using the wheel numbers 1-10.",
                        "Example: [\"hate:6\", \"determination:10\"] disables Wrath Meteor and Heaven's Gate.")
                .defineListAllowEmpty("disabledAbilities", List.of(), () -> "hate:1", Config::validateAbility);
        DISABLED_DIMENSIONS = BUILDER
                .comment("Dimension ids where the staffs cannot be cast, e.g. [\"minecraft:the_end\"].")
                .defineListAllowEmpty("disabledDimensions", List.of(), () -> "minecraft:the_end", Config::validateId);
        BUILDER.pop();

        BUILDER.push("combat");
        DAMAGE_MULTIPLIER = BUILDER
                .comment("Multiplier applied to all spell damage. 1.0 = default, 0.5 = half.")
                .defineInRange("damageMultiplier", 1.0, 0.0, 1000.0);
        TRUE_DAMAGE = BUILDER
                .comment("If true, the execute-style hits (Blood Lance's first target, Judgment, the core of Heaven's Gate) ignore",
                        "armor, Resistance, totems, shields and invulnerability. If false they use normal magic-style damage.")
                .define("trueDamage", true);
        AFFECT_PLAYERS = BUILDER
                .comment("If false, spells never damage or push other players (PvE only).")
                .define("affectPlayers", true);
        AFFECT_CREATIVE = BUILDER
                .comment("If true, spells can also hit creative-mode players. Spectators are never hit.")
                .define("affectCreativePlayers", false);
        SPARE_TEAMMATES = BUILDER
                .comment("If true, players on the caster's scoreboard team are never hit.")
                .define("spareTeammates", true);
        PROTECT_PETS = BUILDER
                .comment("If true, tamed pets and tamed horses are never targeted by spell effects.")
                .define("protectTamedPets", true);
        IMMUNE_ENTITIES = BUILDER
                .comment("Entity type ids that spells never target, e.g. [\"minecraft:villager\", \"minecraft:iron_golem\"].")
                .defineListAllowEmpty("immuneEntities", List.of(), () -> "minecraft:villager", Config::validateId);
        BUILDER.pop();

        BUILDER.push("world");
        BLOCK_DAMAGE = BUILDER
                .comment("Whether spell explosions break blocks. NOTE: explosions still hurt anything inside their blast radius;",
                        "use immuneEntities / protectTamedPets for the spell effects, and keep NONE on spawn-protected servers.")
                .defineEnum("blockDamage", BlockDamage.ALWAYS);
        BLOCK_DAMAGE_PERMISSION_LEVEL = BUILDER
                .comment("Minimum permission level required for a caster's spell to break blocks. Set to 4 for operators only.")
                .defineInRange("blockDamagePermissionLevel", 2, 0, 4);
        BLOCK_DAMAGE_DISABLED_ABILITIES = BUILDER
                .comment("Abilities that never break blocks, using staff:number syntax, e.g. [\"hate:6\", \"determination:2\"].")
                .defineListAllowEmpty("blockDamageDisabledAbilities", List.of(), () -> "hate:1", Config::validateAbility);
        MAX_RANGE = BUILDER
                .comment("Hard cap (blocks) on how far spells can be aimed. 0 = automatic (server view distance, at least 64).")
                .defineInRange("maxRange", 0, 0, 2048);
        MAX_ACTIVE_ZONES = BUILDER
                .comment("Maximum number of lingering zones (wells, chains, curses, sanctuaries) alive at once. Oldest are dropped first.")
                .defineInRange("maxActiveZones", 64, 1, 1024);
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    /** True if the 0-based ability of the given staff is switched off in the config. */
    public static boolean isAbilityDisabled(StaffType type, int ability) {
        String key = (type.isHate() ? "hate" : "determination") + ":" + (ability + 1);
        for (String s : DISABLED_ABILITIES.get()) {
            if (key.equalsIgnoreCase(s.trim())) return true;
        }
        return false;
    }

    public static boolean isDimensionDisabled(String dimensionId) {
        for (String s : DISABLED_DIMENSIONS.get()) {
            if (dimensionId.equals(s.trim())) return true;
        }
        return false;
    }

    private static boolean validateAbility(Object o) {
        if (!(o instanceof String s)) return false;
        String[] parts = s.trim().toLowerCase().split(":");
        if (parts.length != 2 || !(parts[0].equals("hate") || parts[0].equals("determination"))) return false;
        try {
            int n = Integer.parseInt(parts[1]);
            return n >= 1 && n <= StaffType.COUNT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean validateId(Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s.trim()) != null;
    }
}
