package com.ancientstudios.ancientmagic;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The hero 3D compositions for each spell. Each is a pure function of age (seconds), so it is fully interpolated. */
public final class SpellVisuals {
    private SpellVisuals() {}

    private static final float TAU = (float) (Math.PI * 2);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** Deterministic pseudo-random unit vector for spike i. */
    private static Vec3 dir(int i, int n) {
        double y = 1 - 2 * (i + .5) / n, r = Math.sqrt(1 - y * y), a = i * 2.399963;
        return new Vec3(Math.cos(a) * r, y, Math.sin(a) * r);
    }

    private static float fadeInOut(float age, float in, float total, float out) {
        return FxMath.clamp01(age / in) * FxMath.clamp01((total - age) / out);
    }

    // ============================== COMMAND LANCE ==============================
    public static void lance(Vec3 from, Vec3 to, int rgb) { lance(from, to, rgb, 1f); }
    public static void lance(Vec3 from, Vec3 to, int rgb, float scale) {
        Vec3 d = to.subtract(from);
        float travel = .16f;
        Fx3D.add(from, 1.1f, scale, (vc, m, t, age) -> {
            float h = easeIn(Math.min(1f, age / travel));
            Vec3 head = d.scale(h);
            float fade = 1f - FxMath.clamp01((age - travel) / .8f);
            // long trail that thins out after impact
            Fx3D.beam(vc, m, Vec3.ZERO, head, .22f * fade + .02f, fade, rgb);
            if (age < travel + .08f) {
                Fx3D.crystal(vc, m, head, d, 1.5f, .22f, 6, age * 14, 1f, rgb);
                Matrix4f r = alignY(new Matrix4f(m).translate((float) head.x, (float) head.y, (float) head.z), d);
                for (int k = 0; k < 3; k++) Fx3D.ring(vc, r, .45f + k * .3f, .08f, -k * .55f, age * (8 + k * 3) * (k % 2 == 0 ? 1 : -1), .9f, k == 1 ? 0xFFFFFF : rgb);
            }
        });
        Fx3D.add(to, 1.3f, scale, (vc, m, t, age) -> {
            float a = age - travel;
            if (a < 0) return;
            float u = a / (1.3f - travel), e = FxMath.easeOutExpo(u), fade = 1f - u;
            Fx3D.shell(vc, m, FxMath.lerp(.1f, 2.4f, e), fade, rgb, 12);
            Fx3D.shell(vc, m, FxMath.lerp(.05f, 1.1f, e), fade, 0xFFFFFF, 10);
            for (int k = 0; k < 2; k++) {
                Matrix4f r = new Matrix4f(m).rotateX(k * 1.5708f);
                Fx3D.ring(vc, r, FxMath.lerp(.3f, 4.2f, e), .3f * fade + .05f, 0, k, fade, k == 0 ? 0xFFFFFF : rgb);
            }
            for (int i = 0; i < 12; i++) {
                Vec3 sd = dir(i, 12);
                Fx3D.crystal(vc, m, sd.scale(.3f + 2.6f * e), sd, 1.1f * (1f - u * .5f), .09f, 4, i, fade, rgb);
            }
        });
    }

    /** Rotates the matrix so its local +Y axis points along dir (plain axis-angle, no JOML rotateTo needed). */
    private static Matrix4f alignY(Matrix4f mat, Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 axis = UP.cross(d);
        double len = axis.length();
        if (len < 1e-6) { // parallel or anti-parallel to Y
            if (d.y < 0) mat.rotateX((float) Math.PI);
            return mat;
        }
        float angle = (float) Math.acos(Math.max(-1, Math.min(1, UP.dot(d))));
        return mat.rotate(angle, (float) (axis.x / len), (float) (axis.y / len), (float) (axis.z / len));
    }

    private static float easeIn(float t) { return t * t * t; }

