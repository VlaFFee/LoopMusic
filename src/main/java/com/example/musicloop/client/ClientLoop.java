package com.example.musicloop.client;

import com.example.musicloop.ClockSync;
import com.example.musicloop.LoopConfig;
import com.example.musicloop.network.LoopPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientLoop {
    /** recvNanos - момент, когда пакет реально пришёл (сетевой поток), а не когда до него дошёл главный поток. */
    public static void handle(LoopPacket p, long recvNanos) {
        if (p.action() == LoopPacket.PLAY) {
            ClockSync.provisional(p.serverNowNanos() - recvNanos);
            if (LoopConfig.suppressVanilla()) Minecraft.getInstance().getMusicManager().stopPlaying();
            LoopEngine.requestPlay(p.track(), p.startServerNanos(), p.fadeMs());
        } else {
            LoopEngine.requestStop(p.fadeMs());
        }
    }

    private ClientLoop() {}
}
