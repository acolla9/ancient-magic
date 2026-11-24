// FILE: src/main/java/com/ancientstudios/ancientmagic/DeterminationFx.java   (REPLACES the existing file)
// Changes: moving lights for Halo Lance / Judgment / Starfall / Heaven's Gate, golden sky for Heaven's Gate.
// The old static glows are kept as the fallback when ClientConfig.dynamicLights() is off.
package com.ancientstudios.ancientmagic;

import static com.ancientstudios.ancientmagic.AncientFx.*;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Particles, Veil glows, impact frames and camera shake for the ten Staff of Determination abilities. */
final class DeterminationFx {
    private static final int W = DeterminationVisuals.W, GOLD = DeterminationVisuals.GOLD, SKY = DeterminationVisuals.SKY, PALE = DeterminationVisuals.PALE;
    private static final StaffType T = StaffType.DETERMINATION;
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);

    private DeterminationFx() {}

    static void play(int ability, Vec3 from, Vec3 to, float scale) {
        switch (ability) {
            case 0 -> lance(from, to, scale);
            case 1 -> judgment(to);
            case 2 -> wings(from, to);
            case 3 -> sanctuary(to);
            case 4 -> shield(to);
            case 5 -> burst(to);
            case 6 -> starfall(to);
            case 7 -> blades(from, to);
            case 8 -> resurgence(to);
            default -> gate(to);
        }
    }

    private static float s(float scale) { return Math.min(3f, scale); }

    private static void lance(Vec3 from, Vec3 to, float scale) {
        Level l = lvl();
        DeterminationVisuals.haloLance(from, to, scale);
        SpellLights.lance(from, to, PALE); // moving light: flies with the beam, flares at the target
        Vec3 d = to.subtract(from), dn = d.normalize();
        Vec3 u = dn.cross(Math.abs(dn.y) > .95 ? X : Y).normalize(), w = dn.cross(u).normalize();
        glow(from, PALE, 6f, 2.4f, 10);
        int n = (int) Math.min(220, d.length() * 5);
        for (int i = 0; i < n; i++) {
            double t = i / (double) n, a = t * d.length() * 1.5;
            for (int k = 0; k < 2; k++) {
                double ph = a + k * Math.PI;
                puff(l, from.add(d.scale(t)).add(u.scale(Math.cos(ph) * .4)).add(w.scale(Math.sin(ph) * .4)), dn.scale(.04), .16f, 0f, rgb(k == 0 ? W : GOLD), rgb(SKY), 16, .01);
            }
            if (i % 4 == 0) puff(l, from.add(d.scale(t)), new Vec3(0, -.03, 0).add(rnd().scale(.02)), .14f, 0f, rgb(W), rgb(GOLD), 30, .08);
        }
        seq(30, tick -> {
            if (tick == 3) {
                float S = s(scale);
                sphereBurst(l, to, W, GOLD, 110, .15 * S, .6 * S, .34f);
                ringBurstH(l, to, W, GOLD, .5 * S, 56, .26f);
                ringBurst(l, to, Y, X, GOLD, W, .4 * S, 40, .22f);
                glow(to, PALE, 12f, 3.8f, 26);
                impact(T, to, 1.0f, .5f, .9f);
            }
            if (tick > 3 && tick < 26) drift(l, to.add(0, 1.5, 0), 2.6, 4, W, GOLD, .16f, 28, -.02);
        });
    }

    private static void judgment(Vec3 c) {
        Level l = lvl();
        DeterminationVisuals.judgment(c);
        SpellLights.judgment(c, PALE); // ground glow, then lights stacked up the pillar
        glow(c.add(0, 3, 0), PALE, 10f, 1.6f, 14);
        seq(56, tick -> {
            if (tick < 14) {
                double u = tick / 14.0;
                for (int i = 0; i < 12; i++) { double a = Math.random() * Math.PI * 2, r = 6 * (1 - u * .9); puff(l, c.add(Math.cos(a) * r, .1, Math.sin(a) * r), new Vec3(0, .12, 0), .2f, 0f, rgb(W), rgb(GOLD), 10, .02); }
            } else if (tick == 14) {
                column(l, c, 40, 1.4, 200, W, GOLD, .5, .42f, 26);
                sphereBurst(l, c, W, GOLD, 160, .2, 1.0, .46f);
                ringBurstH(l, c.add(0, .2, 0), W, GOLD, 1.0, 120, .34f);
                glow(c, PALE, 22f, 6f, 36);
                impact(T, c, 1.35f, .65f, 2.4f);
            } else if (tick < 40) {
                column(l, c, 30, 1.2, 14, W, PALE, .4, .3f, 20);
                drift(l, c.add(0, 3, 0), 5, 4, W, GOLD, .2f, 30, .03);
            }
        });
    }

    private static void wings(Vec3 from, Vec3 to) {
        Level l = lvl();
        DeterminationVisuals.seraphWings(from, to);
        Vec3 d = to.subtract(from);
        glow(from, PALE, 8f, 2.4f, 16);
        seq(36, tick -> {
            if (tick == 1) { sphereBurst(l, from, W, GOLD, 80, .1, .5, .3f); impact(T, from, .6f, .35f, .8f); }
            if (tick < 12) {
                double u = tick / 12.0;
                line(l, from, from.add(d.scale(Math.min(1, u * 1.2))), 14, W, GOLD, .2f, 20, .12);
                for (int i = 0; i < 6; i++) puff(l, from.add(d.scale(Math.random() * Math.min(1, u * 1.2))).add(0, .3, 0), new Vec3(0, -.03, 0).add(rnd().scale(.03)), .22f, 0f, rgb(W), rgb(SKY), 30, .15);
            }
            if (tick == 10) { sphereBurst(l, to, W, GOLD, 50, .1, .4, .26f); ringBurstH(l, to, W, GOLD, .35, 40, .22f); glow(to, PALE, 7f, 2.2f, 12); impact(T, to, .5f, .3f, .6f); }
        });
    }

    private static void sanctuary(Vec3 c) {
        Level l = lvl();
        DeterminationVisuals.sanctuary(c);
        glow(c, PALE, 12f, 1.4f, 160);
        seq(170, tick -> {
            if (tick == 2) { ringBurstH(l, c.add(0, -.7, 0), W, GOLD, .5, 120, .28f); impact(T, c, .45f, .4f, .5f); }
            if (tick < 160) {
                column(l, c.add(0, -.8, 0), 6, 8.5, 3, W, GOLD, .05, .2f, 28);
                if (tick % 4 == 0) for (int i = 0; i < 12; i++) { double a = i * Math.PI * 2 / 12 + tick * .01; puff(l, c.add(Math.cos(a) * 9, -.7, Math.sin(a) * 9), new Vec3(0, .22, 0), .22f, 0f, rgb(W), rgb(GOLD), 26, .03); }
            }
            if (tick % 40 == 20) ringBurstH(l, c.add(0, -.7, 0), GOLD, W, .3, 80, .22f);
        });
    }

    private static void shield(Vec3 c) {
        Level l = lvl();
        DeterminationVisuals.divineShield(c);
        glow(c, PALE, 8f, 2.2f, 40);
        seq(64, tick -> {
            if (tick == 2) { ringBurstH(l, c.add(0, -.8, 0), W, GOLD, .5, 100, .28f); sphereBurst(l, c, W, GOLD, 90, .1, .5, .3f); impact(T, c, .7f, .4f, 1.1f); }
            if (tick < 60) {
                for (int i = 0; i < 3; i++) { double a = tick * .28 + i * Math.PI * 2 / 3; puff(l, c.add(Math.cos(a) * 2.2, Math.sin(a * 1.3) * .8, Math.sin(a) * 2.2), Vec3.ZERO, .16f, 0f, rgb(W), rgb(GOLD), 8, .01); }
                if (tick % 3 == 0) puff(l, c.add(rnd().scale(2)), new Vec3(0, .04, 0), .16f, 0f, rgb(W), rgb(SKY), 22, .02);
            }
        });
    }

    private static void burst(Vec3 c) {
        Level l = lvl();
        DeterminationVisuals.radiantBurst(c);
        seq(40, tick -> {
            if (tick == 0) glow(c, W, 28f, 7f, 32);
            if (tick == 2) {
                sphereBurst(l, c, W, GOLD, 320, .3, 1.6, .55f);
                ringBurstH(l, c, W, GOLD, 1.2, 140, .38f);
                ringBurst(l, c, X, Y, GOLD, W, 1.0, 90, .32f);
                ringBurst(l, c, Z, Y, GOLD, W, 1.0, 90, .32f);
                for (int i = 0; i < 60; i++) { Vec3 d = rnd(); line(l, c, c.add(d.scale(10)), 10, W, GOLD, .18f, 14, .0); }
                impact(T, c, 1.6f, .75f, 2.6f);
            }
            if (tick > 2 && tick < 30) drift(l, c, 8, 6, W, PALE, .2f, 26, .03);
        });
    }

    private static void starfall(Vec3 c) {
        Level l = lvl();
        DeterminationVisuals.starfall(c);
        SpellLights.starfall(c, PALE); // one moving light per star: falls with it, flares where it lands
        int N = 12;
        glow(c.add(0, 2, 0), PALE, 10f, 1.4f, 20);
        seq(StaffType.starStrikeTick(N - 1) + 14, tick -> {
            for (int i = 0; i < N; i++) {
                Vec3 sp = StaffType.starPos(c, i, N, 9.0);
                int st = StaffType.starStrikeTick(i);
                if (tick >= st - 10 && tick < st) {
                    double u = (tick - (st - 10)) / 10.0;
                    Vec3 p = sp.add(new Vec3(6, 28, -4).scale(1 - u));
                    for (int k = 0; k < 4; k++) puff(l, p.add(rnd().scale(.3)), rnd().scale(.04), .4f, 0f, rgb(k % 2 == 0 ? W : GOLD), rgb(SKY), 14, .05);
                }
                if (tick == st) {
                    sphereBurst(l, sp, W, GOLD, 40, .1, .5, .28f);
                    ringBurstH(l, sp.add(0, .1, 0), W, GOLD, .3, 30, .22f);
                    column(l, sp, 5, .4, 14, W, GOLD, .3, .26f, 14);
                    if (!ClientConfig.dynamicLights()) glow(sp.add(0, 1, 0), PALE, 6f, 2.4f, 8); // classic fallback
                    if (i == 4) impact(T, sp, .5f, .3f, .9f);
                    if (i == N - 1) impact(T, sp, .95f, .5f, 1.7f);
                }
            }
        });
    }

    private static void blades(Vec3 from, Vec3 to) {
        Level l = lvl();
        DeterminationVisuals.seraphBlades(from, to);
        Vec3 d = to.subtract(from);
        glow(from, PALE, 6f, 2f, 16);
        seq(34, tick -> {
            if (tick < 14) for (int i = 0; i < 6; i++) { double a = i * Math.PI * 2 / 6 + tick * .25; puff(l, from.add(Math.cos(a) * 1.5, .4, Math.sin(a) * 1.5), Vec3.ZERO, .18f, 0f, rgb(W), rgb(GOLD), 8, .02); }
            else if (tick < 20) for (int i = 0; i < 6; i++) { double a = i * Math.PI * 2 / 6; double u = (tick - 14) / 6.0; Vec3 p = from.add(Math.cos(a) * 1.5, .4, Math.sin(a) * 1.5).add(d.scale(u)); puff(l, p, d.normalize().scale(.1), .22f, 0f, rgb(W), rgb(GOLD), 10, .02); }
            if (tick == 20) {
                sphereBurst(l, to, W, GOLD, 120, .15, .8, .34f);
                ringBurstH(l, to, W, GOLD, .6, 70, .28f);
                glow(to, PALE, 10f, 3.4f, 22);
                impact(T, to, 1.0f, .5f, 1.5f);
            }
        });
    }

    private static void resurgence(Vec3 c) {
        Level l = lvl();
        DeterminationVisuals.resurgence(c);
        glow(c, PALE, 10f, 2.8f, 40);
        seq(52, tick -> {
            if (tick == 2) { sphereBurst(l, c, W, GOLD, 140, .15, .7, .34f); ringBurstH(l, c.add(0, -.8, 0), W, GOLD, .6, 110, .3f); impact(T, c, .9f, .5f, 1.3f); }
            if (tick < 40) {
                spiral(l, c.add(0, -.8, 0), 1.3, 8, 10, 3, W, GOLD, .18f, 14, tick * .4);
                spiral(l, c.add(0, -.8, 0), 1.3, 8, 10, 3, GOLD, W, .18f, 14, tick * .4 + Math.PI);
                column(l, c.add(0, -.8, 0), 12, 1.0, 8, W, PALE, .3, .26f, 22);
            }
        });
    }

    private static void gate(Vec3 c) {
        Level l = lvl();
        DeterminationVisuals.heavensGate(c);
        SpellLights.heavensGate(c, PALE); // light inside the gate, then lights up the beam
        SkyEffects.heavensGate(c);        // sky turns golden for a moment
        Vec3 gp = c.add(0, 13, 0);
        if (!ClientConfig.dynamicLights()) glow(gp, PALE, 18f, 2.4f, 24); // classic fallback
        seq(76, tick -> {
            if (tick < 20) {
                for (int i = 0; i < 14; i++) { double a = Math.random() * Math.PI * 2; puff(l, gp.add(Math.cos(a) * 5, Math.sin(a) * 5, 0), new Vec3(0, -.05, 0), .3f, 0f, rgb(W), rgb(GOLD), 16, .05); }
                drift(l, gp, 7, 4, W, PALE, .24f, 24, -.02);
            } else if (tick == 20) {
                column(l, c, 16, 2.6, 260, W, GOLD, .5, .5f, 28);
                sphereBurst(l, c, W, GOLD, 220, .25, 1.4, .5f);
                ringBurstH(l, c.add(0, .2, 0), W, GOLD, 1.3, 150, .4f);
                ringBurst(l, c, X, Y, GOLD, W, 1.0, 80, .32f);
                glow(c, PALE, 28f, 7f, 44);
                impact(T, c, 1.8f, .85f, 3.6f);
            } else if (tick < 56) {
                column(l, c, 14, 2.4, 22, W, PALE, .4, .34f, 22);
                drift(l, c.add(0, 4, 0), 8, 5, W, GOLD, .22f, 30, .04);
            }
        });
    }
}