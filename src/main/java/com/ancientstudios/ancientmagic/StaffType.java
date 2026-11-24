package com.ancientstudios.ancientmagic;

import net.minecraft.world.phys.Vec3;

/**
 * The two staffs and everything that differs between them: ability names, UI strings, colours, cooldowns.
 * The FX packet carries a single int ("fx id") = ordinal * 10 + ability, see {@link #fxId(int)} / {@link #fromFxId(int)}.
 */
public enum StaffType {
    HATE("item.ancientmagic.admin_staff",
            new String[]{"Blood Lance", "Crimson Nova", "Hemorrhage Well", "Blood Pact", "Rending Chains",
                    "Wrath Meteor", "Reaper's Sweep", "Thorn Eruption", "Curse of Ruin", "Blood Moon"},
            new String[]{"☠", "✹", "◉", "♥", "✠", "☄", "⚔", "✶", "❖", "☽"},
            new String[]{"Piercing blood spear", "Blast of crimson wrath", "Drag foes into blood", "Drain, heal, empower",
                    "Bind foes in chains", "Meteor of burning hate", "Crescent scythe slash", "Thorns burst from ground",
                    "Lingering ruin sigil", "Shockwave of the moon"},
            new int[]{0xFF2D3C, 0xE8112D, 0xB5122E, 0xFF5260, 0xCC1A33, 0xFF3B2F, 0xE62E4D, 0xA30D24, 0xD1163A, 0xFF1744},
            new int[]{24, 40, 60, 60, 50, 80, 30, 50, 70, 110},
            0xFF1F3A, 1.0f, 0.06f, 0.10f),

    DETERMINATION("item.ancientmagic.staff_of_determination",
            new String[]{"Halo Lance", "Judgment", "Seraph Wings", "Sanctuary", "Divine Shield",
                    "Radiant Burst", "Starfall", "Seraph Blades", "Resurgence", "Heaven's Gate"},
            new String[]{"✦", "✞", "⚜", "❂", "✚", "☀", "✧", "✴", "☥", "❋"},
            new String[]{"Piercing beam of light", "Pillar of holy wrath", "Winged dash of light", "Hallowed healing dome",
                    "Guarded by feathers", "Blinding burst of light", "Stars rain from heaven", "Blades of mercy converge",
                    "Rise again, unbroken", "Gate opens, light falls"},
            new int[]{0xFFFFFF, 0xFFF2B8, 0xD6ECFF, 0xFFE7A3, 0xE9F4FF, 0xFFFBE0, 0xCFE6FF, 0xFFEFC2, 0xFFFFFF, 0xFFE08A},
            new int[]{20, 50, 40, 100, 80, 60, 70, 50, 120, 140},
            0xFFF1B5, 1.0f, 0.97f, 0.82f);

    public static final int COUNT = 10;

    /** Item translation key prefix, e.g. "item.ancientmagic.admin_staff" (+ ".desc", ".ability", ".controls"). */
    public final String langKey;
    public final String[] names, glyphs, descriptions;
    /** Per-ability accent colour (wheel wedge, charge motes). */
    public final int[] colors;
    /** Cooldown in ticks applied after each ability. */
    public final int[] cooldowns;
    /** Main UI accent. */
    public final int accent;
    /** Colour of the staff's point light (Veil). */
    public final float lightR, lightG, lightB;

    StaffType(String langKey, String[] names, String[] glyphs, String[] descriptions, int[] colors, int[] cooldowns,
              int accent, float lightR, float lightG, float lightB) {
        this.langKey = langKey;
        this.names = names;
        this.glyphs = glyphs;
        this.descriptions = descriptions;
        this.colors = colors;
        this.cooldowns = cooldowns;
        this.accent = accent;
        this.lightR = lightR;
        this.lightG = lightG;
        this.lightB = lightB;
    }

    public boolean isHate() { return this == HATE; }

    public int fxId(int ability) { return ordinal() * COUNT + Math.max(0, Math.min(COUNT - 1, ability)); }

    public static StaffType fromFxId(int fxId) { return values()[Math.max(0, Math.min(values().length - 1, fxId / COUNT))]; }

    public static int abilityOf(int fxId) { return Math.max(0, Math.min(COUNT - 1, fxId % COUNT)); }

    // ---- deterministic layouts shared by server (damage) and client (visuals) so both always agree ----

    /** Position of falling star i of Starfall around centre c. */
    public static Vec3 starPos(Vec3 c, int i, int total, double radius) {
        double a = i * 2.399963, r = radius * Math.sqrt((i + 0.5) / total);
        return new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
    }

    /** Game tick (after cast) at which star i lands. */
    public static int starStrikeTick(int i) { return 10 + 2 * i; }
}
