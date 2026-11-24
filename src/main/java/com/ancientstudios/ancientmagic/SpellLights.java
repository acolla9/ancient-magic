// FILE: src/main/java/com/ancientstudios/ancientmagic/SpellLights.java   (NEW file)
package com.ancientstudios.ancientmagic;

import java.util.ArrayList;
import java.util.List;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Moving Veil point lights that travel with the spell geometry, so meteors, lances, Judgment pillars and Starfall stars
 * light up the terrain they pass over (the old {@code AncientFx.glow} lights are static flashes).
 *
 * Every light is a pure function of its age in seconds - exactly like {@link Fx3D} shapes - and uses the same
 * nanoTime clock, so a light stays glued to its 3D effect at any frame rate. Positions are refreshed once per frame
 * (RenderLevelStageEvent.AFTER_SKY), not once per tick, so fast movers (lances, meteors) do not stutter.
 *
 * Lights are allocated lazily (only while they are inside their life window) and capped by
 * {@link ClientConfig#maxDynamicLights()}; when the cap is reached a new light simply waits for a free slot.
 * Disabled by ClientConfig (extensiveVisuals + dynamicSpellLights); callers fall back to the old static glow.
 */
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public final class SpellLights {
    // Must match the geometry in HateVisuals / DeterminationVisuals.
    private static final Vec3 METEOR_OFF = HateVisuals.METEOR_OFF;
    private static final Vec3 STAR_OFF = new Vec3(6, 28, -4);

    /** Where the light is, how far it reaches and how bright it is at one moment. */
    public record Sample(Vec3 pos, float radius, float brightness) {}

    /** Light behaviour over time. {@code age} is seconds since this light started (after its delay). */
    @FunctionalInterface
    public interface Curve { Sample at(float age); }

    private static final class Active {
        final Curve curve;
        final float delay, life, r, g, b;
        final long start = System.nanoTime();
        LightRenderHandle<PointLightData> handle;

        Active(int rgb, float delay, float life, Curve curve) {
            this.curve = curve; this.delay = delay; this.life = life;
            this.r = ((rgb >> 16) & 255) / 255f; this.g = ((rgb >> 8) & 255) / 255f; this.b = (rgb & 255) / 255f;
        }

        void release() {
            if (handle == null) return;
            try { handle.free(); } catch (RuntimeException ignored) {}
            handle = null;
        }
    }

    private static final List<Active> ACTIVE = new ArrayList<>();

    private SpellLights() {}

    /** Registers one moving light. No-op when dynamic lights are disabled in the client config. */
    public static void add(int rgb, float delay, float life, Curve curve) {
        if (!ClientConfig.dynamicLights() || ACTIVE.size() >= 64) return;
        ACTIVE.add(new Active(rgb, Math.max(0f, delay), life, curve));
    }

    // ------------------------------------------------------------------ per-frame update

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || ACTIVE.isEmpty()) return;
        long now = System.nanoTime();
        int cap = ClientConfig.maxDynamicLights();
        int live = 0;
        for (Active a : ACTIVE) if (a.handle != null) live++;

        for (var it = ACTIVE.iterator(); it.hasNext(); ) {
            Active a = it.next();
            float age = (now - a.start) / 1e9f - a.delay;
            if (age < 0f) continue;                       // not started yet
            if (age > a.life) { a.release(); it.remove(); continue; }
            Sample s = a.curve.at(age);
            if (s == null) continue;
            try {
                if (a.handle == null || !a.handle.isValid()) {
                    if (a.handle == null && live >= cap) continue; // wait for a free slot
                    PointLightData data = new PointLightData()
                            .setPosition(s.pos.x, s.pos.y, s.pos.z).setRadius(s.radius)
                            .setColor(a.r, a.g, a.b).setBrightness(s.brightness);
                    a.handle = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
                    if (a.handle != null) live++;
                } else {
                    a.handle.getLightData().setPosition(s.pos.x, s.pos.y, s.pos.z)
                            .setRadius(s.radius).setBrightness(Math.max(0f, s.brightness));
                    a.handle.markDirty();
                }
            } catch (IllegalStateException | NullPointerException ex) {
                // Veil's renderer is not ready (world transition): drop this light.
                a.release();
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        if (Minecraft.getInstance().level == null || !ClientConfig.dynamicLights()) clear();
    }

    public static void clear() {
        for (Active a : ACTIVE) a.release();
        ACTIVE.clear();
    }

    // ------------------------------------------------------------------ small helpers

    private static float clamp01(float t) { return FxMath.clamp01(t); }

    /** Quadratic fade-out: 1 at u=0, 0 at u=1. */
    private static float fadeOut(float u) { float k = 1f - clamp01(u); return k * k; }

    private static Vec3 up(Vec3 p, double y) { return p.add(0, y, 0); }

    /** A short expanding flare on the ground / in the air (shockwave pulses). */
    private static void flare(Vec3 c, int rgb, float delay, float life, float radius, float brightness) {
        add(rgb, delay, life, age -> {
            float u = clamp01(age / life), e = FxMath.easeOutExpo(u);
            return new Sample(up(c, .8), radius * (.4f + .6f * e), brightness * fadeOut(u));
        });
    }

    // ------------------------------------------------------------------ the spells

    /** Blood Lance / Halo Lance: a light that flies from the caster to the target, then flares and fades there. */
    public static void lance(Vec3 from, Vec3 to, int rgb) {
        Vec3 d = to.subtract(from);
        float travel = .16f, len = (float) d.length();
        // head: same easing as the visual (easeOutCubic over 0.16 s), lingers at the impact point
        add(rgb, 0f, 1.3f, age -> {
            float h = FxMath.easeOutCubic(Math.min(1f, age / travel));
            float post = clamp01((age - travel) / 1.0f);
            float flare = age < travel ? 1f : 1f + 1.2f * clamp01(1f - (age - travel) / .12f);
            return new Sample(from.add(d.scale(h)), 9f - 3f * post, 2.6f * fadeOut(post) * flare);
        });
        // wake: a dimmer, wider light half-way along the beam so the whole line glows
        add(rgb, 0f, 1.0f, age -> {
            float h = FxMath.easeOutCubic(Math.min(1f, age / travel));
            float u = clamp01(age / 1.0f);
            return new Sample(from.add(d.scale(h * .5)), Math.max(5f, Math.min(20f, len * .35f)), 1.1f * fadeOut(u));
        });
    }

    /** Wrath Meteor: a hot light rides the falling rock; a faint telegraph light builds on the ground. */
    public static void meteor(Vec3 c, int rgb) {
        float fall = 1.2f;
        add(rgb, 0f, fall + .12f, age -> {
            float u = clamp01(age / fall);
            Vec3 p = c.add(METEOR_OFF.scale(1f - u * u));           // identical to HateVisuals.wrathMeteor
            return new Sample(p, FxMath.lerp(14f, 24f, u), (3.2f + 2.2f * u) * Fx3D.flicker(age, 22));
        });
        add(rgb, 0f, fall + .1f, age -> {
            float u = clamp01(age / fall);
            return new Sample(up(c, 1), FxMath.lerp(9f, 14f, u), 1.4f * u);
        });
    }

    /** Judgment: the ground glow builds during the wind-up, then three lights stacked up the pillar of light. */
    public static void judgment(Vec3 c, int rgb) {
        float strike = .7f, total = 2.7f, after = total - strike;
        add(rgb, 0f, strike, age -> {
            float u = clamp01(age / strike);
            return new Sample(up(c, 2), FxMath.lerp(5f, 11f, u), .6f + 1.8f * u);
        });
        double[] heights = {8, 18, 30};
        for (int i = 0; i < heights.length; i++) {
            final double h = heights[i];
            final int idx = i;
            add(rgb, strike, after, age -> {
                float u = clamp01(age / after);
                float flick = .88f + .12f * (float) Math.sin(age * 34f + idx * 2f);
                return new Sample(up(c, h), 13f + idx * 2f, 4.2f * fadeOut(u) * flick * (1f - idx * .15f));
            });
        }
    }

    /** Starfall: every star carries its own light while it falls, then flares where it lands. */
    public static void starfall(Vec3 c, int rgb) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            Vec3 sp = StaffType.starPos(c, i, n, 9.0);              // same layout as the server + DeterminationVisuals
            float strike = StaffType.starStrikeTick(i) / 20f;
            float fallTime = .5f, flashTime = .6f;
            add(rgb, Math.max(0f, strike - fallTime), fallTime + flashTime, age -> {
                float a = age - fallTime;                           // < 0 while falling, >= 0 after landing
                if (a < 0f) {
                    float u = 1f + a / fallTime;                    // 0 -> 1 over the fall
                    return new Sample(sp.add(STAR_OFF.scale(1f - u)), 7f, 1.6f + 1.2f * u);
                }
                float u = clamp01(a / flashTime);
                return new Sample(up(sp, 1), FxMath.lerp(6f, 9f, u), 3.2f * fadeOut(u));
            });
        }
    }

    /** Blood Moon: a big red light follows the moon up to its place in the sky, plus two shockwave flares on the ground. */
    public static void bloodMoon(Vec3 c, int rgb) {
        float total = 3.4f;
        add(rgb, 0f, total, age -> {
            float g = FxMath.easeOutCubic(clamp01(age / .6f));
            float f = Math.min(clamp01(age / .35f), clamp01((total - age) / .9f));
            return new Sample(up(c, 15f * g), 30f, 4f * f * Fx3D.flicker(age, 9));
        });
        flare(c, rgb, .7f, 1.2f, 16f, 3.6f);
        flare(c, HateVisuals.HOT, 1.4f, 1.0f, 13f, 3.0f);
    }

    /** Heaven's Gate: a light inside the opening gate, then two lights up the beam when it strikes. */
    public static void heavensGate(Vec3 c, int rgb) {
        float strike = 1.0f, total = 3.6f, after = total - strike;
        add(rgb, 0f, total, age -> {
            float open = FxMath.easeOutBack(clamp01(age / .8f));
            float gf = age < strike ? 1f : 1f - clamp01((age - strike - .6f) / (total - strike - .6f));
            return new Sample(up(c, 13), 22f, 3.2f * Math.max(0f, open) * gf);
        });
        add(rgb, 0f, strike, age -> {                                // glow building on the ground below the gate
            float u = clamp01(age / strike);
            return new Sample(up(c, 2), FxMath.lerp(5f, 12f, u), .5f + 1.5f * u);
        });
        double[] heights = {6, 16};
        for (int i = 0; i < heights.length; i++) {
            final double h = heights[i];
            final int idx = i;
            add(rgb, strike, after, age -> {
                float u = clamp01(age / after);
                float flick = .9f + .1f * (float) Math.sin(age * 30f + idx * 3f);
                return new Sample(up(c, h), 16f, 5f * fadeOut(u) * flick);
            });
        }
    }
}