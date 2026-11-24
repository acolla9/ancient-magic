package com.ancientstudios.ancientmagic;

import java.awt.Color;
import java.util.concurrent.TimeUnit;

import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.enums.PlayState;

import cn.anecansaitin.freecameraapi.CameraModifierManager;
import cn.anecansaitin.freecameraapi.ICameraModifier;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import team.lodestar.lodestone.modules.rendering.particle.standard.builder.WorldParticleBuilder;
import team.lodestar.lodestone.modules.rendering.particle.standard.data.GenericParticleData;
import team.lodestar.lodestone.modules.rendering.particle.standard.data.color.ColorParticleData;
import team.lodestar.lodestone.registry.common.particle.LodestoneParticleTypes;

@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public final class StaffCastClient {
    private static final ResourceLocation ANIMATION_LAYER = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "staff_cast_layer");
    private static final ResourceLocation CAST_ANIMATION = ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "admin_staff_cast");
    private static final long CHARGE_DURATION_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
    private static final long CAMERA_RETURN_START_NANOS = TimeUnit.MILLISECONDS.toNanos(1220);
    private static final long CAMERA_RETURN_DURATION_NANOS = TimeUnit.MILLISECONDS.toNanos(280);
    private static final String CAMERA_MODIFIER_ID = "ancientmagic_admin_staff_cast";
    private static final double SHOULDER_OFFSET = 0.72;
    private static final double CAMERA_HEIGHT = 1.62;
    private static final double CAMERA_BACK_OFFSET = -3.82;
    private static final double CAMERA_FORWARD_SHIFT = 0.18;

    private static long chargeStartedAt;
    private static long lastParticleAt;
    private static boolean charging;
    private static CameraType previousCameraType;
    private static ICameraModifier cameraModifier;
    private static LightRenderHandle<PointLightData> staffLight;
    private static boolean wheelOpen;
    private static int selectedAbility;
    private static StaffType currentType = StaffType.HATE;

    private StaffCastClient() {}

    public static void openWheel(Player player, StaffType type) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != player) return;
        wheelOpen = true;
        currentType = type;
        selectedAbility = 0;
        charging = true;
        chargeStartedAt = System.nanoTime();
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        cameraModifier = CameraModifierManager.createModifier(CAMERA_MODIFIER_ID, true);
        cameraModifier.enable().enablePos().setPos(SHOULDER_OFFSET, CAMERA_HEIGHT, CAMERA_BACK_OFFSET);
        minecraft.setScreen(new AdminStaffWheelScreen(type));
    }

    public static int selectAbility() { return selectedAbility; }
    public static boolean isWheelOpen() { return wheelOpen; }
    public static StaffType currentType() { return currentType; }
    public static void setSelectedAbility(int ability) { selectedAbility = Math.max(0, Math.min(StaffType.COUNT - 1, ability)); }
    public static void closeWheel() { wheelOpen = false; }

    public static void confirmWheel() {
        Minecraft mc = Minecraft.getInstance();
        if (!wheelOpen || mc.player == null) return;
        // Server re-validates the held staff, cooldown, permission and config; no chat command involved.
        PacketDistributor.sendToServer(new CastSpellPayload(currentType.ordinal(), selectedAbility, clientRenderDistance()));
        wheelOpen = false;
        if (mc.screen instanceof AdminStaffWheelScreen) mc.setScreen(null);
        stopCharge(mc);
    }

    public static void quickCast(StaffType type, int ability) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ability < 0 || ability >= StaffType.COUNT) return;
        if (!(mc.player.getOffhandItem().getItem() instanceof AdminStaffItem held) || held.type() != type) return;
        if (mc.player.getCooldowns().isOnCooldown(held)) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.ancientmagic.staff_cooling_down"), true);
            return;
        }
        PacketDistributor.sendToServer(new CastSpellPayload(type.ordinal(), ability, clientRenderDistance()));
    }

    private static int clientRenderDistance() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.renderDistance().get() * 16;
    }

    public static void cancelWheel() {
        Minecraft mc = Minecraft.getInstance();
        wheelOpen = false;
        if (mc.screen instanceof AdminStaffWheelScreen) mc.setScreen(null);
        stopCharge(mc);
    }

    public static void registerAnimations(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                ANIMATION_LAYER, 1600,
                player -> new PlayerAnimationController(player, (controller, state, setter) -> PlayState.STOP)));
    }

    public static void beginCharge(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != player) return;

        charging = true;
        chargeStartedAt = System.nanoTime();
        lastParticleAt = 0;
        previousCameraType = minecraft.options.getCameraType();
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        cameraModifier = CameraModifierManager.createModifier(CAMERA_MODIFIER_ID, true);
        cameraModifier.enable().enablePos()
                .setPos(0.0, CAMERA_HEIGHT, CAMERA_BACK_OFFSET);
        minecraft.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.75F, 0.8F, false);

        Object layer = PlayerAnimationAccess.getPlayerAnimationLayer(minecraft.player, ANIMATION_LAYER);
        if (layer instanceof PlayerAnimationController controller) {
            controller.triggerAnimation(CAST_ANIMATION);
        }
    }

    public static void cancelCharge() {
        if (!charging) return;
        chargeStartedAt = System.nanoTime() - CAMERA_RETURN_START_NANOS;
    }

    public static void finishCharge() {
        // The camera and light ease out in the render event after the server resolves the spell.
        if (charging) chargeStartedAt = System.nanoTime() - CAMERA_RETURN_START_NANOS;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!charging) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stopCharge(minecraft);
            return;
        }

        if (wheelOpen) {
            cameraModifier.setPos(SHOULDER_OFFSET, CAMERA_HEIGHT, CAMERA_BACK_OFFSET);
            updatePointLight(minecraft.player, 0.55F);
            if (System.nanoTime() - lastParticleAt > TimeUnit.MILLISECONDS.toNanos(55)) {
                AncientFx.chargeOrbit(minecraft.player, currentType, selectedAbility, System.nanoTime() / 1e9f);
                lastParticleAt = System.nanoTime();
            }
            return;
        }
        long elapsed = Math.max(0, System.nanoTime() - chargeStartedAt);
        double progress = Math.min(1.0, elapsed / (double) CHARGE_DURATION_NANOS);
        double shoulder = easeOutCubic(Math.min(1.0, elapsed / TimeUnit.MILLISECONDS.toNanos(320)));
        if (elapsed > CAMERA_RETURN_START_NANOS) {
            double returning = Math.min(1.0, (elapsed - CAMERA_RETURN_START_NANOS) / (double) CAMERA_RETURN_DURATION_NANOS);
            shoulder *= 1.0 - easeInOutCubic(returning);
        }

        if (cameraModifier != null) {
            // Free Camera API interprets this as a player-relative position and applies it after vanilla camera setup.
            // Positive local X places the view over the left shoulder in Free Camera API's player-relative axes;
            // moving local Z toward zero nudges it forward. The eased offset below handles smoothing directly.
            cameraModifier.setPos(SHOULDER_OFFSET * shoulder, CAMERA_HEIGHT + 0.05 * shoulder,
                    CAMERA_BACK_OFFSET + CAMERA_FORWARD_SHIFT * shoulder);
        }

        updatePointLight(minecraft.player, (float) (0.35 + Math.sin(progress * Math.PI) * 0.25));
        if (System.nanoTime() - lastParticleAt > TimeUnit.MILLISECONDS.toNanos(55)) {
            spawnChargeWisp(minecraft.player, progress);
            lastParticleAt = System.nanoTime();
        }

        if (elapsed >= CHARGE_DURATION_NANOS) {
            stopCharge(minecraft);
        }
    }

    private static void updatePointLight(Player player, float radius) {
        try {
            if (staffLight == null || !staffLight.isValid()) {
                Vec3 initialPosition = player.getEyePosition().add(player.getLookAngle().scale(0.7));
                PointLightData data = new PointLightData()
                        .setPosition(initialPosition.x, initialPosition.y, initialPosition.z)
                        .setRadius(6.5F)
                        .setColor(currentType.lightR, currentType.lightG, currentType.lightB)
                        .setBrightness(2.0F);
                staffLight = VeilRenderSystem.renderer().getLightRenderer().addLight(data);
            }
            Vec3 position = player.getEyePosition().add(player.getLookAngle().scale(0.7));
            PointLightData data = staffLight.getLightData();
            data.setPosition(position.x, position.y, position.z)
                    .setColor(currentType.lightR, currentType.lightG, currentType.lightB)
                    .setRadius(6.5F + radius)
                    .setBrightness(1.5F + radius);
            staffLight.markDirty();
        } catch (IllegalStateException | NullPointerException ignored) {
            // Veil's renderer may not be ready during a world transition; vanilla and Lodestone effects continue.
        }
    }

    private static void spawnChargeWisp(Player player, double progress) {
        Vec3 position = player.getEyePosition().add(player.getLookAngle().scale(0.72)).add(0, -0.12, 0);
        Color gold = new Color(255, 115 + (int) (progress * 70), 12);
        Color pale = new Color(255, 236, 145);
        WorldParticleBuilder.create(LodestoneParticleTypes.WISP_PARTICLE.get())
                .setScaleData(GenericParticleData.create(0.16F + (float) progress * 0.08F, 0.0F).build())
                .setTransparencyData(GenericParticleData.create(0.95F, 0.0F).build())
                .setColorData(ColorParticleData.create(gold, pale).build())
                .setLifetime(22)
                .setRandomOffset(0.08, 0.08, 0.08)
                .setRandomMotion(0.02, 0.025, 0.02)
                .addMotion(0, 0.018, 0)
                .enableNoClip()
                .spawn(player.level(), position.x, position.y, position.z);
    }

    private static void stopCharge(Minecraft minecraft) {
        charging = false;
        if (cameraModifier != null) {
            cameraModifier.disable();
            cameraModifier = null;
        }
        if (staffLight != null) {
            staffLight.free();
            staffLight = null;
        }
        if (previousCameraType != null) {
            minecraft.options.setCameraType(previousCameraType);
            previousCameraType = null;
        }
    }

    private static double easeOutCubic(double t) {
        return 1.0 - Math.pow(1.0 - t, 3.0);
    }

    private static double easeInOutCubic(double t) {
        return t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
    }
}
