package com.ancientstudios.ancientmagic;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Shared item class for both staffs; the {@link StaffType} decides abilities, tooltip and cooldowns. */
public class AdminStaffItem extends Item {
    private final StaffType type;

    public AdminStaffItem(Properties properties, StaffType type) {
        super(properties);
        this.type = type;
    }

    public StaffType type() { return type; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) StaffCastClient.openWheel(player, type);
        return InteractionResultHolder.consume(stack);
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 0; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        boolean hate = type.isHate();
        tooltip.add(Component.translatable(type.langKey + ".desc").withStyle(hate ? ChatFormatting.RED : ChatFormatting.WHITE));
        tooltip.add(Component.translatable(type.langKey + ".ability").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(type.langKey + ".controls").withStyle(hate ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW));
    }

    /** Server side: runs the ability and starts that ability's (config-scaled) cooldown. */
    public static void cast(Level level, Player player, AdminStaffItem item, int ability) {
        cast(level, player, item, ability, 0);
    }

    public static void cast(Level level, Player player, AdminStaffItem item, int ability, int clientRenderDistance) {
        if (level.isClientSide) return;
        int index = Math.max(0, Math.min(StaffType.COUNT - 1, ability));
        StaffSpells.cast(level, player, item.type, index, clientRenderDistance);
        int cooldown = (int) Math.round(item.type.cooldowns[index] * Config.COOLDOWN_MULTIPLIER.get());
        if (cooldown > 0) player.getCooldowns().addCooldown(item, cooldown);
    }
}
