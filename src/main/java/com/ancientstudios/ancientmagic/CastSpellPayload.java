package com.ancientstudios.ancientmagic;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: "cast ability N of the staff I am holding".
 * Replaces the old chat-command cast. Everything is re-validated on the server; the packet is never trusted.
 */
public record CastSpellPayload(int staff, int ability, int clientRenderDistance) implements CustomPacketPayload {
    public static final Type<CastSpellPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "cast_spell"));

    public static final StreamCodec<FriendlyByteBuf, CastSpellPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.staff);
                buf.writeVarInt(p.ability);
                buf.writeVarInt(p.clientRenderDistance);
            },
            buf -> new CastSpellPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    public CastSpellPayload(int staff, int ability) { this(staff, ability, 0); }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Runs on the server thread (NeoForge's default for play payload handlers). */
    public static void handle(CastSpellPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            tryCast(player, payload.staff, payload.ability, false, payload.clientRenderDistance);
        }
    }

    /**
     * Validates and performs a cast.
     *
     * @param staffOrdinal   {@link StaffType#ordinal()} of the staff that must be in hand, or -1 for "whichever staff is held"
     * @param ability        0-based ability index
     * @param skipPermission true for the admin command, which has its own permission check
     * @return true if the spell was cast
     */
    public static boolean tryCast(ServerPlayer player, int staffOrdinal, int ability, boolean skipPermission) {
        return tryCast(player, staffOrdinal, ability, skipPermission, 0);
    }

    public static boolean tryCast(ServerPlayer player, int staffOrdinal, int ability, boolean skipPermission, int clientRenderDistance) {
        if (ability < 0 || ability >= StaffType.COUNT) return false;
        if (staffOrdinal >= StaffType.values().length) return false;
        if (!player.isAlive() || player.isSpectator()) return false;

        AdminStaffItem staff = findStaff(player, staffOrdinal);
        if (staff == null) { deny(player, "message.ancientmagic.no_staff_held"); return false; }
        if (player.getCooldowns().isOnCooldown(staff)) { deny(player, "message.ancientmagic.cooldown"); return false; }

        StaffType type = staff.type();
        int level = Config.CAST_PERMISSION_LEVEL.get();
        if (!skipPermission && level > 0 && !player.createCommandSourceStack().hasPermission(level)) {
            deny(player, "message.ancientmagic.no_permission");
            return false;
        }
        if (Config.isAbilityDisabled(type, ability)) {
            deny(player, "message.ancientmagic.ability_disabled");
            return false;
        }
        if (Config.isDimensionDisabled(player.level().dimension().location().toString())) {
            deny(player, "message.ancientmagic.dimension_disabled");
            return false;
        }

        AdminStaffItem.cast(player.level(), player, staff, ability, clientRenderDistance);
        return true;
    }

    private static AdminStaffItem findStaff(ServerPlayer player, int staffOrdinal) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof AdminStaffItem item && (staffOrdinal < 0 || item.type().ordinal() == staffOrdinal)) {
                return item;
            }
        }
        return null;
    }

    private static void deny(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
