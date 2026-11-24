// FILE: src/main/java/com/ancientstudios/ancientmagic/ClientConfig.java   (NEW file)
package com.ancientstudios.ancientmagic;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only visual settings. File: config/ancientmagic-client.toml (never synced, never read by a dedicated server).
 *
 * <ul>
 *   <li><b>extensiveVisuals</b> is the TEMPORARY master switch for the upcoming "Extensive Visuals" pack
 *       (sky/fog takeovers, moving spell lights, the extra impact-frame flash + aberration boost).
 *       Off = the classic look. Delete it (or flip the default) once Extensive Visuals ships.</li>
 *   <li><b>reducedMotion</b> is the comfort switch for people who get motion sick: it removes camera shake and
 *       strobing and softens the impact shaders, whatever the sliders below say.</li>
 * </ul>
 *
 * Every accessor is safe to call before the config has loaded (returns the default instead of throwing).
 */
public final class ClientConfig {
    /** Colour of the short full-screen impact flash. AUTO = red for the Staff of Hate, white for Determination. */
    public enum FlashStyle { AUTO, RED, WHITE, OFF }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- visuals
    public static final ModConfigSpec.BooleanValue EXTENSIVE_VISUALS;
    public static final ModConfigSpec.BooleanValue SKY_EFFECTS;
    public static final ModConfigSpec.IntValue SKY_EFFECT_RANGE;
    public static final ModConfigSpec.BooleanValue DYNAMIC_SPELL_LIGHTS;
    public static final ModConfigSpec.IntValue MAX_DYNAMIC_LIGHTS;
    public static final ModConfigSpec.BooleanValue UNIQUE_CAST_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue SKY_CONTENT;
    public static final ModConfigSpec.BooleanValue BLOOM;
    public static final ModConfigSpec.DoubleValue BLOOM_INTENSITY;
    public static final ModConfigSpec.BooleanValue INK_IMPACT;

    // ---------------------------------------------------------------- comfort / impact frames
    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES;
    public static final ModConfigSpec.DoubleValue SCREEN_SHAKE;
    public static final ModConfigSpec.DoubleValue CHROMATIC_ABERRATION;
    public static final ModConfigSpec.DoubleValue FLASH_INTENSITY;
    public static final ModConfigSpec.EnumValue<FlashStyle> FLASH_STYLE;
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION;

