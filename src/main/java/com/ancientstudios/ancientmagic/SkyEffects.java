// FILE: src/main/java/com/ancientstudios/ancientmagic/SkyEffects.java   (REPLACES the existing file)
package com.ancientstudios.ancientmagic;

import java.util.ArrayList;
import java.util.List;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Sky and world effects: Blood Moon tints the sky and fog red for everyone nearby, Heaven's Gate briefly turns the
 * sky golden. Purely client side - every client reacts to the same SpellFxPayload that already drives the particles.
 *
 * Two layers work together, so it still looks right if one of them is unavailable:
 *  1. VANILLA hooks (always on): ViewportEvent.ComputeFogColor recolours the fog / horizon, ViewportEvent.RenderFog
 *     pulls terrain fog in so the red mist closes around you.
 *  2. VEIL post pipeline "ancientmagic:sky_tint" (pinwheel/post/sky_tint.json + shaders/program/sky_grade.fsh): repaints
 *     the sky dome itself (found through the depth buffer), sinks distant terrain into the tint and grades the scene.
 *     It is only added to Veil's post-processing manager while an effect is visible.
 *
 * Gated by ClientConfig (extensiveVisuals + skyEffects); the pulsing is removed in reduced-motion mode.
 */
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public final class SkyEffects {
    private static final ResourceLocation PIPE = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "sky_tint");

    /** Look of one effect. Colours are 0..1 RGB. darken < 0 brightens the scene. */
    public enum Kind {
        //          sky tint                  fog colour               attack hold  release  fogPull darken haze  pulse elev
        // Blood Moon is sky-only: don't recolour fog or terrain, which made the whole world look red.
        BLOOD_MOON(.62f, .03f, .06f,          .42f, .01f, .03f,         .60f, 3.0f, 2.6f,     0f,      0f,   0f,   1f,   .40f),
        HEAVENS_GATE(1.00f, .80f, .38f,       1.00f, .86f, .50f,        .25f, 1.3f, 2.2f,     .10f,  -.15f,  .55f, 0f,   .95f);

        /** elev = elevation (radians above the horizon) of the painted moon / sun. */
        final float r, g, b, fr, fg, fb, attack, hold, release, fogPull, darken, haze, pulse, elev;

        Kind(float r, float g, float b, float fr, float fg, float fb, float attack, float hold, float release,
             float fogPull, float darken, float haze, float pulse, float elev) {
            this.elev = elev;
            this.r = r; this.g = g; this.b = b; this.fr = fr; this.fg = fg; this.fb = fb;
            this.attack = attack; this.hold = hold; this.release = release;
            this.fogPull = fogPull; this.darken = darken; this.haze = haze; this.pulse = pulse;
        }

        float total() { return attack + hold + release; }
    }

    /** azimuth = compass angle (radians) of the painted moon / sun; derived from the cast point so every client agrees. */
    private record Event(Kind kind, Vec3 pos, long startNanos, float azimuth) {}

    /** Blended result of every running effect for the local camera, recomputed at most once per few ms. */
    private static final class State {
        float r, g, b, fr, fg, fb, amount, fogPull, darken, haze, pulse;
        /** 0 = Blood Moon look, 1 = Heaven's Gate look (blended while both run) and the moon / sun direction. */
        float kind, dx, dy = 1f, dz;
    }

    private static final List<Event> EVENTS = new ArrayList<>();
    private static final State S = new State();
    private static long stateStamp;
    private static boolean pipeActive;
    private static ClientLevel lastLevel;

    private SkyEffects() {}

    /** Call once on client setup (uploads the shader uniforms every frame while the pipeline is active). */
    public static void init() {
        VeilEventPlatform.INSTANCE.preVeilPostProcessing(SkyEffects::upload);
    }

    // ------------------------------------------------------------------ triggers (called from HateFx / DeterminationFx)

    public static void bloodMoon(Vec3 centre) { trigger(Kind.BLOOD_MOON, centre); }

    public static void heavensGate(Vec3 centre) { trigger(Kind.HEAVENS_GATE, centre); }

    public static void trigger(Kind kind, Vec3 pos) {
        if (!ClientConfig.skyEffects()) return;
        if (EVENTS.size() >= 8) EVENTS.remove(0);
        float az = (float) ((Math.abs(Math.sin(pos.x * 12.9898 + pos.z * 78.233) * 43758.5453) % 1.0) * Math.PI * 2.0);
        EVENTS.add(new Event(kind, pos, System.nanoTime(), az));
        stateStamp = 0L;
        BloomFx.pulse(kind == Kind.BLOOD_MOON ? .45f : .60f, kind.total());   // soft glow for the whole effect
    }

    // ------------------------------------------------------------------ state

    private static float smooth(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return t * t * (3f - 2f * t);
    }

    private static float envelope(Kind k, float age) {
        if (age < k.attack) return smooth(age / k.attack);
        if (age < k.attack + k.hold) return 1f;
        return 1f - smooth((age - k.attack - k.hold) / k.release);
    }

    /** 1 near the cast point, fading to 0 at the configured range (fade starts at half of it). */
    private static float rangeFactor(Vec3 cam, Vec3 pos) {
        float range = ClientConfig.skyEffectRange();
        float d = (float) cam.distanceTo(pos);
        return 1f - smooth((d - range * .5f) / (range * .5f));
    }

    private static State state() {
        long now = System.nanoTime();
        if (now - stateStamp < 2_000_000L) return S; // ~2 ms cache; several callbacks per frame ask for it
        stateStamp = now;

        Minecraft mc = Minecraft.getInstance();
        S.amount = 0f; S.fogPull = 0f; S.darken = 0f; S.haze = 0f; S.pulse = 0f;
        if (mc.player == null || mc.level == null || EVENTS.isEmpty()) return S;

        Vec3 cam = mc.player.getEyePosition();
        float wsum = 0f, keep = 1f, r = 0, g = 0, b = 0, fr = 0, fg = 0, fb = 0, kindSum = 0f, best = 0f;
        Event bestEvent = null;
        boolean reduced = ClientConfig.reducedMotion();
        for (var it = EVENTS.iterator(); it.hasNext(); ) {
            Event e = it.next();
            float age = (now - e.startNanos) / 1e9f;
            if (age >= e.kind.total()) { it.remove(); continue; }
            float a = envelope(e.kind, age) * rangeFactor(cam, e.pos);
            if (a <= 0f) continue;
            r += e.kind.r * a; g += e.kind.g * a; b += e.kind.b * a;
            fr += e.kind.fr * a; fg += e.kind.fg * a; fb += e.kind.fb * a;
            wsum += a;
            keep *= 1f - a;
            kindSum += e.kind.ordinal() * a;
            if (a > best) { best = a; bestEvent = e; }
            S.fogPull = Math.max(S.fogPull, e.kind.fogPull * a);
            S.darken += e.kind.darken * a;
            S.haze = Math.max(S.haze, e.kind.haze * a);
            if (!reduced) S.pulse = Math.max(S.pulse, e.kind.pulse * a * (.5f + .5f * (float) Math.sin(age * 6.0)));
        }
        if (wsum <= 0f) return S;
        S.r = r / wsum; S.g = g / wsum; S.b = b / wsum;
        S.fr = fr / wsum; S.fg = fg / wsum; S.fb = fb / wsum;
        S.amount = 1f - keep;
        S.kind = kindSum / wsum;
        if (bestEvent != null) {
            float el = bestEvent.kind.elev, az = bestEvent.azimuth;
            S.dx = (float) (Math.cos(el) * Math.cos(az));
            S.dy = (float) Math.sin(el);
            S.dz = (float) (Math.cos(el) * Math.sin(az));
        }
        return S;
    }

    // ------------------------------------------------------------------ layer 1: vanilla fog hooks

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor e) {
        State s = state();
        if (s.amount < .005f) return;
        float a = Math.min(1f, s.amount * .9f);
        e.setRed(e.getRed() + (s.fr - e.getRed()) * a);
        e.setGreen(e.getGreen() + (s.fg - e.getGreen()) * a);
        e.setBlue(e.getBlue() + (s.fb - e.getBlue()) * a);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog e) {
        State s = state();
        if (s.amount < .005f || s.fogPull <= 0f) return;
        // Only the terrain fog, and never underwater / in lava / powder snow.
        if (e.getMode() != FogRenderer.FogMode.FOG_TERRAIN || e.getType() != FogType.NONE) return;
        float pull = Math.min(1f, s.fogPull);
        float far = e.getFarPlaneDistance(), near = e.getNearPlaneDistance();
        float targetFar = Math.min(far, 40f + far * .2f);
        e.setFarPlaneDistance(far + (targetFar - far) * pull);
        e.setNearPlaneDistance(near * (1f - .85f * pull));
        e.setCanceled(true); // NeoForge only applies the changed values when the event is cancelled
    }

    // ------------------------------------------------------------------ layer 2: Veil post pipeline

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { // dimension change / disconnect: drop everything
            EVENTS.clear();
            lastLevel = mc.level;
            stateStamp = 0L;
        }
        boolean want = mc.level != null && ClientConfig.skyEffects() && state().amount > .01f;
        if (want == pipeActive) return;
        try {
            var pm = VeilRenderSystem.renderer().getPostProcessingManager();
            if (want) pm.add(PIPE); else pm.remove(PIPE);
            pipeActive = want;
        } catch (RuntimeException ignored) {
            // Veil not ready yet: the vanilla fog layer above still works, we retry next tick.
        }
    }

    private static void upload(ResourceLocation name, PostPipeline pipeline, PostPipeline.Context context) {
        if (!PIPE.equals(name)) return;
        State s = state();
        ShaderUniformAccess tint = pipeline.getUniform("SkyTint");
        if (tint != null) tint.setVector(s.r, s.g, s.b);
        f(pipeline, "SkyAmount", s.amount);
        f(pipeline, "SkyDarken", s.darken);
        f(pipeline, "SkyHaze", s.haze);
        f(pipeline, "SkyPulse", s.pulse);
        // painted sky (moon / aurora / clouds): time, which look, where the moon / sun sits
        f(pipeline, "SkyTime", (System.nanoTime() / 1_000_000L % 3_600_000L) / 1000f);
        f(pipeline, "SkyKind", s.kind);
        // Temporarily keep the unstable painted projection disabled; the base vanilla sky
        // is still recoloured by sky_grade, while terrain/fog remain untouched for Blood Moon.
        f(pipeline, "SkyContent", 0f);
        f(pipeline, "SkyMotion", ClientConfig.reducedMotion() ? .25f : 1f);
        ShaderUniformAccess dir = pipeline.getUniform("SkyDir");
        if (dir != null) dir.setVector(s.dx, s.dy, s.dz);
    }

    private static void f(PostPipeline p, String n, float v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null) u.setFloat(v);
    }
}
