package com.example.musicloop.network;

import com.example.musicloop.MusicLoopMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MusicLoopMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, LoopPacket.class,
                LoopPacket::encode, LoopPacket::decode, LoopPacket::handle);
        CHANNEL.registerMessage(1, SyncPingPacket.class,
                SyncPingPacket::encode, SyncPingPacket::decode, SyncPingPacket::handle);
        CHANNEL.registerMessage(2, SyncPongPacket.class,
                SyncPongPacket::encode, SyncPongPacket::decode, SyncPongPacket::handle);
    }

    private NetworkHandler() {}
}
