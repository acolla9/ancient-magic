package com.ancientstudios.ancientmagic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Radial spell wheel with ten wedges, themed per staff:
 * Hate = blood-black, crimson thorns, dripping, heartbeat vignette.
 * Determination = starlit sky, white/gold glass, god rays, drifting sparkles.
 */
public final class AdminStaffWheelScreen extends Screen {
    private static final int N = StaffType.COUNT;
    private static final float STEP = (float) (Math.PI * 2 / N);
    private static final float GAP = (float) Math.toRadians(2.5);
    private static final float TAU = (float) (Math.PI * 2);

    private final StaffType type;
    private final boolean hate;
    private final float[] hover = new float[N];
    private float open;
    private float shownAngle;
    private float time;
    private long last = System.nanoTime();
    private boolean closing;

    public AdminStaffWheelScreen(StaffType type) {
        super(Component.literal(type.isHate() ? "Staff of Hate" : "Staff of Determination"));
        this.type = type;
        this.hate = type.isHate();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { /* custom background */ }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        time += dt;
        open = FxMath.damp(open, closing ? 0f : 1f, 14f, dt);
        float eo = FxMath.easeOutBack(open);
        int sel = StaffCastClient.selectAbility();
        for (int i = 0; i < N; i++) hover[i] = FxMath.damp(hover[i], i == sel ? 1f : 0f, 16f, dt);

        float cx = width / 2f, cy = height / 2f;
        double dx = mouseX - cx, dy = mouseY - cy;
        if (Math.hypot(dx, dy) > 12) {
            float target = (float) Math.atan2(dy, dx);
            shownAngle += FxMath.angleDelta(shownAngle, target) * (1f - (float) Math.exp(-22 * dt));
        }

        if (hate) drawHateBackground(g, cx, cy); else drawHolyBackground(g, cx, cy);
        if (open < 0.04f) return; // vanilla treats alpha<4 as opaque text

        float rIn = 70 * eo, rOut = 156 * eo;
        if (hate) drawHateRings(g, cx, cy, rIn, rOut); else drawHolyRings(g, cx, cy, rIn, rOut);

        int accent = type.accent;
        for (int i = 0; i < N; i++) {
            float h = hover[i];
            float mid = -(float) Math.PI / 2 + i * STEP, half = STEP / 2 - GAP / 2;
            float push = 16f * h * open;
            float ox = (float) Math.cos(mid) * push, oy = (float) Math.sin(mid) * push;
            int col = type.colors[i];
            int inner, outer, edge;
            if (hate) {
                inner = FxMath.mixArgb(FxMath.withAlpha(0x1A0408, 0.86f * open), FxMath.withAlpha(col, 0.62f), h);
                outer = FxMath.mixArgb(FxMath.withAlpha(0x3A0912, 0.9f * open), FxMath.withAlpha(col, 0.98f), h * .85f);
                edge = FxMath.mixArgb(FxMath.withAlpha(0x7A1424, open), FxMath.withAlpha(col, open), h);
            } else {
                inner = FxMath.mixArgb(FxMath.withAlpha(0xFFFFFF, 0.14f * open), FxMath.withAlpha(0xFFF6D8, 0.78f), h);
                outer = FxMath.mixArgb(FxMath.withAlpha(0xDCEBFF, 0.30f * open), FxMath.withAlpha(0xFFFFFF, 0.97f), h * .9f);
                edge = FxMath.mixArgb(FxMath.withAlpha(0xFFFFFF, 0.7f * open), FxMath.withAlpha(0xFFE08A, open), h);
            }
            UiDraw.wedge(g, cx + ox, cy + oy, rIn, rOut + 10 * h, mid - half, mid + half, inner, outer);
            UiDraw.arc(g, cx + ox, cy + oy, rOut + 10 * h, 2.4f, mid - half, mid + half, edge);
            UiDraw.arc(g, cx + ox, cy + oy, rIn, 1.4f, mid - half, mid + half, FxMath.withAlpha(hate ? 0x9A1428 : 0xFFFFFF, .6f * open));
            if (h > .02f) {
                int glow = hate ? col : 0xFFF2C0;
                UiDraw.wedge(g, cx + ox, cy + oy, rOut + 10 * h, rOut + 10 * h + 22 * h, mid - half, mid + half, FxMath.withAlpha(glow, 0.42f * h), FxMath.withAlpha(glow, 0f));
            }
            if (hate && h > .3f) { // blood spike on the hovered wedge
                float tipR = rOut + 10 * h + 20 * h;
                UiDraw.triangle(g, cx + ox + (float) Math.cos(mid - .06f) * (rOut + 8), cy + oy + (float) Math.sin(mid - .06f) * (rOut + 8), FxMath.withAlpha(col, .9f * h),
                        cx + ox + (float) Math.cos(mid + .06f) * (rOut + 8), cy + oy + (float) Math.sin(mid + .06f) * (rOut + 8), FxMath.withAlpha(col, .9f * h),
                        cx + ox + (float) Math.cos(mid) * tipR, cy + oy + (float) Math.sin(mid) * tipR, FxMath.withAlpha(0xFFB0A4, 0f));
            }
            if (!hate && h > .3f) { // sparkle on the hovered wedge
                float sr = rOut + 14 * h, sx = cx + ox + (float) Math.cos(mid) * sr, sy = cy + oy + (float) Math.sin(mid) * sr;
                float sz = 7 * h * (0.8f + 0.2f * (float) Math.sin(time * 6));
                UiDraw.diamond(g, sx, sy, sz * .6f, sz, FxMath.withAlpha(0xFFFFFF, h * open));
                UiDraw.diamond(g, sx, sy, sz, sz * .6f, FxMath.withAlpha(0xFFF2C0, h * open));
            }

            float rm = (rIn + rOut) / 2 + 4 * h;
            float gx = cx + ox + (float) Math.cos(mid) * rm, gy = cy + oy + (float) Math.sin(mid) * rm;
            float sc = 1.0f + 1.2f * h;
            g.pose().pushPose();
            g.pose().translate(gx, gy, 0);
            g.pose().scale(sc * 1.8f, sc * 1.8f, 1);
            int glyphCol = hate ? FxMath.mixArgb(0xE8A0A8, 0xFFFFFF, h) : FxMath.mixArgb(0xFFFFFF, 0x9A6A00, h * .7f);
            g.drawCenteredString(font, type.glyphs[i], 0, -4, FxMath.withAlpha(glyphCol, open));
            g.pose().popPose();
            String num = i == 9 ? "0" : Integer.toString(i + 1);
            g.drawCenteredString(font, num, (int) gx, (int) (gy - 28 - 4 * h), FxMath.withAlpha(hate ? 0xFF8090 : 0xFFFFFF, (0.35f + 0.65f * h) * open));
        }

        // needle
        float nr = rIn - 10;
        float nx = cx + (float) Math.cos(shownAngle) * nr, ny = cy + (float) Math.sin(shownAngle) * nr;
        if (hate) {
            float px = -(float) Math.sin(shownAngle) * 5, py = (float) Math.cos(shownAngle) * 5;
            UiDraw.triangle(g, cx + px, cy + py, FxMath.withAlpha(0x6A0A18, .9f * open), cx - px, cy - py, FxMath.withAlpha(0x6A0A18, .9f * open), nx, ny, FxMath.withAlpha(0xFF3040, open));
        } else {
            UiDraw.line(g, cx, cy, nx, ny, 2f, FxMath.withAlpha(0xFFFFFF, 0.85f * open));
            UiDraw.diamond(g, nx, ny, 4, 6, FxMath.withAlpha(0xFFF2C0, open));
        }

        // centre readout
        g.drawCenteredString(font, type.names[sel].toUpperCase(), (int) cx, (int) cy - 8, FxMath.withAlpha(hate ? 0xFF4050 : 0xFFFFFF, open));
        g.drawCenteredString(font, type.descriptions[sel], (int) cx, (int) cy + 4, FxMath.withAlpha(hate ? 0xD08088 : 0xBFD8FF, open));
        Minecraft mc = Minecraft.getInstance();
        var staffItem = type == StaffType.HATE ? AncientMagic.ADMIN_STAFF.get() : AncientMagic.STAFF_OF_DETERMINATION.get();
        int cooldownTicks = Math.round(type.cooldowns[sel] * mc.player.getCooldowns().getCooldownPercent(staffItem, partialTick));
        String cooldown = cooldownTicks > 0 ? (cooldownTicks / 20 + 1) + "s cooldown" : "Ready";
        String range = "Range up to " + Math.max(64, mc.options.renderDistance().get() * 16) + " blocks";
        g.drawCenteredString(font, cooldown + "  ·  " + range, (int) cx, (int) cy + 17, FxMath.withAlpha(hate ? 0xFF929A : 0xDCEAFF, .9f * open));

        renderPanel(g, mouseX, mouseY, sel);
        String hint = "1-9 / 0 quick cast   ·   mouse aims   ·   " + StaffKeybinds.confirmKeyLabel() + " / click " + (hate ? "unleash" : "ascend") + "   ·   esc cancel";
        g.drawCenteredString(font, hint, (int) cx, height - 20, FxMath.withAlpha(hate ? 0xFF9AA4 : 0xFFFFFF, 0.8f * open));
    }

