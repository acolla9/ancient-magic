// FILE: src/main/java/com/ancientstudios/ancientmagic/HateFx.java   (REPLACES the existing file)
// Changes: moving lights for Blood Lance / Wrath Meteor / Blood Moon, red sky for Blood Moon.
// The old static glows are kept as the fallback when ClientConfig.dynamicLights() is off.
package com.ancientstudios.ancientmagic;

import static com.ancientstudios.ancientmagic.AncientFx.*;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Particles, Veil glows, impact frames and camera shake for the ten Staff of Hate abilities. */
final class HateFx {
    private static final int R = HateVisuals.R, HOT = HateVisuals.HOT, CORE = HateVisuals.CORE, DARK = HateVisuals.DARK;
    private static final StaffType T = StaffType.HATE;
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);

    private HateFx() {}

    static void play(int ability, Vec3 from, Vec3 to, float scale) {
        switch (ability) {
            case 0 -> lance(from, to, scale);
            case 1 -> nova(to, scale);
            case 2 -> well(to, scale);
            case 3 -> pact(to, scale);
            case 4 -> chains(to);
            case 5 -> meteor(to);
            case 6 -> sweep(from, to);
            case 7 -> thorns(to);
            case 8 -> curse(to);
            default -> moon(to);
        }
    }

    private static float s(float scale) { return Math.min(3f, scale); }

    private static void lance(Vec3 from, Vec3 to, float scale) {
        Level l = lvl();
        HateVisuals.bloodLance(from, to, scale);
        SpellLights.lance(from, to, R); // moving light: flies with the spear, flares at the target
        Vec3 d = to.subtract(from), dn = d.normalize();
        Vec3 u = dn.cross(Math.abs(dn.y) > .95 ? X : Y).normalize(), w = dn.cross(u).normalize();
        glow(from, R, 5f, 2.4f, 10);
        int n = (int) Math.min(220, d.length() * 5);
        for (int i = 0; i < n; i++) {
            double t = i / (double) n, a = t * d.length() * 1.6;
            for (int k = 0; k < 2; k++) {
                double ph = a + k * Math.PI;
                puff(l, from.add(d.scale(t)).add(u.scale(Math.cos(ph) * .45)).add(w.scale(Math.sin(ph) * .45)), dn.scale(.05), .17f, 0f, rgb(k == 0 ? CORE : HOT), rgb(DARK), 16, .01);
            }
            if (i % 3 == 0) puff(l, from.add(d.scale(t)), new Vec3(0, -.04, 0), .12f, 0f, rgb(R), rgb(0x300005), 22, .06);
        }
        seq(24, tick -> {
            if (tick == 3) {
                float S = s(scale);
                sphereBurst(l, to, R, CORE, 110, .15 * S, .6 * S, .36f);
                ringBurstH(l, to, R, CORE, .5 * S, 56, .26f);
                ringBurst(l, to, Y, X, R, CORE, .4 * S, 40, .22f);
                for (int i = 0; i < 30; i++) puff(l, to, rnd().scale(.1 + Math.random() * .3).add(0, .2, 0), .2f, 0f, rgb(R), rgb(0x2A0004), 30, .05);
                glow(to, R, 10f, 3.4f, 24);
                impact(T, to, 1.0f, .5f, 1.6f);
            }
            if (tick > 3 && tick < 20) rain(l, to.add(0, 3, 0), 2.5, 2, 6, .12, R, 0x2A0004, .14f, 18);
        });
    }

    private static void nova(Vec3 c, float scale) {
        Level l = lvl();
        HateVisuals.crimsonNova(c, scale);
        float S = s(scale);
        glow(c, R, 8f, 1.8f, 8);
        seq(46, tick -> {
            if (tick < 7) {
                for (int i = 0; i < 26; i++) { Vec3 d = rnd(); puff(l, c.add(d.scale(3.6 * S)), d.scale(-.5 * S), .24f, .05f, rgb(R), rgb(CORE), 7, .02); }
            } else if (tick == 7) {
                sphereBurst(l, c, R, CORE, 260, .2 * S, 1.2 * S, .5f);
                sphereBurst(l, c, 0x2A0004, DARK, 120, .1 * S, .7 * S, .55f);
                ringBurstH(l, c, R, CORE, 1.1 * S, 110, .36f);
                ringBurst(l, c, X, Y, HOT, CORE, .8 * S, 60, .3f);
                ringBurst(l, c, Z, Y, HOT, CORE, .8 * S, 60, .3f);
                glow(c, R, 16f * S, 4.5f, 30);
                impact(T, c, 1.3f, .65f, 2.6f);
            } else if (tick < 36) {
                rain(l, c.add(0, 5, 0), 6 * S, 4, 14, .18, R, 0x2A0004, .2f, 22);
                for (int i = 0; i < 4; i++) puff(l, c.add(rnd().scale(3 * S)), new Vec3(0, .08, 0), .5f, 1.2f, rgb(0x5A0010), rgb(0x120002), 26, .1);
            }
        });
    }

    private static void well(Vec3 c, float scale) {
        Level l = lvl();
        HateVisuals.hemorrhageWell(c, scale);
        float S = s(scale);
        seq(80, tick -> {
            if (tick == 6) { sphereBurst(l, c, R, CORE, 120, .1, .5, .3f); impact(T, c, .7f, .45f, 1.0f); glow(c, R, 12f * S, 2.5f, 70); }
            if (tick > 6 && tick < 68) {
                for (int i = 0; i < 12; i++) {
                    double a = Math.random() * Math.PI * 2, r = (3 + Math.random() * 4) * S;
                    Vec3 p = c.add(Math.cos(a) * r, (Math.random() - .5) * 2, Math.sin(a) * r);
                    Vec3 tang = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.22);
                    puff(l, p, tang.add(c.subtract(p).normalize().scale(.22)), .26f, .05f, rgb(CORE), rgb(R), 12, .03);
                }
                if (tick % 2 == 0) puff(l, c.add(rnd().scale(.4)), new Vec3(0, .1, 0), .7f, 1.1f, rgb(0x5A0010), rgb(0x100002), 18, .05);
            }
            if (tick == 70) {
                sphereBurst(l, c, R, CORE, 200, .3 * S, 1.0 * S, .45f);
                ringBurstH(l, c, R, CORE, .9 * S, 90, .3f);
                glow(c, R, 14f * S, 4f, 20);
                impact(T, c, .95f, .5f, 1.9f);
            }
        });
    }

    private static void pact(Vec3 c, float scale) {
        Level l = lvl();
        HateVisuals.bloodPact(c, scale);
        float S = s(scale);
        glow(c, R, 6f * S, 2.2f, 28);
        seq(60, tick -> {
            if (tick == 2) { ringBurstH(l, c.add(0, -.8, 0), R, CORE, .55 * S, 80, .26f); sphereBurst(l, c, R, CORE, 70, .1, .4, .3f); impact(T, c, .75f, .4f, 1.4f); }
            if (tick < 40) {
                spiral(l, c.add(0, -.8, 0), 1.6, 3.2, 8, 2.2, CORE, R, .17f, 12, tick * .35);
                for (int i = 0; i < 5; i++) {
                    double a = tick * .09 + i * Math.PI * 2 / 5;
                    Vec3 b = c.add(Math.cos(a) * 3.6 * S, -.8, Math.sin(a) * 3.6 * S);
                    puff(l, b, new Vec3(0, .25, 0), .28f, 0f, rgb(R), rgb(0x2A0004), 14, .03);
                    puff(l, b.add(0, 2.4, 0), new Vec3(0, -.05, 0), .12f, 0f, rgb(HOT), rgb(0x2A0004), 18, .1);
                }
            }
            if (tick == 36) { sphereBurst(l, c, R, CORE, 120, .15, .6, .3f); glow(c, R, 6f, 2f, 14); }
        });
    }

    private static void chains(Vec3 c) {
        Level l = lvl();
        HateVisuals.rendingChains(c);
        glow(c.add(0, 1, 0), R, 8f, 2f, 20);
        seq(60, tick -> {
            if (tick < 12) {
                double g = tick / 12.0;
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI * 2 / 8 + .3;
                    for (int k = 0; k < 4; k++) {
                        double u = Math.random() * g, rr = 7 * (1 - u * .72), hh = 6.5 * Math.sin(u * 1.45);
                        puff(l, c.add(Math.cos(a) * rr, hh, Math.sin(a) * rr), new Vec3(0, .02, 0), .2f, 0f, rgb(CORE), rgb(R), 14, .04);
                    }
                }
            }
            if (tick == 10) { ringBurstH(l, c.add(0, .1, 0), R, CORE, .4, 90, .26f); sphereBurst(l, c.add(0, 1, 0), R, CORE, 70, .1, .5, .3f); impact(T, c, .85f, .45f, 1.7f); }
            if (tick > 10 && tick < 50 && tick % 2 == 0) drift(l, c.add(0, 1.5, 0), 3, 6, HOT, DARK, .16f, 20, .03);
        });
    }

    private static void meteor(Vec3 c) {
        Level l = lvl();
        HateVisuals.wrathMeteor(c);
        SpellLights.meteor(c, R); // moving light rides the falling rock
        Vec3 off = HateVisuals.METEOR_OFF;
        seq(70, tick -> {
            if (tick < 24) {
                double u = tick / 24.0;
                Vec3 p = c.add(off.scale(1 - u * u));
                for (int i = 0; i < 10; i++) puff(l, p.add(rnd().scale(.9)), rnd().scale(.1).add(off.normalize().scale(.25)), .7f, 0f, rgb(i % 3 == 0 ? CORE : HOT), rgb(0x2A0004), 20, .1);
                puff(l, p, Vec3.ZERO, 2.2f, 2.8f, rgb(R), rgb(DARK), 6, .2);
                if (tick % 4 == 0 && !ClientConfig.dynamicLights()) glow(p, R, 12f, 2.4f, 5); // classic fallback
                for (int i = 0; i < 6; i++) { double a = Math.random() * Math.PI * 2; puff(l, c.add(Math.cos(a) * 11, .1, Math.sin(a) * 11), new Vec3(-Math.cos(a), 0, -Math.sin(a)).scale(.45 * u), .22f, 0f, rgb(CORE), rgb(R), 16, .02); }
            } else if (tick == 24) {
                sphereBurst(l, c, R, CORE, 320, .3, 1.6, .6f);
                sphereBurst(l, c, 0x2A0004, DARK, 160, .2, 1.0, .7f);
                ringBurstH(l, c.add(0, .2, 0), R, CORE, 1.3, 140, .4f);
                ringBurst(l, c, X, Y, HOT, CORE, 1.0, 70, .34f);
                ringBurst(l, c, Z, Y, HOT, CORE, 1.0, 70, .34f);
                column(l, c, 22, 1.8, 180, CORE, R, .5, .45f, 26);
                glow(c, R, 26f, 6f, 40);
                impact(T, c, 1.5f, .8f, 3.4f);
            } else if (tick < 60) {
                rain(l, c.add(0, 10, 0), 11, 8, 18, .25, R, 0x2A0004, .22f, 24);
                for (int i = 0; i < 5; i++) puff(l, c.add(rnd().scale(5)), new Vec3(0, .1, 0), .8f, 1.6f, rgb(0x5A0010), rgb(0x100002), 30, .1);
            }
        });
    }

    private static void sweep(Vec3 from, Vec3 to) {
        Level l = lvl();
        HateVisuals.reapersSweep(from, to);
        Vec3 dd = to.subtract(from);
        double phi = Math.atan2(dd.z, dd.x);
        glow(from.add(dd.normalize().scale(3)), R, 8f, 2.2f, 10);
        seq(8, tick -> {
            if (tick < 5) {
                double a1 = phi - 1.3 + 2.6 * (tick / 4.0);
                for (int k = 0; k < 3; k++) {
                    double r = 3.2 + k * 1.5;
                    for (int j = 0; j < 14; j++) {
                        double a = phi - 1.3 + (a1 - (phi - 1.3)) * (j / 13.0);
                        Vec3 p = from.add(Math.cos(a) * r, -.4 + k * .35, Math.sin(a) * r);
                        Vec3 tang = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(.18);
                        puff(l, p, tang.add(Math.cos(a) * .08, .03, Math.sin(a) * .08), .26f, 0f, rgb(k == 1 ? CORE : HOT), rgb(DARK), 14, .03);
                    }
                }
            }
            if (tick == 2) {
                Vec3 mid = from.add(dd.normalize().scale(4.5));
                sphereBurst(l, mid, R, CORE, 70, .1, .55, .3f);
                for (int i = 0; i < 24; i++) puff(l, mid, new Vec3(Math.cos(phi) + (Math.random() - .5), .3, Math.sin(phi) + (Math.random() - .5)).scale(.3), .2f, 0f, rgb(R), rgb(0x2A0004), 22, .08);
                impact(T, mid, .8f, .35f, 1.8f);
            }
        });
    }

    private static void thorns(Vec3 c) {
        Level l = lvl();
        HateVisuals.thornEruption(c);
        double[] radii = {5, 8, 11};
        int[] counts = {10, 16, 22};
        seq(26, tick -> {
            for (int w = 0; w < 3; w++) {
                if (tick != w * 8) continue;
                for (int i = 0; i < counts[w]; i++) {
                    double ang = i * Math.PI * 2 / counts[w] + w * .3;
                    Vec3 b = c.add(Math.cos(ang) * radii[w], 0, Math.sin(ang) * radii[w]);
                    for (int h = 0; h < 6; h++) puff(l, b.add(0, h * .5, 0), new Vec3(0, .25, 0), .26f, 0f, rgb(h > 3 ? CORE : R), rgb(0x2A0004), 16, .05);
                    for (int k = 0; k < 4; k++) puff(l, b, rnd().scale(.2).add(0, .15, 0), .18f, 0f, rgb(R), rgb(0x2A0004), 20, .1);
                }
                ringBurstH(l, c.add(0, .1, 0), R, CORE, radii[w] * .09, 50 + w * 20, .24f);
                glow(c.add(0, 1, 0), R, 8f + w * 3, 2f, 14);
                impact(T, c, w == 2 ? 1.1f : .45f, w == 2 ? .55f : .3f, w == 2 ? 2.2f : .9f);
            }
        });
    }

    private static void curse(Vec3 c) {
        Level l = lvl();
        HateVisuals.curseOfRuin(c);
        glow(c.add(0, 1, 0), R, 9f, 1.6f, 120);
        seq(132, tick -> {
            if (tick == 4) { ringBurstH(l, c.add(0, .1, 0), R, CORE, .45, 100, .26f); impact(T, c, .5f, .4f, .9f); }
            if (tick < 120) {
                for (int k = 0; k < 3; k++) {
                    double r = 8 * (1 - k * .27), a = tick * .06 * (k % 2 == 0 ? 1 : -1) + k;
                    for (int j = 0; j < 2; j++) { double aa = a + j * Math.PI; puff(l, c.add(Math.cos(aa) * r, .15, Math.sin(aa) * r), new Vec3(0, .04, 0), .2f, 0f, rgb(k == 1 ? CORE : R), rgb(DARK), 12, .02); }
                }
                column(l, c, 3.5, 8, 3, 0x5A0010, 0x100002, .06, .7f, 24);
                if (tick % 3 == 0) puff(l, c.add(rnd().scale(4)).add(0, 1, 0), new Vec3(0, .05, 0), .22f, 0f, rgb(HOT), rgb(0x2A0004), 28, .05);
            }
            if (tick % 20 == 10) ringBurstH(l, c.add(0, .1, 0), R, CORE, .3, 60, .2f);
        });
    }

    private static void moon(Vec3 c) {
        Level l = lvl();
        HateVisuals.bloodMoon(c);
        SpellLights.bloodMoon(c, R); // big moving red light that follows the moon up
        SkyEffects.bloodMoon(c);     // red sky + fog for everyone nearby
        Vec3 moonP = c.add(0, 15, 0);
        seq(70, tick -> {
            if (tick == 0 && !ClientConfig.dynamicLights()) glow(moonP, R, 22f, 3f, 60); // classic fallback
            if (tick < 52) {
                for (int i = 0; i < 6; i++) puff(l, moonP.add(rnd().scale(4.4)), rnd().scale(.06), .4f, 0f, rgb(i % 2 == 0 ? CORE : HOT), rgb(DARK), 18, .1);
                if (tick % 2 == 0) line(l, moonP, c.add(Math.cos(tick) * 7, 0, Math.sin(tick) * 7), 10, R, 0x2A0004, .2f, 14, .08);
            }
            if (tick == 14) { ringBurstH(l, c.add(0, .2, 0), R, CORE, 1.1, 150, .36f); sphereBurst(l, c, R, CORE, 140, .2, 1.0, .45f); glow(c, R, 20f, 5f, 26); impact(T, c, 1.3f, .7f, 3.0f); }
            if (tick == 28) { ringBurstH(l, c.add(0, .2, 0), HOT, CORE, .9, 110, .3f); impact(T, c, .8f, .45f, 1.8f); }
            if (tick > 12 && tick < 56) rain(l, c.add(0, 10, 0), 12, 6, 12, .22, R, 0x2A0004, .2f, 22);
        });
    }
}