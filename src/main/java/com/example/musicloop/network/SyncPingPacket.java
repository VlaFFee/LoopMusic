package com.example.musicloop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Клиент -> сервер: "который час?" */
public record SyncPingPacket(long clientNanos) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(clientNanos);
    }

    public static SyncPingPacket decode(FriendlyByteBuf buf) {
        return new SyncPingPacket(buf.readLong());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer p = c.getSender();
        if (p != null) {
            // отвечаем сразу из сетевого потока - без ожидания тика сервера
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new SyncPongPacket(clientNanos, System.nanoTime()));
        }
        c.setPacketHandled(true);
    }
}
