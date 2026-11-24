package com.ancientstudios.ancientmagic;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * Draws the Sky Block: a cube whose fragment shader computes the sky colour from the view direction, using the live
 * world values (sky colour, sun/moon position, star brightness, rain) uploaded as uniforms every draw.
 */
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public class SkyBlockRenderer implements BlockEntityRenderer<SkyBlockEntity> {
    private static ShaderInstance skyShader;

    private static final RenderType SKY = RenderType.create("ancientmagic_sky",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> skyShader))
                    .setCullState(new RenderStateShard.CullStateShard(false))
                    .createCompositeState(false));

    // inset so we never z-fight with the neighbouring blocks' faces
    private static final float LO = 0.002F, HI = 0.998F;

    public SkyBlockRenderer(BlockEntityRendererProvider.Context context) {}

    @SubscribeEvent
    static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "sky_block"), DefaultVertexFormat.POSITION_COLOR),
                shader -> skyShader = shader);
    }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AncientMagic.SKY_BLOCK_ENTITY.get(), SkyBlockRenderer::new);
    }

    private static void uploadUniforms(ClientLevel level, float partialTick) {
        if (skyShader == null) return;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 sky = level.getSkyColor(cam, partialTick);
        float angle = level.getTimeOfDay(partialTick) * (float) (Math.PI * 2.0);
        // sun path matches vanilla: rises in +X, passes the zenith at angle 0
        float sunX = -(float) Math.sin(angle), sunY = (float) Math.cos(angle);
        if (skyShader.getUniform("SkyColor") != null) skyShader.getUniform("SkyColor").set((float) sky.x, (float) sky.y, (float) sky.z);
        if (skyShader.getUniform("SunDir") != null) skyShader.getUniform("SunDir").set(sunX, sunY, 0.0F);
        if (skyShader.getUniform("SkyParams") != null) {
            skyShader.getUniform("SkyParams").set(level.getStarBrightness(partialTick), level.getRainLevel(partialTick), angle, 0.0F);
        }
    }

    @Override
    public void render(SkyBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = be.getLevel();
        if (!(level instanceof ClientLevel clientLevel) || skyShader == null) return;
        uploadUniforms(clientLevel, partialTick);

        VertexConsumer vc = buffers.getBuffer(SKY);
        Matrix4f m = poseStack.last().pose();
        for (Direction d : Direction.values()) {
            if (level.getBlockState(be.getBlockPos().relative(d)).getBlock() instanceof SkyBlock) continue; // hidden face
            switch (d) {
                case DOWN -> quad(vc, m, LO, LO, LO, HI, LO, LO, HI, LO, HI, LO, LO, HI);
                case UP -> quad(vc, m, LO, HI, LO, LO, HI, HI, HI, HI, HI, HI, HI, LO);
                case NORTH -> quad(vc, m, LO, LO, LO, LO, HI, LO, HI, HI, LO, HI, LO, LO);
                case SOUTH -> quad(vc, m, LO, LO, HI, HI, LO, HI, HI, HI, HI, LO, HI, HI);
                case WEST -> quad(vc, m, LO, LO, LO, LO, LO, HI, LO, HI, HI, LO, HI, LO);
                case EAST -> quad(vc, m, HI, LO, LO, HI, HI, LO, HI, HI, HI, HI, LO, HI);
            }
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vc.addVertex(m, x1, y1, z1).setColor(255, 255, 255, 255);
        vc.addVertex(m, x2, y2, z2).setColor(255, 255, 255, 255);
        vc.addVertex(m, x3, y3, z3).setColor(255, 255, 255, 255);
        vc.addVertex(m, x4, y4, z4).setColor(255, 255, 255, 255);
    }

    @Override
    public int getViewDistance() { return 128; }
}