    // ---------------------------------------------------------------- Hate decoration

    private void drawHateBackground(GuiGraphics g, float cx, float cy) {
        g.fillGradient(0, 0, width, height, FxMath.withAlpha(0x1C0308, 0.68f * open), FxMath.withAlpha(0x040001, 0.88f * open));
        float beat = (float) Math.pow(Math.max(0, Math.sin(time * 5.2)), 6) * .6f + (float) Math.pow(Math.max(0, Math.sin(time * 5.2 - .9)), 6) * .3f;
        UiDraw.vignette(g, width, height, 0xB0081C, (0.30f + 0.4f * beat) * open);
        UiDraw.wedge(g, cx, cy, 0, 210, 0, TAU, FxMath.withAlpha(0xA00A1C, 0.28f * open), FxMath.withAlpha(0xA00A1C, 0f));
        for (int i = 0; i < 18; i++) { // dripping blood from the top edge
            float x = (i * 997 % 1000) / 1000f * width, speed = 20 + (i * 37 % 40), delay = (i * 53 % 100) / 100f * 6f;
            float len = ((time * speed + delay * 40) % (height * 0.55f)) * open;
            int c = FxMath.withAlpha(0xA3101F, 0.75f * open);
            g.fill((int) x, 0, (int) x + 2, (int) len, c);
            UiDraw.wedge(g, x + 1, len, 0, 3.2f, 0, TAU, FxMath.withAlpha(0xFF2A40, 0.9f * open), FxMath.withAlpha(0xA3101F, 0.6f * open));
        }
    }