    static {
        BUILDER.push("visuals");
        EXTENSIVE_VISUALS = BUILDER
                .comment("TEMPORARY master switch for the Extensive Visuals pack: sky/fog effects, moving spell lights and the",
                        "extra impact-frame flash / aberration boost. Turn off for the classic visuals.")
                .define("extensiveVisuals", true);
        SKY_EFFECTS = BUILDER
                .comment("Blood Moon tints the sky and fog red, Heaven's Gate turns the sky golden. Needs extensiveVisuals.")
                .define("skyEffects", true);
        SKY_EFFECT_RANGE = BUILDER
                .comment("Blocks from the cast point within which the sky/fog change is seen. It fades out smoothly towards this distance.")
                .defineInRange("skyEffectRange", 96, 16, 256);
        DYNAMIC_SPELL_LIGHTS = BUILDER
                .comment("Meteors, lances, Judgment pillars, Starfall stars, Blood Moon and Heaven's Gate carry a moving Veil",
                        "light that lights up the terrain. Needs extensiveVisuals.")
                .define("dynamicSpellLights", true);
        MAX_DYNAMIC_LIGHTS = BUILDER
                .comment("Most moving spell lights alive at once. Lower this if your FPS drops during big casts.")
                .defineInRange("maxDynamicLights", 20, 1, 64);
        UNIQUE_CAST_ANIMATIONS = BUILDER
                .comment("Each ability plays its own cast pose (beam, sweep, slam...). Off = no cast pose at all.")
                .define("uniqueCastAnimations", true);
        SKY_CONTENT = BUILDER
                .comment("Paint a real moon, aurora, sun rays and drifting clouds into the Blood Moon / Heaven's Gate sky instead of only",
                        "recolouring it. Costs a little GPU time while a sky effect is visible. Needs extensiveVisuals + skyEffects.")
                .define("skyContent", true);
        BLOOM = BUILDER
                .comment("Soft glow around bright spell effects during big casts and impacts (Veil post pipeline). Needs extensiveVisuals.")
                .define("bloom", true);
        BLOOM_INTENSITY = BUILDER
                .comment("Strength of the bloom glow. 0 = off, 1 = default, 2 = very strong. Halved in reduced-motion mode.")
                .defineInRange("bloomIntensity", 1.0, 0.0, 2.0);
        INK_IMPACT = BUILDER
                .comment("Hate impact frame gets radial blur, ink outlines, halftone dots and a hard black/white first frame.",
                        "The hard first frame is always skipped in reduced-motion mode. Needs extensiveVisuals.")
                .define("inkImpact", true);
        BUILDER.pop();

        BUILDER.push("comfort");
        IMPACT_FRAMES = BUILDER
                .comment("The Hate / Determination full-screen impact shaders. Off removes them completely (shake is separate).")
                .define("impactFrames", true);
        SCREEN_SHAKE = BUILDER
                .comment("Camera shake multiplier. 1.0 = default, 0 = none. Shake already fades with distance to the impact.")
                .defineInRange("screenShake", 1.0, 0.0, 1.0);
        CHROMATIC_ABERRATION = BUILDER
                .comment("Colour-fringing multiplier on impact frames. 1.0 = default, 0 = none, 2.0 = extra strong.")
                .defineInRange("chromaticAberration", 1.0, 0.0, 2.0);
        FLASH_INTENSITY = BUILDER
                .comment("Brightness of the short red / white flash on big hits. 0 = no flash. Needs extensiveVisuals.")
                .defineInRange("flashIntensity", 1.0, 0.0, 1.0);
        FLASH_STYLE = BUILDER
                .comment("AUTO = red for Hate, white for Determination. RED / WHITE force a colour, OFF disables the flash.")
                .defineEnum("flashStyle", FlashStyle.AUTO);
        REDUCED_MOTION = BUILDER
                .comment("Motion-sickness mode: no camera shake, no speed lines or screen warping, much weaker flash and",
                        "aberration, no pulsing in the sky effects. Overrides the sliders above.")
                .define("reducedMotion", false);
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    private static <T> T get(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    // ---------------------------------------------------------------- accessors used by the visuals

    public static boolean extensive() { return get(EXTENSIVE_VISUALS, true); }
    public static boolean skyEffects() { return extensive() && get(SKY_EFFECTS, true); }
    public static boolean skyContent() { return skyEffects() && get(SKY_CONTENT, true); }
    public static boolean bloom() { return extensive() && get(BLOOM, true) && get(BLOOM_INTENSITY, 1.0) > 0.01; }
    /** Bloom multiplier from the slider (halved in reduced-motion mode). */
    public static float bloomScale() { return (float) (get(BLOOM_INTENSITY, 1.0) * (reducedMotion() ? .5 : 1.0)); }
    public static boolean inkImpact() { return extensive() && get(INK_IMPACT, true); }
    public static int skyEffectRange() { return get(SKY_EFFECT_RANGE, 96); }
    public static boolean dynamicLights() { return extensive() && get(DYNAMIC_SPELL_LIGHTS, true); }
    public static int maxDynamicLights() { return get(MAX_DYNAMIC_LIGHTS, 20); }
    public static boolean castAnimations() { return get(UNIQUE_CAST_ANIMATIONS, true); }

    public static boolean impactFrames() { return get(IMPACT_FRAMES, true); }
    public static boolean reducedMotion() { return get(REDUCED_MOTION, false); }

    /** Camera shake multiplier, already 0 in reduced-motion mode. */
    public static float shakeScale() { return reducedMotion() ? 0f : get(SCREEN_SHAKE, 1.0).floatValue(); }

    /** Chromatic aberration multiplier (reduced-motion cuts it to a quarter). */
    public static float chromaScale() {
        float v = get(CHROMATIC_ABERRATION, 1.0).floatValue();
        return reducedMotion() ? v * .25f : v;
    }

    /** Flash brightness multiplier. 0 when the extra impact effects are off, the style is OFF, or the slider is 0. */
    public static float flashScale() {
        if (!extensive() || get(FLASH_STYLE, FlashStyle.AUTO) == FlashStyle.OFF) return 0f;
        float v = get(FLASH_INTENSITY, 1.0).floatValue();
        return reducedMotion() ? v * .25f : v;
    }

    public static FlashStyle flashStyle() { return get(FLASH_STYLE, FlashStyle.AUTO); }

    /** Extra impact-frame effects (aberration boost by proximity, colour flash) belong to Extensive Visuals. */
    public static boolean extraImpactEffects() { return extensive(); }
}