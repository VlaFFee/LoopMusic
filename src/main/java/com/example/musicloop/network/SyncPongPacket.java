package com.example.musicloop.network;

import com.example.musicloop.ClockSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Сервер -> клиент: серверное время в момент ответа. */
public record SyncPongPacket(long clientNanos, long serverNanos) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(clientNanos);
        buf.writeLong(serverNanos);
    }

    public static SyncPongPacket decode(FriendlyByteBuf buf) {
        return new SyncPongPacket(buf.readLong(), buf.readLong());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        long t1 = System.nanoTime();   // сетевой поток: момент реального прихода пакета
        ClockSync.onPong(clientNanos, serverNanos, t1);
        ctx.get().setPacketHandled(true);
    }
}