    private void drawHateRings(GuiGraphics g, float cx, float cy, float rIn, float rOut) {
        for (int k = 0; k < 2; k++) { // rotating rings of thorns, one pointing out, one pointing in
            int count = 28 - k * 6;
            float r = rOut + 26 + k * 14, spin = time * (k == 0 ? .25f : -.4f);
            for (int s = 0; s < count; s++) {
                float a = spin + s * TAU / count, wdt = .05f;
                float tip = k == 0 ? r + 12 : r - 12;
                int base = FxMath.withAlpha(0xA3101F, (0.65f - k * .2f) * open), pt = FxMath.withAlpha(0xFF3A4A, 0f);
                UiDraw.triangle(g, cx + (float) Math.cos(a - wdt) * r, cy + (float) Math.sin(a - wdt) * r, base,
                        cx + (float) Math.cos(a + wdt) * r, cy + (float) Math.sin(a + wdt) * r, base,
                        cx + (float) Math.cos(a) * tip, cy + (float) Math.sin(a) * tip, pt);
            }
            UiDraw.arc(g, cx, cy, r, 1.2f, 0, TAU, FxMath.withAlpha(0x8A1022, 0.55f * open));
        }
        UiDraw.arc(g, cx, cy, rIn - 6, 1.6f, 0, TAU, FxMath.withAlpha(0xFF2A40, 0.55f * open));
    }

