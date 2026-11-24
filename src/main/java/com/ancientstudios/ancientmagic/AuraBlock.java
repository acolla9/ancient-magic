package com.ancientstudios.ancientmagic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * Block of Hate / Block of Determination. Pure client-side ambient effects (animateTick only runs on the client,
 * near the player): an orbiting ring, rising motes, and occasional bursts, themed red vs white/gold.
 */
public class AuraBlock extends Block {
    private static final Vector3f CRIMSON = new Vector3f(0.90f, 0.05f, 0.12f);
    private static final Vector3f BLACKRED = new Vector3f(0.20f, 0.0f, 0.03f);
    private static final Vector3f WHITE = new Vector3f(1.0f, 1.0f, 1.0f);
    private static final Vector3f GOLD = new Vector3f(1.0f, 0.88f, 0.45f);

    private final boolean hate;

    public AuraBlock(Properties properties, boolean hate) {
        super(properties);
        this.hate = hate;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double cx = pos.getX() + .5, cy = pos.getY() + .5, cz = pos.getZ() + .5;
        double t = level.getGameTime();
        if (hate) hateFx(level, random, cx, cy, cz, t);
        else determinationFx(level, random, cx, cy, cz, t);
    }

    private static void hateFx(Level level, RandomSource r, double cx, double cy, double cz, double t) {
        // two embers orbiting the block on a tilted ring, trailing red-to-black
        for (int i = 0; i < 2; i++) {
            double a = t * .18 + i * Math.PI;
            double x = cx + Math.cos(a) * 1.05, z = cz + Math.sin(a) * 1.05;
            double y = cy + Math.sin(a * 2) * .35;
            level.addParticle(new DustColorTransitionOptions(CRIMSON, BLACKRED, 1.1f), x, y, z, 0, .01, 0);
        }
        // blood motes seeping off the faces and rising
        if (r.nextInt(2) == 0) {
            double x = cx + (r.nextDouble() - .5) * 1.1, y = cy + (r.nextDouble() - .5) * 1.1, z = cz + (r.nextDouble() - .5) * 1.1;
            level.addParticle(new DustParticleOptions(CRIMSON, .9f), x, y, z, 0, .025 + r.nextDouble() * .02, 0);
        }
        // dripping blood from the underside
        if (r.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.FALLING_LAVA, cx + (r.nextDouble() - .5) * .8, cy - .52, cz + (r.nextDouble() - .5) * .8, 0, 0, 0);
        }
        // slow crimson spores and a wisp of smoke above
        if (r.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.CRIMSON_SPORE, cx + (r.nextDouble() - .5) * 2, cy + r.nextDouble(), cz + (r.nextDouble() - .5) * 2, 0, .01, 0);
        }
        if (r.nextInt(8) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, cx + (r.nextDouble() - .5) * .8, cy + .6, cz + (r.nextDouble() - .5) * .8, 0, .03, 0);
        }
        // occasional spark burst
        if (r.nextInt(40) == 0) {
            for (int i = 0; i < 8; i++) {
                level.addParticle(new DustParticleOptions(CRIMSON, 1.4f), cx, cy + .6, cz,
                        (r.nextDouble() - .5) * .25, .05 + r.nextDouble() * .1, (r.nextDouble() - .5) * .25);
            }
        }
    }

    private static void determinationFx(Level level, RandomSource r, double cx, double cy, double cz, double t) {
        // double helix climbing around the block
        for (int i = 0; i < 2; i++) {
            double a = t * .15 + i * Math.PI;
            double y = cy - .5 + ((t * .02) % 1.0) * 2.0;
            level.addParticle(new DustColorTransitionOptions(WHITE, GOLD, 1.0f), cx + Math.cos(a) * .95, y, cz + Math.sin(a) * .95, 0, 0, 0);
        }
        // halo ring floating above
        if (t % 2 == 0) {
            double a = t * .12;
            level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a) * .7, cy + 1.2, cz + Math.sin(a) * .7, 0, 0, 0);
            level.addParticle(ParticleTypes.END_ROD, cx + Math.cos(a + Math.PI) * .7, cy + 1.2, cz + Math.sin(a + Math.PI) * .7, 0, 0, 0);
        }
        // feathery motes drifting up
        if (r.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.END_ROD, cx + (r.nextDouble() - .5) * 1.2, cy - .3 + r.nextDouble(), cz + (r.nextDouble() - .5) * 1.2, 0, .02 + r.nextDouble() * .02, 0);
        }
        if (r.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.GLOW, cx + (r.nextDouble() - .5) * 1.6, cy + (r.nextDouble() - .5) * 1.6, cz + (r.nextDouble() - .5) * 1.6, 0, .01, 0);
        }
        // occasional starburst
        if (r.nextInt(40) == 0) {
            for (int i = 0; i < 10; i++) {
                level.addParticle(ParticleTypes.END_ROD, cx, cy + .7, cz,
                        (r.nextDouble() - .5) * .2, (r.nextDouble() - .2) * .15, (r.nextDouble() - .5) * .2);
            }
        }
    }
}