    // ============================== ASTRAL NOVA ==============================
    public static void nova(Vec3 c, int rgb) { nova(c, rgb, 1f); }
    public static void nova(Vec3 c, int rgb, float scale) {
        float charge = .35f, total = 1.9f;
        Fx3D.add(c, total, scale, (vc, m, t, age) -> {
            if (age < charge) {
                float u = age / charge, e = FxMath.easeOutCubic(u);
                Fx3D.shell(vc, m, FxMath.lerp(3.5f, .5f, e), .35f + .5f * u, rgb, 14);
                Fx3D.shell(vc, m, .3f + .5f * e, u, 0xFFFFFF, 10);
                for (int i = 0; i < 6; i++) {
                    Vec3 sd = dir(i, 6);
                    Fx3D.crystal(vc, m, sd.scale(FxMath.lerp(3f, .9f, e)), sd.scale(-1), .8f, .06f, 4, age * 9, u, rgb);
                }
                return;
            }
            float a = age - charge, u = a / (total - charge), e = FxMath.easeOutExpo(u), fade = 1f - u;
            Fx3D.shell(vc, m, FxMath.lerp(.5f, 6.8f, e), fade * .9f, rgb, 20);
            Fx3D.shell(vc, m, FxMath.lerp(.4f, 4.2f, FxMath.easeOutCubic(u)), fade * .8f, 0xFFFFFF, 14);
            for (int i = 0; i < 18; i++) {
                Vec3 sd = dir(i, 18);
                float len = (i % 3 == 0 ? 3.2f : 1.9f) * (1f - u * .4f);
                Fx3D.crystal(vc, m, sd.scale(FxMath.lerp(.4f, 5.5f, e)), sd, len, .12f, 5, age * 6 + i, fade, i % 2 == 0 ? rgb : 0xFFFFFF);
            }
            for (int k = 0; k < 3; k++) {
                Matrix4f r = new Matrix4f(m).rotateX(k * 1.0472f).rotateZ(k * .6f + age * .5f);
                Fx3D.ring(vc, r, FxMath.lerp(.6f, 7.5f, e) * (1f - k * .08f), .4f * fade + .05f, 0, age * (k + 1), fade, k == 1 ? 0xFFFFFF : rgb);
            }
            Matrix4f g = new Matrix4f(m).translate(0, -aimDrop(c), 0);
            Fx3D.disc(vc, g, FxMath.lerp(.5f, 8f, e), .02f, fade * .5f, rgb);
        });
    }

    private static float aimDrop(Vec3 c) { return 0f; }

