package com.ancientstudios.ancientmagic;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 3D compositions for the ten Staff of Determination abilities: white, golden, angelic. */
public final class DeterminationVisuals {
    public static final int W = 0xFFFFFF, GOLD = 0xFFE08A, SKY = 0xBFE0FF, PALE = 0xFFF6D8;
    private static final float TAU = (float) (Math.PI * 2);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DeterminationVisuals() {}

    private static Vec3 dir(int i, int n, float tilt) {
        double a = i * Math.PI * 2 / n;
        return new Vec3(Math.cos(a) * Math.cos(tilt), Math.sin(tilt), Math.sin(a) * Math.cos(tilt)).normalize();
    }

    private static float fade(float age, float in, float total, float out) {
        return Math.min(FxMath.clamp01(age / in), FxMath.clamp01((total - age) / out));
    }

    private static Matrix4f at(Matrix4f m, Vec3 p) { return new Matrix4f(m).translate((float) p.x, (float) p.y, (float) p.z); }

    // 0 ---------------------------------------------------------------- Halo Lance
    public static void haloLance(Vec3 from, Vec3 to, float scale) {
        Vec3 d = to.subtract(from), dn = d.normalize();
        float travel = .16f;
        Fx3D.add(from, 1.4f, scale, (vc, m, t, age) -> {
            float h = FxMath.easeOutCubic(Math.min(1f, age / travel)), fade = 1f - FxMath.clamp01((age - travel) / 1.1f);
            Vec3 head = d.scale(h);
            Fx3D.beam(vc, m, Vec3.ZERO, head, .18f * fade + .02f, fade, GOLD, W);
            Matrix4f al = Fx3D.alignY(new Matrix4f(m), dn);
            int n = (int) Math.max(3, Math.min(18, d.length() / 2.5));
            for (int i = 0; i < n; i++) {
                float u = (i + .5f) / n;
                float ph = (float) (u * d.length() * 0.5 - age * 6f);
                float dist = u * (float) d.length() * h;
                Matrix4f r = new Matrix4f(al).translate(0, dist, 0);
                float rad = .5f + .12f * (float) Math.sin(ph);
                Fx3D.solidRing(vc, r, rad, .07f, 0f, fade * .9f, i % 2 == 0 ? W : GOLD);
            }
            if (age < travel + .2f) {
                Matrix4f hm = at(m, head);
                float open = FxMath.easeOutBack(Math.min(1f, age / .12f));
                Matrix4f w = new Matrix4f(hm).rotateY((float) Math.atan2(-dn.x, -dn.z) + 1.5708f);
                Fx3D.wing(vc, w, 1, 1.9f * open, 8, 1f, W, GOLD, .2f);
                Fx3D.wing(vc, w, -1, 1.9f * open, 8, 1f, W, GOLD, .2f);
                Fx3D.crystal(vc, hm, Vec3.ZERO, dn, 1.5f, .22f, 6, age * 10, 1f, W, GOLD);
            }
        });
        Fx3D.add(to, 1.5f, scale, (vc, m, t, age) -> {
            float a = age - travel;
            if (a < 0) return;
            float u = a / (1.5f - travel), e = FxMath.easeOutExpo(u), fade = 1f - u;
            Fx3D.shell(vc, m, FxMath.lerp(.1f, 2.8f, e), fade, W, 12);
            Fx3D.shell(vc, m, FxMath.lerp(.1f, 1.4f, e), fade, GOLD, 10);
            Fx3D.shell(vc, m, 3.8f * e, fade * .35f, SKY, 10);
            Fx3D.solidRing(vc, m, 3.4f * e, .22f, 0f, fade, W);
            Matrix4f vert = new Matrix4f(m).rotateX(1.5708f);
            Fx3D.solidRing(vc, vert, 2.6f * e, .15f, 0f, fade, GOLD);
            Fx3D.rays(vc, m, 16, .4f, 1.5f + 4f * e, .08f, a, 0f, fade, W);
            Fx3D.rays(vc, vert, 8, .4f, 1.5f + 3f * e, .08f, -a, 0f, fade, GOLD);
        });
    }

