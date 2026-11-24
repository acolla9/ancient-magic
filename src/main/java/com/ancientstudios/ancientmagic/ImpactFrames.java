// FILE: src/main/java/com/ancientstudios/ancientmagic/ImpactFrames.java   (REPLACES the existing file)
package com.ancientstudios.ancientmagic;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.shader.uniform.ShaderUniformAccess;
import foundry.veil.platform.VeilEventPlatform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

/**
 * Anime-style "impact frames" built on Veil post-processing, plus camera shake.
 * Hate = inverted blood-ink with speed lines; Determination = holy white-out with god rays.
 * The pipelines live in assets/ancientmagic/pinwheel/post/ and are only active while an impact is playing.
 *
 * What is new compared with the first version:
 *  - Everything is scaled by DISTANCE to the impact point (see {@link #proximity}): shake, aberration and flash all
 *    fade smoothly instead of the old hard cut-off, and bigger spells are felt from further away.
 *  - Chromatic aberration is now a shader uniform (ImpactChroma), strongest on the first frames of a hit.
 *  - A short full-screen flash (red for Hate, white for Determination, see {@link ClientConfig.FlashStyle}).
 *  - Everything honours {@link ClientConfig}: shake / aberration / flash sliders and the reduced-motion switch.
 */
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public final class ImpactFrames {
    private static final ResourceLocation HATE_PIPE = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "impact_hate");
    private static final ResourceLocation HOLY_PIPE = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "impact_determination");

    /** Peak opacity of the colour flash at full strength, full slider and zero distance. Kept below 1 on purpose. */
    private static final float FLASH_PEAK = .5f;
    /** Seconds-constant of the flash: exp(-t * FLASH_DECAY) -> about 0.2 s to fade out. */
    private static final float FLASH_DECAY = 12f;

    private static ResourceLocation active;
    private static boolean activeIsHate;
    private static long startNanos;
    private static float seconds = .5f, strength = 1f, proximity = 1f;
    private static Vec3 worldPos = Vec3.ZERO;
    private static float uvX = .5f, uvY = .5f;

    private static long shakeStart;
    private static float shakeSeconds, shakeAmp;

    private ImpactFrames() {}

    /** Call once on client setup. Uploads the per-frame uniforms to our pipelines. */
    public static void init() {
        VeilEventPlatform.INSTANCE.preVeilPostProcessing(ImpactFrames::upload);
    }

    /**
     * 0..1 "how much of this impact reaches the player". 1 inside the near radius, 0 beyond the far radius, smooth in
     * between. Both radii grow with the spell's power, so a Heaven's Gate rumbles over a much wider area than a lance.
     */
    static float proximity(double distance, float power) {
        float p = Math.max(.3f, Math.min(1.6f, power));
        float near = 8f + 8f * p, far = 32f + 32f * p;
        float t = (float) Math.max(0.0, Math.min(1.0, (distance - near) / (far - near)));
        float s = t * t * (3f - 2f * t);
        return 1f - s;
    }

    /** Trigger an impact frame + shake. power ~0.4 (small) .. 1.8 (huge); shakeDeg is camera shake in degrees. */
    public static void hit(StaffType type, Vec3 pos, float power, float secs, float shakeDeg) {
        hit(type, pos, power, secs, shakeDeg, false);
    }

    /** @param local true when the local player cast the spell: it is always felt at (almost) full strength. */
    public static void hit(StaffType type, Vec3 pos, float power, float secs, float shakeDeg, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float prox = proximity(mc.player.getEyePosition().distanceTo(pos), power);
        if (local) prox = Math.max(prox, .9f);
        if (prox < .03f) return;

        float k = Math.min(1.25f, power);

        // Camera shake: falls off a bit faster than linear so far-away rumbles are subtle.
        float shakeAmpNow = shakeDeg * Math.min(1f, k) * prox * (float) Math.sqrt(prox) * ClientConfig.shakeScale();
        if (shakeAmpNow > .02f) shake(shakeAmpNow, Math.max(.3f, secs));

        // Bloom glow on every felt hit (see BloomFx), even when the full-screen impact shader is off.
        BloomFx.pulse(Math.min(1f, k * prox) * .9f, Math.max(.35f, secs * 1.6f));

        // The full-screen shader only makes sense when the impact is reasonably close.
        if (prox < .12f || !ClientConfig.impactFrames()) return;
        ResourceLocation want = type.isHate() ? HATE_PIPE : HOLY_PIPE;
        try {
            PostProcessingManager pm = VeilRenderSystem.renderer().getPostProcessingManager();
            if (active != null && !active.equals(want)) pm.remove(active);
            pm.add(want);
            active = want;
            activeIsHate = type.isHate();
            startNanos = System.nanoTime();
            seconds = Math.max(.15f, secs);
            strength = Math.min(1f, k * prox);
            proximity = prox;
            worldPos = pos;
        } catch (RuntimeException ignored) {
            active = null;
        }
    }

    public static void shake(float amplitudeDeg, float secs) {
        float remaining = shakeAmp * Math.max(0f, 1f - (System.nanoTime() - shakeStart) / 1e9f / Math.max(.01f, shakeSeconds));
        if (amplitudeDeg < remaining) return;
        shakeAmp = amplitudeDeg;
        shakeSeconds = secs;
        shakeStart = System.nanoTime();
    }

    private static void upload(ResourceLocation name, PostPipeline pipeline, PostPipeline.Context context) {
        if (active == null || !name.equals(active)) return;
        float ageSec = (System.nanoTime() - startNanos) / 1e9f;
        float t = Math.min(1f, ageSec / seconds);
        float hold = .12f;
        float inten = t < hold ? 1f : (float) Math.pow(1f - (t - hold) / (1f - hold), 2.0);
        float flash = (float) Math.exp(-t * 9.0);
        boolean reduced = ClientConfig.reducedMotion();
        boolean extra = ClientConfig.extraImpactEffects();

        f(pipeline, "ImpactIntensity", inten * strength);
        f(pipeline, "ImpactFlash", flash * strength);
        f(pipeline, "ImpactProgress", t);
        ShaderUniformAccess c = pipeline.getUniform("ImpactCenter");
        if (c != null) c.setVector(uvX, uvY);

        // Chromatic aberration: slider * (nearer impacts fringe more). Without Extensive Visuals the old fixed look is kept.
        f(pipeline, "ImpactChroma", ClientConfig.chromaScale() * (extra ? .7f + .6f * proximity : 1f));
        // Warp / speed-line / inversion strength (reduced motion tones all of it down).
        f(pipeline, "ImpactMotion", reduced ? .25f : 1f);
        // Hate only: radial blur + ink outlines + halftone + hard first frame (the shader ignores it if it does not declare it).
        f(pipeline, "ImpactInk", ClientConfig.inkImpact() ? 1f : 0f);

        // Short colour flash on top of everything.
        float flashAmount = ClientConfig.flashScale() * FLASH_PEAK * strength * proximity * (float) Math.exp(-ageSec * FLASH_DECAY);
        boolean red = switch (ClientConfig.flashStyle()) {
            case RED -> true;
            case WHITE -> false;
            default -> activeIsHate;
        };
        ShaderUniformAccess fc = pipeline.getUniform("FlashColor");
        if (fc != null) { if (red) fc.setVector(1f, .05f, .08f); else fc.setVector(1f, .97f, .9f); }
        f(pipeline, "FlashAmount", Math.max(0f, Math.min(.85f, flashAmount)));
    }

    private static void f(PostPipeline p, String n, float v) {
        ShaderUniformAccess u = p.getUniform(n);
        if (u != null) u.setFloat(v);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (active == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean done = mc.level == null || (System.nanoTime() - startNanos) / 1e9f > seconds;
        if (!done) return;
        try {
            VeilRenderSystem.renderer().getPostProcessingManager().remove(active);
        } catch (RuntimeException ignored) {}
        active = null;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (active != null) projectImpact(e.getCamera(), mc);
        if (shakeAmp <= 0f) return;
        float age = (System.nanoTime() - shakeStart) / 1e9f;
        if (age >= shakeSeconds) { shakeAmp = 0f; return; }
        float k = 1f - age / shakeSeconds, a = shakeAmp * k * k, t = age * 38f;
        e.setYaw(e.getYaw() + a * (float) (Math.sin(t * 1.13) + .6 * Math.sin(t * 2.71 + 1.3)));
        e.setPitch(e.getPitch() + a * (float) (Math.sin(t * 1.37 + 2.0) + .6 * Math.sin(t * 3.1)));
        e.setRoll(e.getRoll() + a * .6f * (float) Math.sin(t * .93 + .7));
    }

    /** Screen UV (0..1) of the impact point, so rays / shockwave originate where it happened. */
    private static void projectImpact(Camera cam, Minecraft mc) {
        Vec3 rel = worldPos.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double z = rel.x * look.x + rel.y * look.y + rel.z * look.z;
        if (z < .05) { uvX = .5f; uvY = .5f; return; }
        double x = -(rel.x * left.x + rel.y * left.y + rel.z * left.z), y = rel.x * up.x + rel.y * up.y + rel.z * up.z;
        double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        uvX = (float) Math.max(-.25, Math.min(1.25, .5 + x / (z * tan * aspect) * .5));
        uvY = (float) Math.max(-.25, Math.min(1.25, .5 + y / (z * tan) * .5));
    }
}