    // ============================== GRAVITY WELL ==============================
    public static void well(Vec3 c, int rgb) { well(c, rgb, 1f); }
    public static void well(Vec3 c, int rgb, float scale) {
        float total = 3.2f;
        Fx3D.add(c, total, scale, (vc, m, t, age) -> {
            float f = fadeInOut(age, .45f, total, .7f), spin = age * 3f;
            float pulse = Fx3D.flicker(age, 14);
            // singularity
            Fx3D.shell(vc, m, .55f * pulse * FxMath.easeOutBack(Math.min(1f, age / .45f)), f, 0xFFFFFF, 14);
            Fx3D.shell(vc, m, 1.0f * pulse, f * .8f, rgb, 14);
            Fx3D.shell(vc, m, 1.6f, f * .35f, 0x3A0A70, 12);
            // tilted accretion discs
            for (int k = 0; k < 3; k++) {
                Matrix4f r = new Matrix4f(m).rotateX(.5f + k * .55f).rotateY(k * 2.1f + age * (k % 2 == 0 ? 1.2f : -1.6f));
                Fx3D.ring(vc, r, 1.5f + k * .6f, .3f, 0, spin * (k + 1), f, k == 1 ? 0xFFFFFF : rgb);
                Fx3D.ring(vc, r, 2.0f + k * .6f, .08f, 0, -spin, f * .7f, rgb);
            }
            // vortex funnel: stacked rings narrowing upward and downward, each with its own phase
            for (int i = 0; i < 9; i++) {
                float u = i / 8f, r = FxMath.lerp(6f, .7f, FxMath.easeOutCubic(u));
                float y = (u - .5f) * 0 + (i % 2 == 0 ? 1 : -1) * u * 3.2f;
                Fx3D.ring(vc, m, r, .12f, y, spin * (1.5f - u) + i, f * (.25f + .5f * u), rgb);
            }
            // debris crystals spiralling in
            for (int i = 0; i < 18; i++) {
                float u = (age * .45f + i / 18f) % 1f, r = 7f * (1f - u) + .5f;
                float ang = i * 2.4f + age * (1.5f + (1f - u) * 7f), y = (float) Math.sin(i * 1.7) * 2f * (1f - u);
                Vec3 pos = new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r);
                Vec3 tangent = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
                Fx3D.crystal(vc, m, pos, tangent, .5f + .4f * u, .1f + .06f * u, 4, age * 5 + i, f * (.4f + .6f * u), i % 3 == 0 ? 0xFFFFFF : rgb);
            }
        });
    }

    // ============================== AETHER WARD ==============================
    public static void ward(Vec3 centre, int rgb) { ward(centre, rgb, 1f); }
    public static void ward(Vec3 centre, int rgb, float scale) {
        float total = 2.6f;
        Fx3D.add(centre, total, scale, (vc, m, t, age) -> {
            float f = fadeInOut(age, .2f, total, .8f), grow = FxMath.easeOutBack(Math.min(1f, age / .55f));
            float R = 1.9f * grow;
            // ground sigil (feet are 0.9 below the effect origin)
            Matrix4f gnd = new Matrix4f(m).translate(0, -.88f, 0);
            Fx3D.disc(vc, gnd, 2.4f * grow, 0, f * .35f, rgb);
            for (int k = 0; k < 3; k++) Fx3D.ring(vc, gnd, (1.0f + k * .6f) * grow, .1f + .05f * (k % 2), .01f * k, age * (k % 2 == 0 ? 2f : -2.7f), f * .9f, k == 1 ? 0xFFFFFF : rgb);
            for (int i = 0; i < 6; i++) { // hexagram rods
                float a0 = i * TAU / 6 + age, a1 = (i + 2) * TAU / 6 + age;
                Fx3D.seg(vc, gnd, new Vec3(Math.cos(a0) * 2.1 * grow, .02, Math.sin(a0) * 2.1 * grow), new Vec3(Math.cos(a1) * 2.1 * grow, .02, Math.sin(a1) * 2.1 * grow), .05f, rgb, f);
            }
            // dome cage + soft shell
            Matrix4f dome = new Matrix4f(m).rotateY(age * .6f);
            Fx3D.cage(vc, dome, R, 7, 12, .04f, f * (.6f + .2f * Fx3D.flicker(age, 9)), rgb, false);
            Fx3D.shell(vc, m, R * .98f, f * .22f, rgb, 14);
            // light pillars
            for (int i = 0; i < 8; i++) {
                float a = i * TAU / 8 - age * .8f, rr = 2.3f * grow, h = 3.4f * FxMath.easeOutCubic(Math.min(1f, (age - .1f * (i % 4)) / .6f));
                Vec3 base = new Vec3(Math.cos(a) * rr, -.88f, Math.sin(a) * rr);
                Fx3D.seg(vc, m, base, base.add(0, h, 0), .09f, rgb, f * .7f);
                Fx3D.seg(vc, m, base, base.add(0, h, 0), .03f, 0xFFFFFF, f);
            }
            // orbiting diamonds
            for (int i = 0; i < 6; i++) {
                float a = i * TAU / 6 + age * 2.2f;
                Vec3 pos = new Vec3(Math.cos(a) * 1.35, .2 + Math.sin(age * 3 + i) * .5, Math.sin(a) * 1.35);
                Fx3D.crystal(vc, m, pos, UP, .35f, .1f, 4, age * 4, f, 0xFFFFFF);
            }
        });
    }
}
