package com.ancientstudios.ancientmagic;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Procedural 3D spell geometry (rune rings, shock shells, beam cores, wings, thorns...) drawn additively after particles.
 * Everything is a pure function of age, so it interpolates smoothly at any frame rate.
 *
 * Plain vanilla rendering. The {@link Shape} interface is the seam where glTF/PulseLib models can be plugged in later.
 */
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public final class Fx3D {
    /** One timed 3D effect. t is normalised age 0..1. */
    public interface Shape { void draw(VertexConsumer vc, Matrix4f m, float t, float ageSeconds); }

    private record Active(Vec3 pos, long startNanos, float seconds, float scale, Shape shape) {}

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static final float TAU = (float) (Math.PI * 2);
    private static final Vec3 UPV = new Vec3(0, 1, 0);

    private Fx3D() {}

    public static void add(Vec3 pos, float seconds, Shape shape) {
        add(pos, seconds, 1.0F, shape);
    }

    public static void add(Vec3 pos, float seconds, float scale, Shape shape) {
        ACTIVE.add(new Active(pos, System.nanoTime(), seconds, Math.max(.1F, scale), shape));
    }

    // ---------- primitives (all additive, double-sided, camera-independent) ----------

    public static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int rgb, float a) {
        vc.addVertex(m, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (FxMath.clamp01(a) * 255));
    }

    /** Double-sided quad (a triangle if d == c). */
    public static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float al) {
        Vec3[] q = {a, b, c, d};
        for (int side = 0; side < 2; side++)
            for (int i = 0; i < 4; i++) { Vec3 p = q[side == 0 ? i : 3 - i]; v(vc, m, (float) p.x, (float) p.y, (float) p.z, rgb, al); }
    }

    /** Thin glowing rod between two points: two crossed quads so it is visible from every angle. */
    public static void seg(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float thick, int rgb, float al) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-8) return;
        Vec3 dir = d.normalize(), up = Math.abs(dir.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize().scale(thick / 2), w = dir.cross(u).normalize().scale(thick / 2);
        quad(vc, m, a.add(u), a.subtract(u), b.subtract(u), b.add(u), rgb, al);
        quad(vc, m, a.add(w), a.subtract(w), b.subtract(w), b.add(w), rgb, al);
    }

    /** Flat annulus in the local XZ plane with optional runic gaps. */
    public static void ring(VertexConsumer vc, Matrix4f m, float radius, float width, float y, float rot, float alpha, int rgb) {
        int seg = 64;
        for (int i = 0; i < seg; i++) {
            if ((i / 4) % 4 == 3) continue;
            float a0 = rot + i * TAU / seg, a1 = rot + (i + 1) * TAU / seg;
            float ro = radius + width / 2, ri = radius - width / 2;
            quad(vc, m, p(a0, ri, y), p(a0, ro, y), p(a1, ro, y), p(a1, ri, y), rgb, alpha);
        }
    }

    /** Unbroken annulus (a halo). */
    public static void solidRing(VertexConsumer vc, Matrix4f m, float radius, float width, float y, float alpha, int rgb) {
        int seg = 64;
        float ro = radius + width / 2, ri = radius - width / 2;
        for (int i = 0; i < seg; i++) {
            float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            quad(vc, m, p(a0, ri, y), p(a0, ro, y), p(a1, ro, y), p(a1, ri, y), rgb, alpha);
        }
    }

    private static Vec3 p(float ang, float r, float y) { return new Vec3(Math.cos(ang) * r, y, Math.sin(ang) * r); }

    /** Solid filled disc, brighter at the centre feel via alpha falloff rings. */
    public static void disc(VertexConsumer vc, Matrix4f m, float r, float y, float alpha, int rgb) {
        int seg = 40;
        for (int k = 0; k < 4; k++) {
            float r0 = r * k / 4f, r1 = r * (k + 1) / 4f, a = alpha * (1f - k * .22f);
            for (int i = 0; i < seg; i++) {
                float a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
                quad(vc, m, p(a0, r0, y), p(a0, r1, y), p(a1, r1, y), p(a1, r0, y), rgb, a);
            }
        }
    }

    /** Lat/long filled shell. */
    public static void shell(VertexConsumer vc, Matrix4f m, float r, float alpha, int rgb, int seg) {
        if (alpha <= 0.01f) return;
        for (int i = 0; i < seg; i++) {
            float p0 = (float) Math.PI * i / seg - (float) Math.PI / 2, p1 = (float) Math.PI * (i + 1) / seg - (float) Math.PI / 2;
            for (int j = 0; j < seg * 2; j++) {
                float t0 = (float) Math.PI * j / seg, t1 = (float) Math.PI * (j + 1) / seg;
                float a = alpha * (.25f + .75f * (float) Math.abs(Math.sin(p0 * 2 + t0 * 3))) * .5f;
                quad(vc, m, sp(r, p0, t0), sp(r, p1, t0), sp(r, p1, t1), sp(r, p0, t1), rgb, a);
            }
        }
    }

    public static Vec3 sp(float r, float phi, float theta) {
        double c = Math.cos(phi);
        return new Vec3(r * c * Math.cos(theta), r * Math.sin(phi), r * c * Math.sin(theta));
    }

    /** Wireframe energy cage (latitude + longitude rods). */
    public static void cage(VertexConsumer vc, Matrix4f m, float r, int lat, int lon, float thick, float alpha, int rgb, boolean domeOnly) {
        int steps = 28;
        for (int i = 1; i < lat; i++) {
            float phi = (float) Math.PI * i / lat - (float) Math.PI / 2;
            if (domeOnly && phi < 0) continue;
            for (int s = 0; s < steps; s++)
                seg(vc, m, sp(r, phi, s * TAU / steps), sp(r, phi, (s + 1) * TAU / steps), thick, rgb, alpha);
        }
        for (int j = 0; j < lon; j++) {
            float th = j * TAU / lon;
            for (int s = 0; s < steps; s++) {
                float p0 = (float) Math.PI * s / steps - (float) Math.PI / 2, p1 = (float) Math.PI * (s + 1) / steps - (float) Math.PI / 2;
                if (domeOnly && p0 < 0) continue;
                seg(vc, m, sp(r, p0, th), sp(r, p1, th), thick, rgb, alpha);
            }
        }
    }

    /** Elongated bipyramid ("crystal / spear head") along dir, tip at +dir*len. Underside tinted white. */
    public static void crystal(VertexConsumer vc, Matrix4f m, Vec3 centre, Vec3 dir, float len, float radius, int sides, float roll, float alpha, int rgb) {
        crystal(vc, m, centre, dir, len, radius, sides, roll, alpha, rgb, 0xFFFFFF);
    }

    /** Same as above but the underside / core uses {@code core} (e.g. pale blood-red for the Hate staff). */
    public static void crystal(VertexConsumer vc, Matrix4f m, Vec3 centre, Vec3 dir, float len, float radius, int sides, float roll, float alpha, int rgb, int core) {
        Vec3 d = dir.normalize(), up = Math.abs(d.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(up).normalize(), w = d.cross(u).normalize();
        Vec3 tip = centre.add(d.scale(len)), tail = centre.subtract(d.scale(len * .35));
        for (int k = 0; k < sides; k++) {
            double a0 = roll + k * Math.PI * 2 / sides, a1 = roll + (k + 1) * Math.PI * 2 / sides;
            Vec3 r0 = centre.add(u.scale(Math.cos(a0) * radius)).add(w.scale(Math.sin(a0) * radius));
            Vec3 r1 = centre.add(u.scale(Math.cos(a1) * radius)).add(w.scale(Math.sin(a1) * radius));
            quad(vc, m, tip, r0, r1, r1, rgb, alpha);
            quad(vc, m, tail, r1, r0, r0, core, alpha * .6f);
        }
    }

    /** A thorn / spike whose BASE is at {@code base} and whose tip is at base + dir * len. */
    public static void thorn(VertexConsumer vc, Matrix4f m, Vec3 base, Vec3 dir, float len, float radius, float alpha, int rgb, int core) {
        Vec3 d = dir.normalize();
        crystal(vc, m, base.add(d.scale(len * .3)), d, len * .7f, radius, 4, 0f, alpha, rgb, core);
    }

    /** Four-sided glowing beam along d, white core + coloured halo. */
    public static void beam(VertexConsumer vc, Matrix4f m, Vec3 start, Vec3 d, float radius, float alpha, int rgb) {
        beam(vc, m, start, d, radius, alpha, rgb, 0xFFFFFF);
    }

    public static void beam(VertexConsumer vc, Matrix4f m, Vec3 start, Vec3 d, float radius, float alpha, int rgb, int core) {
        seg(vc, m, start, start.add(d), radius * 2f, rgb, alpha * .45f);
        seg(vc, m, start, start.add(d), radius * .8f, core, alpha);
    }

    public static float flicker(float age, float speed) { return .75f + .25f * (float) Math.sin(age * speed) * (float) Math.sin(age * speed * 1.7f + 1.3f); }

    /** Cheap deterministic hash -> 0..1. */
    public static float hash(int seed, int i) {
        int h = seed * 374761393 + i * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    /** Jagged lightning / blood-vein polyline between two points (change {@code seed} over time to make it crackle). */
    public static void bolt(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, int segs, float jitter, int seed, float thick, int rgb, int core, float al) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-8) return;
        Vec3 dir = d.normalize(), up = Math.abs(dir.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(up).normalize(), w = dir.cross(u).normalize();
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs;
            Vec3 q = a.add(d.scale(t));
            if (i < segs) {
                double env = Math.sin(t * Math.PI) * jitter;
                q = q.add(u.scale((hash(seed, i * 2) - .5) * 2 * env)).add(w.scale((hash(seed, i * 2 + 1) - .5) * 2 * env));
            }
            seg(vc, m, prev, q, thick, rgb, al);
            seg(vc, m, prev, q, thick * .35f, core, al);
            prev = q;
        }
    }

    /**
     * Feathered wing fan in the local XY plane (root at origin, spreading toward +X * side, +Y is up).
     * flap is added to every feather angle in radians (positive = raised).
     */
    public static void wing(VertexConsumer vc, Matrix4f m, float side, float span, int feathers, float alpha, int rgb, int core, float flap) {
        for (int i = 0; i < feathers; i++) {
            float u = feathers <= 1 ? 0f : i / (float) (feathers - 1); // 0 = leading edge (top), 1 = trailing edge (bottom)
            float ang = FxMath.lerp(1.2f, -0.95f, u) + flap * (1f - u * .5f);
            float len = span * (0.5f + 0.5f * (float) Math.sin((u * .8f + .12f) * Math.PI));
            float wdt = span * .075f;
            Vec3 root = new Vec3(side * .06f, 0, 0);
            Vec3 dir = new Vec3(Math.cos(ang) * side, Math.sin(ang), 0);
            Vec3 nrm = new Vec3(-dir.y, dir.x, 0);
            Vec3 mid = root.add(dir.scale(len * .55));
            Vec3 tip = root.add(dir.scale(len));
            quad(vc, m, root, mid.add(nrm.scale(wdt)), tip, mid.subtract(nrm.scale(wdt)), rgb, alpha * (.5f + .5f * u));
            seg(vc, m, root, tip, .022f, core, alpha);
        }
    }

    /** One free-floating feather (leaf quad + spine) at pos pointing along dir. */
    public static void feather(VertexConsumer vc, Matrix4f m, Vec3 pos, Vec3 dir, float len, float width, float alpha, int rgb, int core) {
        Vec3 d = dir.normalize(), n = d.cross(Math.abs(d.y) > .95 ? new Vec3(1, 0, 0) : UPV).normalize();
        Vec3 mid = pos.add(d.scale(len * .5)), tip = pos.add(d.scale(len));
        quad(vc, m, pos, mid.add(n.scale(width)), tip, mid.subtract(n.scale(width)), rgb, alpha);
        seg(vc, m, pos, tip, width * .25f, core, alpha);
    }

    /** Tapered crescent band in the local XZ plane between angles a0..a1 (a sickle / sword-slash shape). */
    public static void crescent(VertexConsumer vc, Matrix4f m, float radius, float thick, float a0, float a1, float y, float alpha, int rgb, int core) {
        int n = 30;
        for (int i = 0; i < n; i++) {
            float t0 = i / (float) n, t1 = (i + 1) / (float) n;
            float g0 = FxMath.lerp(a0, a1, t0), g1 = FxMath.lerp(a0, a1, t1);
            float w0 = thick * (float) Math.sin(t0 * Math.PI), w1 = thick * (float) Math.sin(t1 * Math.PI);
            quad(vc, m, p(g0, radius - w0, y), p(g0, radius + w0 * .3f, y), p(g1, radius + w1 * .3f, y), p(g1, radius - w1, y), rgb, alpha);
            seg(vc, m, p(g0, radius + w0 * .3f, y), p(g1, radius + w1 * .3f, y), .05f, core, alpha);
        }
    }

    /** Open translucent cylinder (a column of light / a blood pillar), fading toward the top. */
    public static void pillar(VertexConsumer vc, Matrix4f m, float radius, float height, float y0, float alpha, int rgb, int sides, float rot) {
        for (int i = 0; i < sides; i++) {
            float a0 = rot + i * TAU / sides, a1 = rot + (i + 1) * TAU / sides;
            for (int k = 0; k < 4; k++) {
                float ya = y0 + height * k / 4f, yb = y0 + height * (k + 1) / 4f, al = alpha * (1f - k * .2f);
                quad(vc, m, p(a0, radius, ya), p(a1, radius, ya), p(a1, radius, yb), p(a0, radius, yb), rgb, al);
            }
        }
    }

    /** Radial rays in the local XZ plane. */
    public static void rays(VertexConsumer vc, Matrix4f m, int n, float r0, float r1, float thick, float rot, float y, float alpha, int rgb) {
        for (int i = 0; i < n; i++) {
            float a = rot + i * TAU / n;
            seg(vc, m, p(a, r0, y), p(a, r1, y), thick, rgb, alpha);
        }
    }

    /** Rising helix of rods. */
    public static void helix(VertexConsumer vc, Matrix4f m, float radius, float height, float turns, float y0, float thick, float rot, float alpha, int rgb, int steps) {
        Vec3 prev = null;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps, a = rot + t * turns * TAU;
            Vec3 q = new Vec3(Math.cos(a) * radius, y0 + height * t, Math.sin(a) * radius);
            if (prev != null) seg(vc, m, prev, q, thick, rgb, alpha);
            prev = q;
        }
    }

    /** Rotates the matrix so its local +Y axis points along dir (plain axis-angle, no JOML rotateTo needed). */
    public static Matrix4f alignY(Matrix4f mat, Vec3 dir) {
        Vec3 d = dir.normalize();
        Vec3 axis = UPV.cross(d);
        double len = axis.length();
        if (len < 1e-6) { // parallel or anti-parallel to Y
            if (d.y < 0) mat.rotateX((float) Math.PI);
            return mat;
        }
        float angle = (float) Math.acos(Math.max(-1, Math.min(1, UPV.dot(d))));
        return mat.rotate(angle, (float) (axis.x / len), (float) (axis.y / len), (float) (axis.z / len));
    }

    // ---------- render hook ----------

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        long now = System.nanoTime();
        ACTIVE.removeIf(e -> (now - e.startNanos) / 1e9f > e.seconds);
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        PoseStack ps = event.getPoseStack();
        for (Active e : ACTIVE) {
            float age = (now - e.startNanos) / 1e9f;
            ps.pushPose();
            ps.translate(e.pos.x - cam.x, e.pos.y - cam.y, e.pos.z - cam.z);
            ps.scale(e.scale, e.scale, e.scale);
            e.shape.draw(vc, ps.last().pose(), FxMath.clamp01(age / e.seconds), age);
            ps.popPose();
        }
        buffers.endBatch(RenderType.lightning());
    }
}
