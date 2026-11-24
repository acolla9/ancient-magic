package com.ancientstudios.ancientmagic;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Vanilla-only GUI primitives: annular wedges, glow lines, triangles, themed panels. No library dependencies. */
public final class UiDraw {
    private UiDraw() {}

    private static void begin() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /** Filled annular sector. Angles in radians (0 = +X, clockwise on screen). Colours fade inner -> outer. */
    public static void wedge(GuiGraphics g, float cx, float cy, float rIn, float rOut,
                             float a0, float a1, int colIn, int colOut) {
        begin();
        Matrix4f m = g.pose().last().pose();
        int steps = Math.max(4, (int) ((a1 - a0) * 28));
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= steps; i++) {
            float a = a0 + (a1 - a0) * i / steps, c = (float) Math.cos(a), s = (float) Math.sin(a);
            b.addVertex(m, cx + c * rIn, cy + s * rIn, 0).setColor(colIn);
            b.addVertex(m, cx + c * rOut, cy + s * rOut, 0).setColor(colOut);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** Thin ring / arc line made of a very thin wedge. */
    public static void arc(GuiGraphics g, float cx, float cy, float r, float thick, float a0, float a1, int col) {
        wedge(g, cx, cy, r - thick / 2, r + thick / 2, a0, a1, col, col);
    }

    /** Straight line with thickness, drawn as a quad. */
    public static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float thick, int col) {
        begin();
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f) return;
        float nx = -dy / len * thick / 2, ny = dx / len * thick / 2;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, x0 - nx, y0 - ny, 0).setColor(col);
        b.addVertex(m, x0 + nx, y0 + ny, 0).setColor(col);
        b.addVertex(m, x1 - nx, y1 - ny, 0).setColor(col);
        b.addVertex(m, x1 + nx, y1 + ny, 0).setColor(col);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** Single triangle; each vertex has its own colour so edges can fade out. */
    public static void triangle(GuiGraphics g, float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2) {
        begin();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        b.addVertex(m, x0, y0, 0).setColor(c0);
        b.addVertex(m, x1, y1, 0).setColor(c1);
        b.addVertex(m, x2, y2, 0).setColor(c2);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** Four-point diamond / sparkle of half-size r. */
    public static void diamond(GuiGraphics g, float cx, float cy, float rx, float ry, int col) {
        triangle(g, cx, cy - ry, col, cx + rx, cy, col, cx, cy + ry, col);
        triangle(g, cx, cy - ry, col, cx - rx, cy, col, cx, cy + ry, col);
    }

    /** Soft glow: stacked translucent outlines expanding outward. */
    public static void glowRect(GuiGraphics g, int x, int y, int w, int h, int rgb, float strength) {
        for (int i = 6; i >= 1; i--) {
            float a = strength * 0.16f / i;
            g.fill(x - i, y - i, x + w + i, y + h + i, FxMath.withAlpha(rgb, a));
        }
    }

    /** Coloured fade-in from all four screen edges. */
    public static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha) {
        int steps = 26, step = 4;
        for (int i = 0; i < steps; i++) {
            int col = FxMath.withAlpha(rgb, alpha * (1f - i / (float) steps) * (1f - i / (float) steps));
            int o = i * step;
            g.fill(0, o, w, o + step, col);
            g.fill(0, h - o - step, w, h - o, col);
            g.fill(o, 0, o + step, h, col);
            g.fill(w - o - step, 0, w - o, h, col);
        }
    }

    /** Original futuristic panel (cyan style). */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent, float alpha) {
        g.fill(x, y, x + w, y + h, FxMath.withAlpha(0x08101C, 0.88f * alpha));
        g.fillGradient(x, y, x + w, y + 22, FxMath.withAlpha(0x14283E, 0.9f * alpha), FxMath.withAlpha(0x0C1828, 0.0f));
        g.renderOutline(x, y, w, h, FxMath.withAlpha(0x2A4258, alpha));
        g.fill(x, y, x + 2, y + h, FxMath.withAlpha(accent, alpha));
        int t = 6, c = FxMath.withAlpha(accent, alpha);
        g.fill(x + w - t, y, x + w, y + 1, c); g.fill(x + w - 1, y, x + w, y + t, c);
        g.fill(x + w - t, y + h - 1, x + w, y + h, c); g.fill(x + w - 1, y + h - t, x + w, y + h, c);
    }

    /** Hate panel: blood-black body, jagged thorn edge along the top, crimson spine, dripping bottom. */
    public static void panelHate(GuiGraphics g, int x, int y, int w, int h, float alpha, float time) {
        g.fill(x, y, x + w, y + h, FxMath.withAlpha(0x120205, 0.92f * alpha));
        g.fillGradient(x, y, x + w, y + 30, FxMath.withAlpha(0x5A0A14, 0.75f * alpha), FxMath.withAlpha(0x120205, 0f));
        g.renderOutline(x, y, w, h, FxMath.withAlpha(0x7A0F1E, alpha));
        g.fill(x, y, x + 3, y + h, FxMath.withAlpha(0xFF1F3A, alpha));
        int thorn = FxMath.withAlpha(0xC4122C, alpha);
        for (int i = 0; i < w / 10; i++) {
            float tx = x + 5 + i * 10, len = 5 + 3 * (float) Math.sin(i * 1.7f + time * 2f);
            triangle(g, tx, y, thorn, tx + 10, y, thorn, tx + 5, y - len, FxMath.withAlpha(0xFF3040, 0f));
        }
        for (int i = 0; i < 5; i++) {
            float dx = x + 14 + i * (w - 28) / 4f, len = 6 + 10 * (0.5f + 0.5f * (float) Math.sin(time * 1.3f + i * 2.1f));
            g.fill((int) dx, y + h, (int) dx + 2, y + h + (int) len, FxMath.withAlpha(0xB0102A, alpha * .85f));
            g.fill((int) dx - 1, y + h + (int) len, (int) dx + 3, y + h + (int) len + 3, FxMath.withAlpha(0xFF2040, alpha * .85f));
        }
    }

    /** Determination panel: luminous frosted glass, gold-white frame, diamond corners, soft top light. */
    public static void panelHoly(GuiGraphics g, int x, int y, int w, int h, float alpha, float time) {
        g.fill(x, y, x + w, y + h, FxMath.withAlpha(0xDCE8FF, 0.13f * alpha));
        g.fillGradient(x, y, x + w, y + 36, FxMath.withAlpha(0xFFFFFF, 0.28f * alpha), FxMath.withAlpha(0xFFFFFF, 0f));
        glowRect(g, x, y, w, h, 0xFFF4C8, 0.7f * alpha);
        g.renderOutline(x, y, w, h, FxMath.withAlpha(0xFFFFFF, 0.8f * alpha));
        g.renderOutline(x + 2, y + 2, w - 4, h - 4, FxMath.withAlpha(0xFFE9A0, 0.45f * alpha));
        g.fill(x, y, x + 2, y + h, FxMath.withAlpha(0xFFF1B5, alpha));
        float pulse = 0.8f + 0.2f * (float) Math.sin(time * 2.4f);
        int gold = FxMath.withAlpha(0xFFE9A0, alpha * pulse);
        diamond(g, x, y, 5, 5, gold); diamond(g, x + w, y, 5, 5, gold);
        diamond(g, x, y + h, 5, 5, gold); diamond(g, x + w, y + h, 5, 5, gold);
    }
}
