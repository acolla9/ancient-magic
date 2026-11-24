package com.ancientstudios.ancientmagic;

import net.minecraft.util.Mth;

/** Easing and interpolation helpers shared by every visual. */
public final class FxMath {
    private FxMath() {}

    public static float clamp01(float t) { return Mth.clamp(t, 0f, 1f); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static float easeOutCubic(float t) { t = clamp01(t); float u = 1 - t; return 1 - u * u * u; }
    public static float easeOutExpo(float t) { t = clamp01(t); return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t); }
    public static float easeInOutCubic(float t) { t = clamp01(t); return t < .5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }
    public static float easeOutBack(float t) { t = clamp01(t); float c1 = 1.70158f, c3 = c1 + 1; float u = t - 1; return 1 + c3 * u * u * u + c1 * u * u; }
    public static float easeOutElastic(float t) {
        t = clamp01(t);
        if (t == 0 || t == 1) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - .75) * (2 * Math.PI / 3)) + 1);
    }

    /** Frame-rate independent exponential smoothing: current -> target. */
    public static float damp(float current, float target, float speed, float dt) {
        return lerp(current, target, 1f - (float) Math.exp(-speed * dt));
    }

    /** Linear blend of two ARGB colours. */
    public static int mixArgb(int a, int b, float t) {
        t = clamp01(t);
        int aa = (int) lerp(a >>> 24, b >>> 24, t), ar = (int) lerp((a >> 16) & 255, (b >> 16) & 255, t);
        int ag = (int) lerp((a >> 8) & 255, (b >> 8) & 255, t), ab = (int) lerp(a & 255, b & 255, t);
        return aa << 24 | ar << 16 | ag << 8 | ab;
    }

    public static int withAlpha(int argb, float alpha) {
        return ((int) (clamp01(alpha) * 255) << 24) | (argb & 0xFFFFFF);
    }

    /** Shortest signed angle difference, radians. */
    public static float angleDelta(float from, float to) {
        float d = (to - from) % (float) (Math.PI * 2);
        if (d > Math.PI) d -= (float) (Math.PI * 2);
        if (d < -Math.PI) d += (float) (Math.PI * 2);
        return d;
    }
}