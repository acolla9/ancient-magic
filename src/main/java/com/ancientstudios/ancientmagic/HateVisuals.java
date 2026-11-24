package com.ancientstudios.ancientmagic;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 3D compositions for the ten Staff of Hate abilities: crimson, thorned, bloody. */
public final class HateVisuals {
    public static final int R = 0xE0102A, HOT = 0xFF5A4E, CORE = 0xFFB0A4, DARK = 0x5A0010, VOID = 0x2A0008;
    /** Where the Wrath Meteor comes from, relative to the impact point. */
    public static final Vec3 METEOR_OFF = new Vec3(16, 52, -10);
    private static final float TAU = (float) (Math.PI * 2);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private HateVisuals() {}

    private static Vec3 dir(int i, int n, float tilt) {
        double a = i * Math.PI * 2 / n;
        return new Vec3(Math.cos(a) * Math.cos(tilt), Math.sin(tilt), Math.sin(a) * Math.cos(tilt)).normalize();
    }

    private static float fade(float age, float in, float total, float out) {
        return Math.min(FxMath.clamp01(age / in), FxMath.clamp01((total - age) / out));
    }

    // 0 ---------------------------------------------------------------- Blood Lance
    public static void bloodLance(Vec3 from, Vec3 to, float scale) {
        Vec3 d = to.subtract(from), dn = d.normalize();
        Vec3 up = Math.abs(dn.y) > .95 ? new Vec3(1, 0, 0) : UP;
        Vec3 su = dn.cross(up).normalize(), sw = dn.cross(su).normalize();
        float travel = .16f;
        Fx3D.add(from, 1.3f, scale, (vc, m, t, age) -> {
            float h = FxMath.easeOutCubic(Math.min(1f, age / travel)), fade = 1f - FxMath.clamp01((age - travel) / 1.0f);
            Vec3 head = d.scale(h);
            Fx3D.beam(vc, m, Vec3.ZERO, head, .2f * fade + .02f, fade, R, CORE);
            int seed = (int) (age * 24);
            for (int k = 0; k < 3; k++) Fx3D.bolt(vc, m, Vec3.ZERO, head, 16, .55f, seed * 7 + k, .05f, HOT, CORE, fade * .8f);
            int n = (int) Math.max(2, Math.min(16, d.length() / 3.0));
            for (int i = 0; i < n; i++) {
                float u = (i + .5f) / n, a = i * 1.9f + age * 9f;
                Vec3 c = head.scale(u).add(su.scale(Math.cos(a) * .55)).add(sw.scale(Math.sin(a) * .55));
                Fx3D.crystal(vc, m, c, dn, .55f, .07f, 4, age * 5, fade, R, CORE);
            }
            if (age < travel + .12f) {
                Fx3D.crystal(vc, m, head, dn, 1.6f, .24f, 6, age * 14, 1f, R, CORE);
                Matrix4f r = Fx3D.alignY(new Matrix4f(m).translate((float) head.x, (float) head.y, (float) head.z), dn);
                for (int k = 0; k < 3; k++) Fx3D.ring(vc, r, .45f + k * .3f, .08f, -k * .55f, age * (8 + k * 3) * (k % 2 == 0 ? 1 : -1), .9f, k == 1 ? CORE : R);
            }
        });
        Fx3D.add(to, 1.4f, scale, (vc, m, t, age) -> {
            float a = age - travel;
            if (a < 0) return;
            float u = a / (1.4f - travel), e = FxMath.easeOutExpo(u), fade = 1f - u;
            Fx3D.shell(vc, m, FxMath.lerp(.1f, 2.6f, e), fade, R, 12);
            Fx3D.shell(vc, m, FxMath.lerp(.05f, 1.2f, e), fade, CORE, 10);
            Fx3D.shell(vc, m, FxMath.lerp(.2f, 3.5f, e), fade * .5f, DARK, 10);
            Fx3D.ring(vc, m, 3.2f * e, .2f, 0f, 0f, fade, R);
            for (int i = 0; i < 14; i++) {
                Vec3 sd = dir(i, 14, .35f * ((i % 3) - 1));
                Fx3D.crystal(vc, m, sd.scale(.3f + 2.8f * e), sd, 1.2f * (1f - u * .5f), .1f, 4, i, fade, R, CORE);
            }
        });
    }

