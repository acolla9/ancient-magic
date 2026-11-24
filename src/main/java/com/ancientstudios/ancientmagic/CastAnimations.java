// FILE: src/main/java/com/ancientstudios/ancientmagic/CastAnimations.java   (NEW file)
package com.ancientstudios.ancientmagic;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * One cast pose per ability, played through PlayerAnimationLib.
 *
 * The poses are defined in assets/ancientmagic/player_animations/admin_staff.animation.json. The server already sends
 * every nearby client a SpellFxPayload with the caster's eye position as {@code from}, so {@link #play} finds the caster
 * from that position and triggers the pose on THEIR animation layer: other players see your pose too, not just you.
 *
 * Poses (some are shared between the two staffs when the gesture fits both):
 * <pre>
 *   BEAM      thrust one arm straight out, braced stance     Blood Lance, Halo Lance
 *   SWEEP     wind-up across the body, slash with a twist    Reaper's Sweep, Seraph Blades
 *   SLAM      two hands overhead, driven down                Thorn Eruption
 *   SMITE     one arm to the sky, snaps down at the target   Judgment
 *   HURL      cocked overhand throw                          Wrath Meteor
 *   OVERHEAD  slow raise to the heavens, long hold           Blood Moon, Heaven's Gate
 *   NOVA      gather inward, fling both arms wide            Crimson Nova, Radiant Burst
 *   GROUND    crouched lunge, palms pressed down             Curse of Ruin, Sanctuary
 *   DASH      lean forward, arms swept back                  Seraph Wings
 *   EMBRACE   arms cross the chest, bow, open wide           Blood Pact, Divine Shield
 *   PULL      reach out, then heave back                     Hemorrhage Well
 *   CURSE     hands cross, then rend apart                   Rending Chains
 *   BLESS     palms up, lifting slowly                       Resurgence
 *   RAIN      arms sweep up into a V and shimmer             Starfall
 * </pre>
 */
public final class CastAnimations {
    /** MUST match StaffCastClient.ANIMATION_LAYER (the layer registered in StaffCastClient.registerAnimations). */
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "staff_cast_layer");
    /** The original generic pose, used as a fallback if a pose cannot be found. */
    private static final ResourceLocation FALLBACK = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "admin_staff_cast");

    public enum Pose {
        BEAM("cast_beam"), SWEEP("cast_sweep"), SLAM("cast_slam"), SMITE("cast_smite"), HURL("cast_hurl"),
        OVERHEAD("cast_overhead"), NOVA("cast_nova"), GROUND("cast_ground"), DASH("cast_dash"),
        EMBRACE("cast_embrace"), PULL("cast_pull"), CURSE("cast_curse"), BLESS("cast_bless"), RAIN("cast_rain");

        public final ResourceLocation id;

        Pose(String name) { this.id = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, name); }
    }

    //                                            0            1            2           3             4            5
    //                                            6            7            8           9
    private static final Pose[] HATE = {
            Pose.BEAM, Pose.NOVA, Pose.PULL, Pose.EMBRACE, Pose.CURSE, Pose.HURL,
            Pose.SWEEP, Pose.SLAM, Pose.GROUND, Pose.OVERHEAD};
    private static final Pose[] DETERMINATION = {
            Pose.BEAM, Pose.SMITE, Pose.DASH, Pose.GROUND, Pose.EMBRACE, Pose.NOVA,
            Pose.RAIN, Pose.SWEEP, Pose.BLESS, Pose.OVERHEAD};

    private CastAnimations() {}

    public static Pose poseFor(StaffType type, int ability) {
        Pose[] table = type.isHate() ? HATE : DETERMINATION;
        return table[Math.max(0, Math.min(table.length - 1, ability))];
    }

    /** Plays the pose of this ability on whoever cast it (found by the FX origin = their eye position). */
    public static void play(StaffType type, int ability, Vec3 from) {
        if (!ClientConfig.castAnimations()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player caster = findCaster(mc, from);
        if (caster instanceof AbstractClientPlayer player) trigger(player, poseFor(type, ability));
    }

    /** The player whose eyes are closest to {@code from}, within ~2 blocks (the lance starts 0.6 blocks in front of the eyes). */
    private static Player findCaster(Minecraft mc, Vec3 from) {
        Player best = null;
        double bestSq = 4.0;
        for (Player p : mc.level.players()) {
            double d = p.getEyePosition().distanceToSqr(from);
            if (d < bestSq) { bestSq = d; best = p; }
        }
        return best;
    }

    private static void trigger(AbstractClientPlayer player, Pose pose) {
        Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER);
        if (!(layer instanceof PlayerAnimationController controller)) return;
        // triggerAnimation returns false (and logs) when the id is unknown -> fall back to the generic pose
        if (!controller.triggerAnimation(pose.id)) controller.triggerAnimation(FALLBACK);
    }
}