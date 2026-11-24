// FILE: src/main/java/com/ancientstudios/ancientmagic/AncientFx.java   (REPLACES the existing file)
// Changes: impact() now delegates distance scaling to ImpactFrames; play() starts the per-ability cast pose.
package com.ancientstudios.ancientmagic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import team.lodestar.lodestone.modules.rendering.particle.standard.builder.WorldParticleBuilder;
import team.lodestar.lodestone.modules.rendering.particle.standard.data.GenericParticleData;
import team.lodestar.lodestone.modules.rendering.particle.standard.data.color.ColorParticleData;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleTypes;

/**
 * Shared FX toolbox (Lodestone particles, Veil glow lights, tick scheduler) + the entry point that plays a spell.
 * Per-spell choreography lives in {@link HateFx} and {@link DeterminationFx}.
 * Only Lodestone builder calls already proven in the original code are used.
 */
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public final class AncientFx {
    static final double TAU = Math.PI * 2;
    static float impactScale = 1f;
    /** True when the spell currently being played was cast by the local player (impact frames at full strength). */
    static boolean localCast = false;
    private static Vec3 castOrigin = Vec3.ZERO;

    private AncientFx() {}

    // ---------- tiny tick scheduler: lets a spell play out over many ticks ----------
    private static final class Seq { int age; final int len; final IntConsumer fn; Seq(int len, IntConsumer fn) { this.len = len; this.fn = fn; } }
    private static final List<Seq> SEQS = new ArrayList<>();

    /** Runs fn(0) now, then fn(1..len-1) once per client tick. */
    static void seq(int ticks, IntConsumer perTick) { SEQS.add(new Seq(ticks, perTick)); perTick.accept(0); }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().level == null) { SEQS.clear(); clearGlows(); return; }
        SEQS.removeIf(s -> { s.fn.accept(++s.age); return s.age >= s.len; });
        tickGlows();
    }

    // ---------- particle primitives ----------
    static void puff(Level lvl, Vec3 p, Vec3 mo, float s0, float s1, Color c0, Color c1, int life, double jitter) {
        WorldParticleBuilder.create(LodestoneParticleTypes.WISP_PARTICLE.get())
                .setScaleData(GenericParticleData.create(s0 * impactScale, s1 * impactScale).build())
                .setTransparencyData(GenericParticleData.create(1f, 0f).build())
                .setColorData(ColorParticleData.create(c0, c1).build())
                .setLifetime(life)
                .setRandomOffset(jitter, jitter, jitter)
                .addMotion(mo.x, mo.y, mo.z)
                .enableNoClip()
                .spawn(lvl, p.x, p.y, p.z);
    }

    static Vec3 rnd() { return new Vec3(Math.random() - .5, Math.random() - .5, Math.random() - .5).normalize(); }
    static Color rgb(int c) { return new Color(c); }
    static Color dark(int c, float k) { Color o = new Color(c); return new Color((int) (o.getRed() * k), (int) (o.getGreen() * k), (int) (o.getBlue() * k)); }
    static Level lvl() { return Minecraft.getInstance().level; }

    static void sphereBurst(Level l, Vec3 c, int rgb, int core, int n, double v0, double v1, float size) {
        for (int i = 0; i < n; i++)
            puff(l, c, rnd().scale(v0 + Math.random() * (v1 - v0)), size, 0f, rgb(core), rgb(rgb), 16 + (int) (Math.random() * 20), .04);
    }

    static void ringBurst(Level l, Vec3 c, Vec3 u, Vec3 v, int rgb, int core, double speed, int n, float size) {
        for (int i = 0; i < n; i++) {
            double a = i * TAU / n;
            Vec3 d = u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            puff(l, c, d.scale(speed), size, .02f, rgb(core), rgb(rgb), 22, .01);
        }
    }

    static void ringBurstH(Level l, Vec3 c, int rgb, int core, double speed, int n, float size) {
        ringBurst(l, c, new Vec3(1, 0, 0), new Vec3(0, 0, 1), rgb, core, speed, n, size);
    }

    static void spiral(Level l, Vec3 base, double radius, double height, int n, double turns, int rgbA, int rgbB, float size, int life, double phase) {
        for (int i = 0; i < n; i++) {
            double t = i / (double) n, a = phase + t * turns * TAU;
            puff(l, base.add(Math.cos(a) * radius, height * t, Math.sin(a) * radius), new Vec3(0, .02, 0), size, 0f, rgb(rgbA), rgb(rgbB), life, .01);
        }
    }

    static void column(Level l, Vec3 base, double height, double radius, int n, int rgbA, int rgbB, double rise, float size, int life) {
        for (int i = 0; i < n; i++) {
            double a = Math.random() * TAU, r = Math.sqrt(Math.random()) * radius, y = Math.random() * height;
            puff(l, base.add(Math.cos(a) * r, y, Math.sin(a) * r), new Vec3(0, rise * (.5 + Math.random()), 0), size, 0f, rgb(rgbA), rgb(rgbB), life, .02);
        }
    }

    static void line(Level l, Vec3 a, Vec3 b, int n, int rgbA, int rgbB, float size, int life, double spread) {
        Vec3 d = b.subtract(a);
        for (int i = 0; i < n; i++) {
            double t = i / (double) Math.max(1, n - 1);
            puff(l, a.add(d.scale(t)), rnd().scale(spread), size, 0f, rgb(rgbA), rgb(rgbB), life, .01);
        }
    }

    /** Particles falling over a disc: blood rain / feather snow. */
    static void rain(Level l, Vec3 centre, double radius, double height, int n, double fall, int rgbA, int rgbB, float size, int life) {
        for (int i = 0; i < n; i++) {
            double a = Math.random() * TAU, r = Math.sqrt(Math.random()) * radius;
            puff(l, centre.add(Math.cos(a) * r, height * Math.random(), Math.sin(a) * r), new Vec3(0, -fall * (.6 + Math.random() * .8), 0), size, 0f, rgb(rgbA), rgb(rgbB), life, .01);
        }
    }

    /** Slowly swirling motes (feathers / embers) around a point. */
    static void drift(Level l, Vec3 centre, double radius, int n, int rgbA, int rgbB, float size, int life, double rise) {
        for (int i = 0; i < n; i++) {
            Vec3 o = rnd().scale(radius * Math.random());
            Vec3 tangent = new Vec3(-o.z, 0, o.x).normalize().scale(.04);
            puff(l, centre.add(o), tangent.add(0, rise * (.4 + Math.random()), 0), size, 0f, rgb(rgbA), rgb(rgbB), life, .02);
        }
    }

    // ---------- Veil point glows (dynamic lights that flare and fade) ----------
    private static final class Glow {
        LightRenderHandle<PointLightData> h; int age; final int life; final float radius, bright, r, g, b; final Vec3 pos;
        Glow(Vec3 pos, int rgb, float radius, float bright, int life) {
            this.pos = pos; this.radius = radius; this.bright = bright; this.life = Math.max(2, life);
            this.r = ((rgb >> 16) & 255) / 255f; this.g = ((rgb >> 8) & 255) / 255f; this.b = (rgb & 255) / 255f;
        }
    }
    private static final List<Glow> GLOWS = new ArrayList<>();

    static void glow(Vec3 pos, int rgb, float radius, float brightness, int life) {
        if (GLOWS.size() > 24) return;
        try {
            Glow gl = new Glow(pos, rgb, radius, brightness, life);
            PointLightData data = new PointLightData()
                    .setPosition(pos.x, pos.y, pos.z).setRadius(radius).setColor(gl.r, gl.g, gl.b).setBrightness(brightness);
            gl.h = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
            GLOWS.add(gl);
        } catch (IllegalStateException | NullPointerException ignored) {}
    }

    private static void tickGlows() {
        for (var it = GLOWS.iterator(); it.hasNext(); ) {
            Glow g = it.next();
            g.age++;
            try {
                if (g.age >= g.life || g.h == null || !g.h.isValid()) { if (g.h != null) g.h.free(); it.remove(); continue; }
                float k = 1f - g.age / (float) g.life;
                PointLightData d = g.h.getLightData();
                d.setBrightness(g.bright * k * k).setRadius(g.radius * (.6f + .4f * k));
                g.h.markDirty();
            } catch (IllegalStateException | NullPointerException ex) { it.remove(); }
        }
    }

    private static void clearGlows() {
        for (Glow g : GLOWS) { try { if (g.h != null) g.h.free(); } catch (RuntimeException ignored) {} }
        GLOWS.clear();
    }

    /**
     * Impact frame + shake. Distance scaling (shake, aberration, flash) now lives in {@link ImpactFrames#hit};
     * the local caster always feels it at (almost) full strength.
     */
    static void impact(StaffType type, Vec3 pos, float power, float seconds, float shakeDeg) {
        ImpactFrames.hit(type, pos, power, seconds, shakeDeg, localCast);
    }

    // ---------- entry point (called by the network handler) ----------
    public static void play(int fxId, Vec3 from, Vec3 to, float scale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        impactScale = Math.max(1f, Math.min(5f, scale));
        castOrigin = from;
        localCast = mc.player != null && mc.player.position().distanceTo(from) < 6.0;
        StaffType type = StaffType.fromFxId(fxId);
        int ability = StaffType.abilityOf(fxId);
        CastAnimations.play(type, ability, from); // each ability has its own cast pose, seen by everyone nearby
        if (type.isHate()) HateFx.play(ability, from, to, scale);
        else DeterminationFx.play(ability, from, to, scale);
    }

    // ---------- charge orbit (while the wheel is open) ----------
    public static void chargeOrbit(Player p, StaffType type, int ability, float seconds) {
        Level l = p.level();
        Vec3 look = p.getLookAngle();
        Vec3 tip = p.getEyePosition().add(look.scale(.9)).add(0, -.35, 0);
        int col = type.colors[Math.max(0, Math.min(StaffType.COUNT - 1, ability))];
        double t = seconds * 7;
        int accentB = type.isHate() ? 0x4A0008 : 0xFFE9A0;
        for (int i = 0; i < 3; i++) {
            double a = t + i * TAU / 3;
            Vec3 o = new Vec3(Math.cos(a) * .35, Math.sin(a * 1.3) * .12, Math.sin(a) * .35);
            puff(l, tip.add(o), look.scale(.01), .12f, 0f, rgb(col), rgb(accentB), 8, .005);
        }
        if (type.isHate()) {
            if (Math.random() < .5) puff(l, tip.add(rnd().scale(.15)), new Vec3(0, -.06, 0), .1f, 0f, rgb(0xFF2030), rgb(0x300005), 14, .01);
        } else {
            if (Math.random() < .5) puff(l, tip.add(rnd().scale(.2)), new Vec3(0, .05, 0), .1f, 0f, rgb(0xFFFFFF), rgb(0xFFE9A0), 14, .01);
            double ha = t * .8;
            puff(l, tip.add(Math.cos(ha) * .3, .45, Math.sin(ha) * .3), Vec3.ZERO, .09f, 0f, rgb(0xFFF6D0), rgb(0xFFFFFF), 6, .0);
        }
    }
}