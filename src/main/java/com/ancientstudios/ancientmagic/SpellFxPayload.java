package com.ancientstudios.ancientmagic;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Server -> every nearby client: "play spell visuals here". Keeps visuals identical for all players. */
public record SpellFxPayload(int spell, Vec3 from, Vec3 to, float scale) implements CustomPacketPayload {
    public static final Type<SpellFxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AncientMagic.MODID, "spell_fx"));

    public static final StreamCodec<FriendlyByteBuf, SpellFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.spell);
                buf.writeFloat(p.scale);
                buf.writeDouble(p.from.x); buf.writeDouble(p.from.y); buf.writeDouble(p.from.z);
                buf.writeDouble(p.to.x); buf.writeDouble(p.to.y); buf.writeDouble(p.to.z);
            },
            buf -> {
                int spell = buf.readVarInt();
                float scale = buf.readFloat();
                Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                Vec3 to = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                return new SpellFxPayload(spell, from, to, scale);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /**
     * Hook this to the MOD event bus: modEventBus.addListener(SpellFxPayload::register);
     * Registers every payload of the mod: spell visuals (server -> client) and cast requests (client -> server).
     * Protocol version bumped when cast requests gained a client render-distance field.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToClient(TYPE, CODEC, (payload, ctx) -> ctx.enqueueWork(() -> ClientHandler.handle(payload)));
        registrar.playToServer(CastSpellPayload.TYPE, CastSpellPayload.CODEC, CastSpellPayload::handle);
    }

    public static void send(ServerLevel level, int spell, Vec3 from, Vec3 to) {
        double distance = from.distanceTo(to);
        float impactScale = (float) Math.min(5.0, 1.0 + distance / 48.0);
        double packetRange = Math.max(64.0, level.getServer().getPlayerList().getViewDistance() * 16.0);
        PacketDistributor.sendToPlayersNear(level, null, to.x, to.y, to.z, packetRange,
                new SpellFxPayload(spell, from, to, impactScale));
    }

    /** Separate class so dedicated servers never load client-only code. */
    private static final class ClientHandler {
        static void handle(SpellFxPayload p) { AncientFx.play(p.spell, p.from, p.to, p.scale); }
    }
}