    // 1 ---------------------------------------------------------------- Judgment
    public static void judgment(Vec3 c) {
        float strike = .7f, total = 2.7f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            if (age < strike) {
                float u = age / strike;
                Fx3D.solidRing(vc, m, 6f * (1f - u * .9f), .18f, .05f, .4f + u * .6f, W);
                Fx3D.ring(vc, m, 4f * (1f - u), .3f, .06f, age * 4f, u, GOLD);
                Fx3D.pillar(vc, m, .15f + u * .2f, 70f * u, 0f, u * .7f, W, 6, age);
                for (int i = 0; i < 4; i++) {
                    float a = i * TAU / 4 + age * 2f;
                    Fx3D.seg(vc, m, new Vec3(Math.cos(a) * 6f * (1f - u * .9f), 0, Math.sin(a) * 6f * (1f - u * .9f)), new Vec3(0, 0, 0).add(Math.cos(a) * 6f * (1f - u * .9f), 5f * u, Math.sin(a) * 6f * (1f - u * .9f)), .05f, GOLD, u);
                }
                return;
            }
            float a = age - strike, u = a / (total - strike), f = (1f - u) * (1f - u), e = FxMath.easeOutExpo(Math.min(1f, a * 2.5f));
            float pw = 1.0f + 1.3f * (1f - FxMath.clamp01(a / .5f));
            Fx3D.pillar(vc, m, pw * 1.7f, 90f, 0f, f * .6f, GOLD, 14, a);
            Fx3D.pillar(vc, m, pw, 90f, 0f, f, W, 14, -a * 2);
            Fx3D.pillar(vc, m, pw * .5f, 90f, 0f, f, W, 8, a * 3);
            Fx3D.shell(vc, m, 7f * e, f * .8f, W, 14);
            Fx3D.shell(vc, m, 4f * e, f, GOLD, 12);
            Fx3D.disc(vc, m, 9f * e, .05f, f * .6f, W);
            Fx3D.solidRing(vc, m, 9f * e, .5f, .1f, f, W);
            Fx3D.rays(vc, m, 20, 1f, 2f + 9f * e, .1f, a, .1f, f, GOLD);
            for (int k = 0; k < 6; k++) {
                float hh = ((1f - ((a * 1.8f + k / 6f) % 1f)) * 30f);
                Fx3D.solidRing(vc, m, pw * 1.9f, .12f, hh, f, k % 2 == 0 ? W : GOLD);
            }
            // cross of light hovering above the strike
            Matrix4f cm = new Matrix4f(m).translate(0, 14f * (float) Math.min(1, a * 3), 0).rotateY(a * .8f);
            Fx3D.seg(vc, cm, new Vec3(0, -2.4, 0), new Vec3(0, 3.2, 0), .35f, W, f);
            Fx3D.seg(vc, cm, new Vec3(-1.7, 1.4, 0), new Vec3(1.7, 1.4, 0), .35f, W, f);
        });
    }

    // 2 ---------------------------------------------------------------- Seraph Wings
    public static void seraphWings(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        Vec3 dh = new Vec3(d.x, 0, d.z);
        float yaw = dh.lengthSqr() < 1e-6 ? 0f : (float) Math.atan2(dh.x, dh.z);
        float total = 1.8f;
        Fx3D.add(from, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .05f, total, .9f), open = FxMath.easeOutBack(Math.min(1f, age / .22f));
            float flap = (float) Math.sin(age * 14f) * .3f * (1f - FxMath.clamp01(age / .9f));
            Matrix4f body = new Matrix4f(m).translate(0, -.5f, 0).rotateY(yaw).translate(0, 0, -.35f).rotateX(-.35f);
            Fx3D.wing(vc, body, 1, 4.2f * open, 12, f * .95f, W, GOLD, flap + .15f);
            Fx3D.wing(vc, body, -1, 4.2f * open, 12, f * .95f, W, GOLD, flap + .15f);
            Fx3D.shell(vc, m, 1.4f * open, f * .35f, PALE, 8);
            Fx3D.solidRing(vc, new Matrix4f(m).translate(0, .85f, 0), .45f, .08f, 0f, f, GOLD);
            // light ribbon along the flight path
            Vec3 pr = null;
            int steps = 40;
            for (int k = 0; k <= steps; k++) {
                float u = k / (float) steps;
                float vis = FxMath.clamp01((age * 3.4f) - u * 1.1f);
                Vec3 p = d.scale(u).add(0, Math.sin(u * 9 + age * 5) * .25, 0);
                if (pr != null) Fx3D.seg(vc, m, pr, p, .16f * (1f - u * .5f), k % 2 == 0 ? W : GOLD, f * vis * .8f);
                pr = p;
            }
            for (int i = 0; i < 6; i++) {
                float u = (i + .5f) / 6f, hv = FxMath.clamp01(age * 3.4f - u);
                Fx3D.feather(vc, m, d.scale(u).add(Math.sin(i * 2.3) * .7, -.3 - age * .4, Math.cos(i * 1.9) * .7), new Vec3(Math.sin(i), -.6, Math.cos(i)), .8f, .12f, f * hv, W, GOLD);
            }
        });
        Fx3D.add(to, .9f, 1f, (vc, m, t, age) -> {
            float u = age / .9f, e = FxMath.easeOutExpo(u), f = 1f - u;
            if (age < .2f) return;
            Fx3D.solidRing(vc, m, 2.8f * e, .2f, 0f, f, W);
            Fx3D.shell(vc, m, 1.4f * e, f * .6f, GOLD, 8);
        });
    }

    // 3 ---------------------------------------------------------------- Sanctuary
    public static void sanctuary(Vec3 c) {
        float total = 8.4f, R = 9f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .7f, total, 1.2f), g = FxMath.easeOutCubic(Math.min(1f, age / .7f));
            Fx3D.cage(vc, new Matrix4f(m).rotateY(age * .1f), R * g, 6, 14, .05f, f * .55f, W, true);
            Fx3D.shell(vc, m, R * g, f * .16f, SKY, 12);
            Fx3D.disc(vc, m, R * g, -.8f, f * .35f, PALE);
            for (int k = 0; k < 4; k++) Fx3D.solidRing(vc, m, R * g * (1f - k * .24f), .12f, -.78f, f * (.9f - k * .1f), k % 2 == 0 ? W : GOLD);
            Fx3D.ring(vc, m, R * g * .62f, .35f, -.76f, age * .8f, f, GOLD);
            for (int i = 0; i < 12; i++) {
                float a = i * TAU / 12 + age * .15f;
                Matrix4f p = new Matrix4f(m).translate((float) Math.cos(a) * R * g, -.8f, (float) Math.sin(a) * R * g);
                Fx3D.pillar(vc, p, .14f, 7.5f, 0f, f * .75f, W, 6, age);
                Fx3D.crystal(vc, p, new Vec3(0, 7.8f, 0), UP, .7f, .12f, 4, age * 2, f, W, GOLD);
            }
            for (int i = 0; i < 7; i++) {
                float a = i * TAU / 7 + age * .5f, ph = (age * .25f + i / 7f) % 1f;
                Fx3D.feather(vc, m, new Vec3(Math.cos(a) * R * .55f, 6f * (1f - ph), Math.sin(a) * R * .55f), new Vec3(Math.sin(a), -.5, Math.cos(a)), .9f, .13f, f * .8f, W, GOLD);
            }
            Fx3D.solidRing(vc, new Matrix4f(m).translate(0, 8.6f * g, 0), 2.6f, .22f, 0f, f, GOLD);
        });
    }

    // 4 ---------------------------------------------------------------- Divine Shield
    public static void divineShield(Vec3 c) {
        float total = 3.2f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .12f, total, .9f), e = FxMath.easeOutBack(Math.min(1f, age / .35f));
            Fx3D.shell(vc, m, 2.1f * e, f * .55f, W, 14);
            Fx3D.shell(vc, m, 2.5f * e, f * .3f, SKY, 12);
            for (int k = 0; k < 3; k++) {
                Matrix4f r = new Matrix4f(m).rotateX(k * 1.0472f + age * (1.2f + k * .4f)).rotateZ(age * (.8f - k * .3f));
                Fx3D.solidRing(vc, r, 2.45f * e, .08f, 0f, f, k == 1 ? GOLD : W);
            }
            Fx3D.solidRing(vc, new Matrix4f(m).translate(0, .95f, 0), .45f, .09f, 0f, f, GOLD);
            for (int i = 0; i < 8; i++) {
                float a = i * TAU / 8 + age * 2.2f, hh = .5f * (float) Math.sin(age * 3 + i * 2);
                Vec3 p = new Vec3(Math.cos(a) * 2.8f * e, hh, Math.sin(a) * 2.8f * e);
                Fx3D.feather(vc, m, p, new Vec3(-Math.sin(a), .1, Math.cos(a)), 1.2f, .16f, f, W, GOLD);
            }
            Matrix4f back = new Matrix4f(m).translate(0, .2f, 0).rotateY(0.0f);
            Fx3D.wing(vc, new Matrix4f(back).rotateX(-.2f), 1, 2.2f * e, 9, f * .6f, W, GOLD, .1f);
            Fx3D.wing(vc, new Matrix4f(back).rotateX(-.2f), -1, 2.2f * e, 9, f * .6f, W, GOLD, .1f);
            float p = FxMath.easeOutExpo(Math.min(1f, age / .5f));
            Fx3D.solidRing(vc, m, 6f * p, .3f, -.8f, (1f - p) * f, W);
        });
    }

    // 5 ---------------------------------------------------------------- Radiant Burst
    public static void radiantBurst(Vec3 c) {
        float total = 1.8f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float u = age / total, e = FxMath.easeOutExpo(Math.min(1f, age * 3f)), f = (1f - u) * (1f - u);
            Fx3D.shell(vc, m, 12f * e, f * .55f, W, 16);
            Fx3D.shell(vc, m, 7f * e, f, W, 14);
            Fx3D.shell(vc, m, 3.5f * e, f, GOLD, 12);
            Fx3D.shell(vc, m, 15f * e, f * .25f, SKY, 12);
            Matrix4f v1 = new Matrix4f(m).rotateX(1.5708f), v2 = new Matrix4f(m).rotateZ(1.5708f);
            for (Matrix4f pl : new Matrix4f[]{m, v1, v2}) {
                Fx3D.rays(vc, pl, 24, 1.2f, 2f + 15f * e, .12f, age * .8f, 0f, f, W);
                Fx3D.rays(vc, pl, 12, 1.2f, 1f + 10f * e, .22f, -age, 0f, f * .6f, GOLD);
                Fx3D.solidRing(vc, pl, 11f * e, .35f, 0f, f, W);
            }
            Fx3D.disc(vc, m, 12f * e, -.8f, f * .5f, W);
        });
    }

    // 6 ---------------------------------------------------------------- Starfall
    public static void starfall(Vec3 c) {
        int N = 12;
        float total = (StaffType.starStrikeTick(N - 1)) / 20f + 1.0f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .1f, total, .6f);
            Fx3D.solidRing(vc, m, 9f, .15f, .05f, f * .5f, GOLD);
            Fx3D.ring(vc, m, 6f, .25f, .06f, age * 1.5f, f * .6f, W);
            for (int i = 0; i < N; i++) {
                Vec3 sp = StaffType.starPos(Vec3.ZERO, i, N, 9.0);
                float strike = StaffType.starStrikeTick(i) / 20f, a = age - strike;
                Vec3 off = new Vec3(6, 28, -4);
                if (a < 0 && a > -.5f) {
                    float u = 1f + a / .5f;
                    Vec3 p = sp.add(off.scale(1f - u));
                    Matrix4f sm = at(m, p);
                    Fx3D.crystal(vc, sm, Vec3.ZERO, off.normalize().scale(-1), 1.0f, .3f, 4, age * 6, 1f, W, GOLD);
                    Fx3D.crystal(vc, sm, Vec3.ZERO, new Vec3(1, 0, 0), .8f, .25f, 4, 0f, .9f, GOLD, W);
                    Fx3D.beam(vc, sm, Vec3.ZERO, off.normalize().scale(10), .12f, .8f, GOLD, W);
                } else if (a >= 0 && a < .6f) {
                    float u = a / .6f, e = FxMath.easeOutExpo(u), ff = 1f - u;
                    Matrix4f sm = at(m, sp);
                    Fx3D.shell(vc, sm, 1.8f * e, ff, W, 8);
                    Fx3D.solidRing(vc, sm, 2.8f * e, .14f, .05f, ff, GOLD);
                    Fx3D.pillar(vc, sm, .25f, 5f * (1f - u), 0f, ff, W, 6, a);
                    Fx3D.rays(vc, sm, 8, .2f, 2.4f * e, .06f, a * 2, .06f, ff, W);
                }
            }
        });
    }

    // 7 ---------------------------------------------------------------- Seraph Blades
    public static void seraphBlades(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int N = 6;
        float launch = .7f, hit = 1.0f;
        Fx3D.add(from, 1.1f, 1f, (vc, m, t, age) -> {
            float f = 1f - FxMath.clamp01((age - launch) / .3f);
            for (int i = 0; i < N; i++) {
                float a = i * TAU / N + age * 5f * (1f - FxMath.clamp01(age / launch) * .4f);
                float r = 1.5f + .3f * (float) Math.sin(age * 8 + i), rise = .4f * FxMath.easeOutCubic(Math.min(1f, age / .3f));
                Vec3 pos = new Vec3(Math.cos(a) * r, rise + .25f * (float) Math.sin(a * 2), Math.sin(a) * r);
                if (age < launch) {
                    Vec3 pt = d.normalize().scale(.6).add(0, .3, 0).add(Math.cos(a) * .6, 0, Math.sin(a) * .6);
                    Fx3D.crystal(vc, m, pos, pt, 1.5f, .1f, 4, a, 1f, W, GOLD);
                    Fx3D.solidRing(vc, new Matrix4f(m).translate(0, 1.2f, 0), 1.9f, .07f, 0f, .6f, GOLD);
                } else {
                    float u = FxMath.easeInOutCubic(FxMath.clamp01((age - launch) / (hit - launch)));
                    Vec3 end = d.add(Math.cos(a) * .3, Math.sin(a) * .3, 0);
                    Vec3 p = pos.add(end.subtract(pos).scale(u));
                    Vec3 dr = end.subtract(pos).normalize();
                    Fx3D.crystal(vc, m, p, dr, 1.6f, .1f, 4, a, f, W, GOLD);
                    Fx3D.beam(vc, m, p, dr.scale(-5 * u), .06f, f * .8f, GOLD, W);
                }
            }
        });
        Fx3D.add(to, 1.4f, 1f, (vc, m, t, age) -> {
            float a = age - hit;
            if (a < 0) return;
            float u = a / .5f, e = FxMath.easeOutExpo(Math.min(1f, u)), f = 1f - FxMath.clamp01(u);
            Fx3D.shell(vc, m, 3f * e, f, W, 10);
            Fx3D.shell(vc, m, 1.5f * e, f, GOLD, 8);
            Fx3D.solidRing(vc, m, 4.5f * e, .25f, 0f, f, W);
            for (int i = 0; i < 6; i++) { Vec3 sd = dir(i, 6, .6f); Fx3D.crystal(vc, m, sd.scale(2f * e), sd, 1.8f * f, .12f, 4, i, f, W, GOLD); }
            Fx3D.rays(vc, m, 12, .3f, 1.5f + 5f * e, .08f, a * 3, 0f, f, GOLD);
        });
    }

    // 8 ---------------------------------------------------------------- Resurgence
    public static void resurgence(Vec3 c) {
        float total = 2.6f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .1f, total, .9f), e = FxMath.easeOutCubic(Math.min(1f, age / .6f));
            Fx3D.pillar(vc, m, 1.0f, 13f * e, -.85f, f * .6f, W, 12, age * 2);
            Fx3D.pillar(vc, m, 1.7f, 9f * e, -.85f, f * .3f, GOLD, 12, -age);
            for (int k = 0; k < 3; k++) Fx3D.helix(vc, m, 1.3f, 8f * e, 3f, -.85f, .09f, age * 3.2f + k * TAU / 3, f, k == 1 ? GOLD : W, 44);
            float up = (float) Math.min(1f, age / .45f), flap = (float) Math.sin(age * 8f) * .25f * (1f - FxMath.clamp01(age / 1.6f)) + .35f * (1f - up);
            Matrix4f body = new Matrix4f(m).translate(0, .3f, 0).rotateX(-.3f);
            Fx3D.wing(vc, body, 1, 4.8f * e, 13, f * .95f, W, GOLD, flap + .2f);
            Fx3D.wing(vc, body, -1, 4.8f * e, 13, f * .95f, W, GOLD, flap + .2f);
            Fx3D.shell(vc, m, 2.6f * FxMath.easeOutExpo(Math.min(1f, age * 3f)), f * (1f - FxMath.clamp01(age / .9f)), W, 12);
            for (int k = 0; k < 3; k++) {
                float p = FxMath.easeOutExpo(Math.min(1f, (age - k * .18f) / .6f));
                if (age - k * .18f > 0) Fx3D.solidRing(vc, m, 6f * p, .25f, -.8f, (1f - p) * f, k == 1 ? GOLD : W);
            }
            Fx3D.solidRing(vc, new Matrix4f(m).translate(0, 1.2f, 0), .45f, .09f, 0f, f, GOLD);
            for (int i = 0; i < 10; i++) {
                float a = i * TAU / 10 + age, ph = (age * .6f + i * .13f) % 1f;
                Fx3D.feather(vc, m, new Vec3(Math.cos(a) * 1.8f, -.8f + ph * 7f, Math.sin(a) * 1.8f), new Vec3(Math.sin(a), 1, Math.cos(a)), .9f, .13f, f * (1f - ph), W, GOLD);
            }
        });
    }

    // 9 ---------------------------------------------------------------- Heaven's Gate
    public static void heavensGate(Vec3 c) {
        float strike = 1.0f, total = 3.6f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float open = FxMath.easeOutBack(Math.min(1f, age / .8f));
            Matrix4f gate = new Matrix4f(m).translate(0, 13f, 0);
            float gf = age < strike ? 1f : 1f - FxMath.clamp01((age - strike - .6f) / (total - strike - .6f));
            Matrix4f vr = new Matrix4f(gate).rotateX(1.5708f);
            Fx3D.solidRing(vc, vr, 5f * open, .35f, 0f, gf, W);
            Fx3D.solidRing(vc, vr, 4.2f * open, .12f, 0f, gf, GOLD);
            Fx3D.ring(vc, vr, 5.8f * open, .3f, 0f, age * .9f, gf, GOLD);
            Fx3D.rays(vc, vr, 24, 5.2f * open, 7.5f * open, .1f, age * .4f, 0f, gf * .8f, W);
            Fx3D.disc(vc, vr, 4.8f * open, 0f, gf * .35f, PALE);
            Fx3D.wing(vc, new Matrix4f(gate).translate(5.4f * open, 0, 0), 1, 6f * open, 13, gf * .9f, W, GOLD, .15f);
            Fx3D.wing(vc, new Matrix4f(gate).translate(-5.4f * open, 0, 0), -1, 6f * open, 13, gf * .9f, W, GOLD, .15f);
            Fx3D.solidRing(vc, m, 8f * open, .2f, .05f, gf * .6f, GOLD);
            if (age < strike) {
                float u = age / strike;
                Fx3D.pillar(vc, m, .3f + u * .3f, 13f * u, 0f, u * .6f, W, 8, age * 3);
                return;
            }
            float a = age - strike, u = a / (total - strike), f = (1f - u) * (1f - u), e = FxMath.easeOutExpo(Math.min(1f, a * 2.2f));
            float pw = 2.6f + 1.4f * (1f - FxMath.clamp01(a / .5f));
            Fx3D.pillar(vc, m, pw * 1.6f, 14f, 0f, f * .55f, GOLD, 16, a);
            Fx3D.pillar(vc, m, pw, 14f, 0f, f, W, 16, -a * 2);
            Fx3D.pillar(vc, m, pw * .5f, 14f, 0f, f, W, 10, a * 3);
            Fx3D.shell(vc, m, 11f * e, f * .7f, W, 16);
            Fx3D.shell(vc, m, 6f * e, f, GOLD, 12);
            Fx3D.disc(vc, m, 14f * e, .05f, f * .6f, W);
            for (int k = 0; k < 3; k++) Fx3D.solidRing(vc, m, (8f + k * 4f) * e, .5f, .1f, f, k % 2 == 0 ? W : GOLD);
            Fx3D.rays(vc, m, 28, 1f, 3f + 14f * e, .12f, a, .1f, f, GOLD);
            for (int i = 0; i < 16; i++) { Vec3 sd = dir(i, 16, .3f + (i % 3) * .2f); Fx3D.crystal(vc, m, sd.scale(2f + 9f * e), sd, 2f * (1f - u * .5f), .15f, 4, i, f, W, GOLD); }
        });
    }
}
