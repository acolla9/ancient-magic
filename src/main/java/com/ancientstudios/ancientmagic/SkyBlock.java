package com.ancientstudios.ancientmagic;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that shows the real sky (sun, moon, stars, weather, day/night colours) as if nothing were in the way.
 * The block model is invisible (BaseEntityBlock default); {@link SkyBlockRenderer} draws it with a sky shader.
 * Skylight passes straight through it, so it also behaves like an open hole to the sky for lighting.
 */
public class SkyBlock extends BaseEntityBlock {
    public static final MapCodec<SkyBlock> CODEC = simpleCodec(SkyBlock::new);

    public SkyBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SkyBlockEntity(pos, state); }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) { return 1.0F; }
}