    // ---------------------------------------------------------------- Determination decoration

    private void drawHolyBackground(GuiGraphics g, float cx, float cy) {
        g.fillGradient(0, 0, width, height, FxMath.withAlpha(0x0B1330, 0.62f * open), FxMath.withAlpha(0x22346A, 0.78f * open));
        UiDraw.wedge(g, cx, cy, 0, 280, 0, TAU, FxMath.withAlpha(0xFFFFFF, 0.24f * open), FxMath.withAlpha(0xFFFFFF, 0f));
        float reach = Math.max(width, height);
        for (int i = 0; i < 16; i++) { // god rays
            float a = time * .08f + i * TAU / 16, wdt = .035f + .02f * (i % 3);
            float p = 0.5f + 0.5f * (float) Math.sin(time * 1.3f + i);
            UiDraw.triangle(g, cx, cy, FxMath.withAlpha(0xFFFFFF, 0.13f * p * open),
                    cx + (float) Math.cos(a - wdt) * reach, cy + (float) Math.sin(a - wdt) * reach, FxMath.withAlpha(0xFFF2C0, 0f),
                    cx + (float) Math.cos(a + wdt) * reach, cy + (float) Math.sin(a + wdt) * reach, FxMath.withAlpha(0xFFF2C0, 0f));
        }
        for (int i = 0; i < 26; i++) { // drifting sparkles / feathers
            float bx = (i * 811 % 1000) / 1000f * width, speed = 10 + (i * 29 % 22);
            float y = ((time * speed + i * 47) % (height + 20)) - 10, x = bx + (float) Math.sin(time * .9f + i) * 14;
            float sz = 1.6f + (i % 3);
            UiDraw.diamond(g, x, y, sz * .7f, sz * 1.4f, FxMath.withAlpha(i % 4 == 0 ? 0xFFE9A0 : 0xFFFFFF, (0.35f + 0.3f * (i % 3) / 2f) * open));
        }
    }

    private void drawHolyRings(GuiGraphics g, float cx, float cy, float rIn, float rOut) {
        for (int k = 0; k < 3; k++) {
            float r = rOut + 20 + k * 10, spin = time * (k % 2 == 0 ? .25f : -.35f);
            UiDraw.arc(g, cx, cy, r, k == 0 ? 2f : 1f, 0, TAU, FxMath.withAlpha(k == 1 ? 0xFFE08A : 0xFFFFFF, (0.55f - k * .12f) * open));
            for (int s = 0; s < N; s++) {
                float a = spin + s * STEP;
                UiDraw.diamond(g, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 2.2f, 4.2f - k, FxMath.withAlpha(k == 1 ? 0xFFE08A : 0xFFFFFF, (0.8f - k * .2f) * open));
            }
        }
        UiDraw.arc(g, cx, cy, rIn - 6, 1.6f, 0, TAU, FxMath.withAlpha(0xFFFFFF, 0.7f * open));
        UiDraw.arc(g, cx, cy - 0, rIn - 12, 1f, 0, TAU, FxMath.withAlpha(0xFFE08A, 0.5f * open));
    }

    // ---------------------------------------------------------------- panel

