package com.ancientstudios.ancientmagic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds no data; exists only so {@link SkyBlockRenderer} can draw the sky. */
public class SkyBlockEntity extends BlockEntity {
    public SkyBlockEntity(BlockPos pos, BlockState state) {
        super(AncientMagic.SKY_BLOCK_ENTITY.get(), pos, state);
    }
}