    // 1 ---------------------------------------------------------------- Crimson Nova
    public static void crimsonNova(Vec3 c, float scale) {
        Fx3D.add(c, 2.3f, scale, (vc, m, t, age) -> {
            float charge = .35f;
            if (age < charge) {
                float u = age / charge;
                Fx3D.shell(vc, m, FxMath.lerp(3.2f, .3f, FxMath.easeInOutCubic(u)), .3f + u * .6f, R, 10);
                Fx3D.shell(vc, m, .5f + u * .9f, u, CORE, 8);
                for (int i = 0; i < 10; i++) {
                    Vec3 sd = dir(i, 10, .5f);
                    Fx3D.crystal(vc, m, sd.scale(4.0f * (1f - u)), sd.scale(-1), 1.2f, .08f, 4, age * 4, u, R, CORE);
                }
                return;
            }
            float a = age - charge, u = a / (2.3f - charge), e = FxMath.easeOutExpo(Math.min(1f, a * 1.4f)), fade = (1f - u) * (1f - u);
            Fx3D.shell(vc, m, 7.0f * e, fade * .9f, R, 14);
            Fx3D.shell(vc, m, 4.6f * e, fade, HOT, 12);
            Fx3D.shell(vc, m, 2.2f * e, fade, CORE, 10);
            Fx3D.shell(vc, m, 9.0f * e, fade * .4f, DARK, 12);
            Fx3D.disc(vc, m, 8f * e, -.8f, fade * .5f, VOID);
            for (int k = 0; k < 3; k++) Fx3D.ring(vc, m, (5f + k * 3.2f) * e, .4f, -.7f + k * .05f, a * (k % 2 == 0 ? 1 : -1) * 2f, fade, k == 1 ? CORE : R);
            for (int i = 0; i < 22; i++) {
                Vec3 sd = dir(i, 22, (i % 3 - 1) * .35f);
                Fx3D.crystal(vc, m, sd.scale(2f + 6.5f * e), sd, 1.6f * (1f - u * .6f), .13f, 4, i, fade, R, CORE);
            }
            for (int i = 0; i < 5; i++) {
                float ga = i * TAU / 5 + 0.4f;
                Fx3D.pillar(vc, m, .35f, 9f * FxMath.easeOutCubic(Math.min(1f, a * 2f)), -.8f, fade, R, 8, a);
                Matrix4f g = new Matrix4f(m).translate((float) Math.cos(ga) * 3.4f, 0, (float) Math.sin(ga) * 3.4f);
                Fx3D.pillar(vc, g, .28f, 6f * FxMath.easeOutCubic(Math.min(1f, a * 2f)), -.8f, fade * .8f, HOT, 6, a);
            }
        });
    }

    // 2 ---------------------------------------------------------------- Hemorrhage Well
    public static void hemorrhageWell(Vec3 c, float scale) {
        float total = 4.0f;
        Fx3D.add(c, total, scale, (vc, m, t, age) -> {
            float grow = FxMath.easeOutBack(Math.min(1f, age / .5f)), f = fade(age, .3f, total, .8f);
            float pulse = 1f + .08f * (float) Math.sin(age * 9f);
            Fx3D.shell(vc, m, 1.1f * grow * pulse, f, VOID, 12);
            Fx3D.shell(vc, m, 1.5f * grow * pulse, f * .8f, R, 12);
            Fx3D.shell(vc, m, 2.3f * grow, f * .4f, DARK, 10);
            Fx3D.disc(vc, m, 7f * grow, -.6f, f * .5f, DARK);
            for (int k = 0; k < 4; k++) Fx3D.ring(vc, m, (6.5f - k * 1.4f) * grow, .26f, -.55f + k * .04f, age * (1.2f + k * .7f) * (k % 2 == 0 ? 1 : -1), f, k % 2 == 0 ? R : CORE);
            for (int i = 0; i < 18; i++) {
                float ph = (age * .8f + i / 18f) % 1f, a = i * 2.39996f + age * 2.2f * (1f + (1f - ph));
                float rr = 7f * (1f - ph) + .6f, hh = 1.3f * ph * (1f - ph) * 4f - .4f;
                Vec3 p = new Vec3(Math.cos(a) * rr, hh, Math.sin(a) * rr);
                Fx3D.crystal(vc, m, p, new Vec3(-Math.sin(a), -.2, Math.cos(a)), .6f, .07f, 4, age * 6, f * ph, R, CORE);
            }
            for (int i = 0; i < 8; i++) {
                float a = i * TAU / 8 + age * .9f;
                Vec3 far = new Vec3(Math.cos(a) * 6.5f, 1.6f + (float) Math.sin(a * 3 + age * 3), Math.sin(a) * 6.5f);
                Fx3D.bolt(vc, m, far, Vec3.ZERO, 12, .6f, (int) (age * 14) * 5 + i, .05f, HOT, CORE, f * .7f);
            }
        });
    }