    private void renderPanel(GuiGraphics g, int mx, int my, int sel) {
        int w = 158, h = 218;
        int slide = (int) ((1f - FxMath.easeOutCubic(open)) * 40);
        int x = width - w - 24 + slide, y = height / 2 - h / 2;
        int accent = type.accent;
        int titleC = hate ? 0xFF5A68 : 0xFFFFFF, subC = hate ? 0xA05058 : 0xC9DCFF, rowC = hate ? 0xD9A0A6 : 0xE4EEFF;
        if (hate) UiDraw.panelHate(g, x, y, w, h, open, time); else UiDraw.panelHoly(g, x, y, w, h, open, time);
        g.drawString(font, hate ? "Invoke Hatred" : "Invoke Radiance", x + 12, y + 8, FxMath.withAlpha(titleC, open), false);
        g.drawString(font, hate ? "Staff of Hate" : "Staff of Determination", x + 12, y + 20, FxMath.withAlpha(subC, open), false);
        for (int i = 0; i < N; i++) {
            int ry = y + 33 + i * 14;
            boolean s = i == sel;
            if (s) {
                g.fill(x + 4, ry, x + w - 4, ry + 13, FxMath.withAlpha(hate ? 0x5A0A16 : 0xFFFFFF, (hate ? .75f : .28f) * open));
                g.fill(x + 4, ry, x + 6, ry + 13, FxMath.withAlpha(accent, open));
            }
            g.drawString(font, type.glyphs[i] + " " + type.names[i], x + 12, ry + 3, FxMath.withAlpha(s ? 0xFFFFFF : rowC, open), false);
            if (s) g.drawString(font, "✔", x + w - 16, ry + 3, FxMath.withAlpha(hate ? 0xFF3A4A : 0xFFE08A, open), false);
        }
        g.fill(x + 8, y + 176, x + w - 8, y + 177, FxMath.withAlpha(hate ? 0x7A1424 : 0xFFFFFF, (hate ? 1f : .5f) * open));
        float pulse = 0.65f + 0.35f * (float) Math.sin(time * 4);
        g.fill(x + 10, y + 184, x + 14, y + 188, FxMath.withAlpha(accent, pulse * open));
        g.drawString(font, hate ? "Hatred burns" : "Hope endures", x + 20, y + 182, FxMath.withAlpha(accent, open), false);
        boolean over = mx >= x + 8 && mx <= x + w - 8 && my >= y + 196 && my <= y + 212;
        int btn = hate ? (over ? 0xFF3A4A : 0xB0102A) : (over ? 0xFFFFFF : 0xFFF1B5);
        g.fill(x + 8, y + 196, x + w - 8, y + 212, FxMath.withAlpha(btn, open));
        g.drawCenteredString(font, hate ? "UNLEASH" : "ASCEND", x + w / 2, y + 200, FxMath.withAlpha(hate ? 0xFFE0E4 : 0x1A2548, open));
    }

    // ---------------------------------------------------------------- input

    @Override
    public void mouseMoved(double x, double y) {
        double dx = x - width / 2.0, dy = y - height / 2.0;
        if (Math.hypot(dx, dy) > 28) {
            StaffCastClient.setSelectedAbility(Mth.floor((Math.atan2(dy, dx) + Math.PI / 2 + Math.PI * 2) / STEP + .5) % N);
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        StaffCastClient.setSelectedAbility((StaffCastClient.selectAbility() + (sy < 0 ? 1 : N - 1)) % N);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) { StaffCastClient.setSelectedAbility(key - GLFW.GLFW_KEY_1); return true; }
        if (key == GLFW.GLFW_KEY_0) { StaffCastClient.setSelectedAbility(9); return true; }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_DOWN) { StaffCastClient.setSelectedAbility((StaffCastClient.selectAbility() + 1) % N); return true; }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_UP) { StaffCastClient.setSelectedAbility((StaffCastClient.selectAbility() + N - 1) % N); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) { StaffCastClient.confirmWheel(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { StaffCastClient.cancelWheel(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override public boolean mouseClicked(double x, double y, int button) { StaffCastClient.confirmWheel(); return true; }
}
