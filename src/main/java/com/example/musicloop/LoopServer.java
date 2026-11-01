package com.example.musicloop;

import com.example.musicloop.network.LoopPacket;
import com.example.musicloop.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Серверное состояние лупа: что играет и когда стартовало (по часам сервера). */
@Mod.EventBusSubscriber(modid = MusicLoopMod.MODID)
public final class LoopServer {
    private static String track;
    private static long startNanos;

    public static synchronized void start(String t) {
        track = t;
        startNanos = System.nanoTime();
    }

    public static synchronized void stop() {
        track = null;
    }

    public static synchronized String current() {
        return track;
    }

    /** Пакет "играть" с общим временем старта (его же получают и зашедшие позже). null, если ничего не играет. */
    public static synchronized LoopPacket playPacket(int fadeMs) {
        if (track == null) return null;
        return new LoopPacket(LoopPacket.PLAY, track, startNanos, System.nanoTime(), fadeMs);
    }

    public static void broadcast(LoopPacket p) {
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), p);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        LoopCommand.register(e.getDispatcher());
    }

    /** Игрок зашёл позже - получает тот же момент старта, клиент сам вычислит позицию в треке. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        LoopPacket p = playPacket(1500);
        if (p != null) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), p);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        stop();
    }

    private LoopServer() {}
}
