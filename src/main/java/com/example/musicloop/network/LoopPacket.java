package com.example.musicloop.network;

import com.example.musicloop.client.ClientLoop;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент.
 * PLAY: играть track. startServerNanos - серверное время старта (общая точка отсчёта для всех клиентов),
 *       serverNowNanos - серверное время отправки (для первичной оценки часов).
 * STOP: плавно затухнуть за fadeMs.
 */
public record LoopPacket(byte action, String track, long startServerNanos, long serverNowNanos, int fadeMs) {
    public static final byte PLAY = 0, STOP = 1;

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeUtf(track, 256);
        buf.writeLong(startServerNanos);
        buf.writeLong(serverNowNanos);
        buf.writeInt(fadeMs);
    }

    public static LoopPacket decode(FriendlyByteBuf buf) {
        return new LoopPacket(buf.readByte(), buf.readUtf(256), buf.readLong(), buf.readLong(), buf.readInt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // Эта часть выполняется в сетевом потоке: время получения фиксируем сразу,
        // не дожидаясь главного потока (он может быть занят загрузкой мира).
        long recv = System.nanoTime();
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientLoop.handle(this, recv)));
        c.setPacketHandled(true);
    }
}