    // 3 ---------------------------------------------------------------- Blood Pact
    public static void bloodPact(Vec3 c, float scale) {
        float total = 2.8f;
        Fx3D.add(c, total, scale, (vc, m, t, age) -> {
            float f = fade(age, .12f, total, .9f), e = FxMath.easeOutExpo(Math.min(1f, age / .5f));
            Fx3D.shell(vc, m, 1.7f * e, f * .6f, R, 12);
            Fx3D.shell(vc, m, 2.4f * e, f * .3f, DARK, 10);
            Fx3D.disc(vc, m, 4.2f * e, -.85f, f * .45f, DARK);
            // pentagram on the ground
            float pr = 3.6f * e, rot = age * .9f;
            for (int i = 0; i < 5; i++) {
                float a0 = rot + i * TAU / 5, a1 = rot + ((i + 2) % 5) * TAU / 5;
                Fx3D.seg(vc, m, new Vec3(Math.cos(a0) * pr, -.8, Math.sin(a0) * pr), new Vec3(Math.cos(a1) * pr, -.8, Math.sin(a1) * pr), .08f, i % 2 == 0 ? R : CORE, f);
            }
            Fx3D.solidRing(vc, m, pr, .12f, -.8f, f, R);
            Fx3D.ring(vc, m, pr + .5f, .18f, -.8f, -age * 1.4f, f, HOT);
            // blood fountains
            for (int i = 0; i < 5; i++) {
                float a = rot + i * TAU / 5;
                Matrix4f g = new Matrix4f(m).translate((float) Math.cos(a) * pr, -.8f, (float) Math.sin(a) * pr);
                Fx3D.pillar(vc, g, .16f, (2.5f + 1.2f * (float) Math.sin(age * 6 + i)) * e, 0f, f, R, 6, age * 3);
                Fx3D.crystal(vc, g, new Vec3(0, 2.4f * e, 0), UP, .7f, .1f, 4, age * 4, f, R, CORE);
            }
            // spiralling veins into the caster
            for (int k = 0; k < 3; k++) Fx3D.helix(vc, m, 1.4f * (1f - u(age, total) * .3f), 3.2f, 2.2f, -.8f, .05f, age * 2.5f + k * TAU / 3, f * .8f, k == 1 ? CORE : HOT, 36);
        });
    }

    private static float u(float age, float total) { return FxMath.clamp01(age / total); }

