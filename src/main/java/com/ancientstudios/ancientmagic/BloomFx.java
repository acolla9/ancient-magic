// FILE: src/main/java/com/ancientstudios/ancientmagic/BloomFx.java   (NEW file)
package com.ancientstudios.ancientmagic;

import java.util.ArrayList;
import java.util.List;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Bloom glow for big spells. The Veil pipeline "ancientmagic:bloom" (pinwheel/post/bloom.json) extracts the bright
 * parts of the scene, blurs them at 1/2 and 1/4 resolution and adds them back. It is only added to Veil's
 * post-processing manager while at least one "pulse" is alive, so it costs nothing the rest of the time.
 *
 * Usage from any effect code:  BloomFx.pulse(0.8f, 1.2f);   // peak strength ~0..1, duration in seconds
 * SkyEffects and ImpactFrames already call it. Gated by ClientConfig (extensiveVisuals + bloom + bloomIntensity).
 */
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public final class BloomFx {
    private static final ResourceLocation PIPE = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "bloom");

    /** Brightness (0..1, max colour channel) above which a pixel starts to glow. */
    private static final float THRESHOLD = .80f;
    /** Seconds for the glow to ramp up to its peak. */
    private static final float ATTACK = .06f;

    private record Pulse(float peak, float seconds, long startNanos) {}

    private static final List<Pulse> PULSES = new ArrayList<>();
    private static boolean pipeActive;
    private static ClientLevel lastLevel;

    private BloomFx() {}

    /** Call once on client setup. */
    public static void init() {
        VeilEventPlatform.INSTANCE.preVeilPostProcessing(BloomFx::upload);
    }

    /** Add a glow pulse: fast attack, then a smooth fade over {@code seconds}. Overlapping pulses combine softly. */
    public static void pulse(float peak, float seconds) {
        if (!ClientConfig.bloom() || peak <= 0.01f) return;
        if (PULSES.size() >= 12) PULSES.remove(0);
        PULSES.add(new Pulse(Math.min(1.5f, peak), Math.max(.15f, seconds), System.nanoTime()));
    }

    /** Current combined strength 0..~1.5 (drops finished pulses). */
    private static float current() {
        if (PULSES.isEmpty()) return 0f;
        long now = System.nanoTime();
        float keep = 1f;
        for (var it = PULSES.iterator(); it.hasNext(); ) {
            Pulse p = it.next();
            float age = (now - p.startNanos) / 1e9f;
            if (age >= p.seconds) { it.remove(); continue; }
            float env;
            if (age < ATTACK) {
                float t = age / ATTACK;
                env = t * t * (3f - 2f * t);
            } else {
                float t = (age - ATTACK) / Math.max(.01f, p.seconds - ATTACK);
                env = (float) Math.pow(1f - t, 1.6);
            }
            keep *= 1f - Math.min(1f, p.peak * env);
        }
        return 1f - keep;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) { // dimension change / disconnect
            PULSES.clear();
            lastLevel = mc.level;
        }
        boolean want = mc.level != null && ClientConfig.bloom() && current() > .01f;
        if (want == pipeActive) return;
        try {
            var pm = VeilRenderSystem.renderer().getPostProcessingManager();
            if (want) pm.add(PIPE); else pm.remove(PIPE);
            pipeActive = want;
        } catch (RuntimeException ignored) {
            // Veil not ready yet, retry next tick.
        }
    }

    private static void upload(ResourceLocation name, PostPipeline pipeline, PostPipeline.Context context) {
        if (!PIPE.equals(name)) return;
        f(pipeline, "BloomStrength", current() * ClientConfig.bloomScale());
        f(pipeline, "BloomThreshold", THRESHOLD);
    }

    private static void f(PostPipeline p, String n, float v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null) u.setFloat(v);
    }
}