    // 4 ---------------------------------------------------------------- Rending Chains
    public static void rendingChains(Vec3 c) {
        float total = 3.4f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .1f, total, .8f), g = FxMath.easeOutCubic(Math.min(1f, age / .5f));
            Fx3D.disc(vc, m, 7f * g, .05f, f * .4f, DARK);
            Fx3D.solidRing(vc, m, 7f * g, .22f, .06f, f, R);
            Fx3D.ring(vc, m, 5f * g, .3f, .08f, age * 1.1f, f, CORE);
            int chains = 8;
            for (int i = 0; i < chains; i++) {
                float a = i * TAU / chains + .3f;
                Vec3 prev = null;
                int links = 22;
                for (int k = 0; k <= links; k++) {
                    float u = k / (float) links * g;
                    float rr = 7f * (1f - u * .72f) * (k == 0 ? 1f : 1f), hh = 6.5f * (float) Math.sin(u * 1.45f) * 1f;
                    float sway = (float) Math.sin(age * 3f + i + k * .4f) * .08f;
                    Vec3 p = new Vec3(Math.cos(a) * (rr + sway), hh, Math.sin(a) * (rr + sway));
                    if (prev != null) {
                        Vec3 d = p.subtract(prev);
                        Vec3 side = d.cross(UP).normalize().scale(.16);
                        if (k % 2 == 0) { Fx3D.seg(vc, m, prev.add(side), p.subtract(side), .09f, R, f); Fx3D.seg(vc, m, prev.subtract(side), p.add(side), .09f, CORE, f); }
                        else Fx3D.seg(vc, m, prev, p, .12f, HOT, f);
                    }
                    prev = p;
                }
                Fx3D.thorn(vc, m, new Vec3(Math.cos(a) * 7f * g, 0, Math.sin(a) * 7f * g), new Vec3(0, 1, 0), 2.2f * g, .2f, f, R, CORE);
            }
            Fx3D.cage(vc, m, 3.2f * g, 5, 8, .05f, f * .8f, R, true);
            Fx3D.shell(vc, m, 1.4f * g, f * .5f, R, 8);
        });
    }

    // 5 ---------------------------------------------------------------- Wrath Meteor
    public static void wrathMeteor(Vec3 c) {
        float fall = 1.2f, total = 3.4f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            if (age < fall) {
                float u = age / fall;
                Vec3 p = METEOR_OFF.scale(1f - u * u);
                Vec3 dn = METEOR_OFF.normalize();
                Matrix4f mm = new Matrix4f(m).translate((float) p.x, (float) p.y, (float) p.z);
                Fx3D.shell(vc, mm, 1.8f, 1f, R, 12);
                Fx3D.shell(vc, mm, 1.2f, 1f, CORE, 10);
                Fx3D.shell(vc, mm, 2.6f, .5f, DARK, 10);
                Fx3D.beam(vc, mm, Vec3.ZERO, dn.scale(22), .9f, .9f, R, HOT);
                for (int i = 0; i < 7; i++) Fx3D.bolt(vc, mm, Vec3.ZERO, dn.scale(10 + i * 2), 10, 1.4f, (int) (age * 20) * 3 + i, .09f, HOT, CORE, .8f);
                // ground telegraph
                Fx3D.solidRing(vc, m, 11f * (1f - u * .15f), .25f, .05f, .5f + .5f * u, R);
                Fx3D.ring(vc, m, 7f * (1f - u), .3f, .06f, age * 3, u, CORE);
                return;
            }
            float a = age - fall, u = a / (total - fall), e = FxMath.easeOutExpo(Math.min(1f, a * 1.5f)), f = (1f - u) * (1f - u);
            Fx3D.shell(vc, m, 11f * e, f * .8f, R, 16);
            Fx3D.shell(vc, m, 7f * e, f, HOT, 14);
            Fx3D.shell(vc, m, 3.5f * e, f, CORE, 12);
            Fx3D.shell(vc, m, 14f * e, f * .3f, DARK, 12);
            Fx3D.disc(vc, m, 13f * e, .05f, f * .5f, VOID);
            for (int k = 0; k < 4; k++) Fx3D.ring(vc, m, (6f + k * 3.2f) * e, .5f, .1f, a * (k % 2 == 0 ? 2f : -2f), f, k % 2 == 0 ? R : CORE);
            for (int i = 0; i < 30; i++) {
                Vec3 sd = dir(i, 30, .25f + (i % 4) * .22f);
                Fx3D.crystal(vc, m, sd.scale(2f + 10f * e), sd, 2.2f * (1f - u * .5f), .16f, 4, i, f, R, CORE);
            }
            float h = FxMath.easeOutCubic(Math.min(1f, a * 2.4f));
            Fx3D.pillar(vc, m, 1.8f, 26f * h, 0f, f * .9f, R, 12, a * 2);
            Fx3D.pillar(vc, m, 3.2f, 16f * h, 0f, f * .4f, DARK, 12, -a);
        });
    }

    // 6 ---------------------------------------------------------------- Reaper's Sweep
    public static void reapersSweep(Vec3 from, Vec3 to) {
        Vec3 dd = to.subtract(from);
        float phi = (float) Math.atan2(dd.z, dd.x);
        float total = .85f;
        Fx3D.add(from, total, 1f, (vc, m, t, age) -> {
            float sweep = FxMath.easeOutCubic(Math.min(1f, age / .2f)), f = (1f - FxMath.clamp01((age - .1f) / (total - .1f)));
            float a0 = phi - 1.3f, a1 = FxMath.lerp(a0, phi + 1.3f, sweep);
            float[] radii = {3.2f, 4.6f, 6.2f};
            for (int k = 0; k < 3; k++) {
                Matrix4f tilt = new Matrix4f(m).rotateY(-phi).rotateX(.35f * (k - 1)).rotateY(phi);
                Fx3D.crescent(vc, tilt, radii[k], .9f - k * .15f, a0, a1, -.4f + k * .35f, f * (1f - k * .15f), k == 1 ? HOT : R, CORE);
            }
            Vec3 tipEnd = new Vec3(Math.cos(a1) * 6.2f, -.1, Math.sin(a1) * 6.2f);
            for (int i = 0; i < 5; i++) Fx3D.bolt(vc, m, Vec3.ZERO.add(0, -.3, 0), tipEnd, 10, .5f, (int) (age * 30) + i * 11, .04f, HOT, CORE, f * .5f);
            Fx3D.crystal(vc, m, tipEnd, new Vec3(-Math.sin(a1), 0, Math.cos(a1)), 1.8f * f, .2f, 4, 0f, f, R, CORE);
        });
    }

    // 7 ---------------------------------------------------------------- Thorn Eruption
    public static void thornEruption(Vec3 c) {
        float total = 2.4f;
        float[] radii = {5f, 8f, 11f};
        int[] counts = {10, 16, 22};
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = 1f - FxMath.clamp01((age - 1.4f) / (total - 1.4f));
            Fx3D.disc(vc, m, 11f * FxMath.easeOutCubic(Math.min(1f, age / .8f)), .04f, .35f * f, DARK);
            for (int w = 0; w < 3; w++) {
                float t0 = w * .4f, a = age - t0;
                if (a < 0) { Fx3D.solidRing(vc, m, radii[w], .12f, .05f, .35f, R); continue; }
                float g = FxMath.easeOutBack(Math.min(1f, a / .22f)), tf = f * (1f - FxMath.clamp01(a / 1.6f) * .4f);
                Fx3D.solidRing(vc, m, radii[w], .2f, .06f, tf, HOT);
                for (int i = 0; i < counts[w]; i++) {
                    float ang = i * TAU / counts[w] + w * .3f, hgt = (2.8f + 1.8f * Fx3D.hash(w * 31, i)) * g;
                    Vec3 base = new Vec3(Math.cos(ang) * radii[w], 0, Math.sin(ang) * radii[w]);
                    Vec3 lean = new Vec3(Math.cos(ang) * .25, 1, Math.sin(ang) * .25);
                    Fx3D.thorn(vc, m, base, lean, hgt, .28f, tf, R, CORE);
                    Fx3D.thorn(vc, m, base.add(Math.cos(ang + 1.5) * .5, 0, Math.sin(ang + 1.5) * .5), lean.add(.3, 0, 0), hgt * .55f, .18f, tf * .8f, HOT, CORE);
                }
            }
        });
    }

    // 8 ---------------------------------------------------------------- Curse of Ruin
    public static void curseOfRuin(Vec3 c) {
        float total = 6.6f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .5f, total, 1.1f), g = FxMath.easeOutCubic(Math.min(1f, age / .6f)), R8 = 8f * g;
            Fx3D.disc(vc, m, R8, .05f, f * .35f, DARK);
            for (int k = 0; k < 3; k++) Fx3D.ring(vc, m, R8 * (1f - k * .27f), .3f, .08f + k * .01f, age * (.7f + k * .5f) * (k % 2 == 0 ? 1 : -1), f, k == 1 ? CORE : R);
            Fx3D.solidRing(vc, m, R8, .1f, .07f, f, HOT);
            // eight-point star sigil
            float rot = age * .5f;
            for (int i = 0; i < 8; i++) {
                float a0 = rot + i * TAU / 8, a1 = rot + ((i + 3) % 8) * TAU / 8;
                Fx3D.seg(vc, m, new Vec3(Math.cos(a0) * R8 * .8, .1, Math.sin(a0) * R8 * .8), new Vec3(Math.cos(a1) * R8 * .8, .1, Math.sin(a1) * R8 * .8), .07f, i % 2 == 0 ? R : HOT, f * .9f);
            }
            for (int i = 0; i < 8; i++) {
                float a = -rot * 1.3f + i * TAU / 8;
                Vec3 b = new Vec3(Math.cos(a) * R8, 0, Math.sin(a) * R8);
                Fx3D.crystal(vc, m, b.add(0, 1.2 + .4 * Math.sin(age * 3 + i), 0), UP, .9f, .12f, 4, age * 2, f, R, CORE);
                Fx3D.pillar(vc, new Matrix4f(m).translate((float) b.x, 0, (float) b.z), .22f, 4f + (float) Math.sin(age * 2 + i), 0f, f * .6f, DARK, 6, age);
            }
            Fx3D.pillar(vc, m, 1.3f, 8f * g, 0f, f * .45f, R, 10, age * .6f);
        });
    }

    // 9 ---------------------------------------------------------------- Blood Moon
    public static void bloodMoon(Vec3 c) {
        float total = 3.4f;
        Fx3D.add(c, total, 1f, (vc, m, t, age) -> {
            float f = fade(age, .35f, total, .9f), g = FxMath.easeOutCubic(Math.min(1f, age / .6f));
            Matrix4f moon = new Matrix4f(m).translate(0, 15f * g, 0);
            Fx3D.shell(vc, moon, 4.2f, f * .95f, R, 16);
            Fx3D.shell(vc, moon, 3.4f, f, HOT, 14);
            Fx3D.shell(vc, moon, 2.0f, f * .8f, CORE, 10);
            Fx3D.shell(vc, moon, 5.6f, f * .35f, DARK, 12);
            Matrix4f halo = new Matrix4f(moon).rotateX(.5f);
            Fx3D.solidRing(vc, halo, 6.4f, .25f, 0f, f, R);
            Fx3D.ring(vc, halo, 7.6f, .35f, 0f, age * 1.3f, f, CORE);
            for (int i = 0; i < 12; i++) {
                float a = i * TAU / 12 + age * .6f;
                Vec3 spot = new Vec3(Math.cos(a) * 8f, 0, Math.sin(a) * 8f);
                Fx3D.bolt(vc, m, new Vec3(0, 15f * g, 0), spot.add(0, .2, 0), 12, .8f, (int) (age * 12) * 13 + i, .06f, HOT, CORE, f * .55f);
            }
            Fx3D.pillar(vc, m, 1.1f, 15f * g, 0f, f * .5f, R, 10, age * 2);
            float p1 = age - .7f, p2 = age - 1.4f;
            if (p1 > 0) { float u = FxMath.clamp01(p1 / 1.2f); Fx3D.solidRing(vc, m, 16f * FxMath.easeOutExpo(u), .7f, .1f, (1f - u) * f, R); Fx3D.disc(vc, m, 16f * FxMath.easeOutExpo(u), .05f, (1f - u) * .35f, R); }
            if (p2 > 0) { float u = FxMath.clamp01(p2 / 1.0f); Fx3D.solidRing(vc, m, 13f * FxMath.easeOutExpo(u), .5f, .12f, (1f - u) * f, HOT); }
            Fx3D.disc(vc, m, 10f * g, .03f, f * .3f, VOID);
        });
    }
}
