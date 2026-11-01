package com.example.musicloop.client;

import com.example.musicloop.ClockSync;
import com.example.musicloop.LoopConfig;
import com.example.musicloop.MusicLoopMod;
import com.example.musicloop.TrackFolder;
import com.example.musicloop.network.NetworkHandler;
import com.example.musicloop.network.SyncPingPacket;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

@Mod.EventBusSubscriber(modid = MusicLoopMod.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    /** Экраны мода записок: при них луп приглушается (определяем по имени класса - без жёсткой зависимости). */
    private static final Set<String> DUCK_SCREENS = Set.of(
            "com.example.notesmod.client.NoteScreen",
            "com.example.notesmod.client.PhotoScreen",
            "com.example.notesmod.client.PhotoPickerScreen");

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        Screen s = mc.screen;
        boolean noteScreen = s != null && DUCK_SCREENS.contains(s.getClass().getName());

        boolean minimized = false;
        try {
            long w = mc.getWindow().getWindow();
            minimized = GLFW.glfwGetWindowAttrib(w, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        } catch (Throwable ignored) {}
        if (!minimized && !mc.isWindowActive() && LoopConfig.duckWhenUnfocused()) minimized = true;

        LoopEngine.updateState(noteScreen, minimized);

        // пока играет луп - регулярно уточняем разницу часов с сервером
        if (LoopEngine.isActive() && mc.getConnection() != null) {
            long now = System.nanoTime();
            if (ClockSync.needsPing(now)) {
                NetworkHandler.CHANNEL.sendToServer(new SyncPingPacket(now));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        LoopEngine.stopNow();
        ClockSync.reset();
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent e) {
        if (!LoopEngine.isActive() || !LoopConfig.suppressVanilla()) return;
        if (e.getSound() != null && e.getSound().getSource() == SoundSource.MUSIC) {
            e.setSound(null);   // пока играет луп - ванильную музыку не запускаем
        }
    }

    /** Добавляет ползунок "Музыка лупа" в конец списка настроек звука. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post e) {
        if (!(e.getScreen() instanceof SoundOptionsScreen)) return;
        for (GuiEventListener l : e.getListenersList()) {
            if (l instanceof OptionsList list) {
                list.addBig(LoopVolumeOption.create());
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing e) {
        if (e.getScreen() instanceof SoundOptionsScreen) LoopConfig.saveIfDirty();
    }

    /** /musicloopfolder - открыть папку с музыкой. */
    @SubscribeEvent
    public static void onClientCommands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("musicloopfolder").executes(c -> {
            TrackFolder.ensure();
            Util.getPlatform().openFile(TrackFolder.dir().toFile());
            c.getSource().sendSuccess(() -> Component.literal("Папка с музыкой: " + TrackFolder.dir()), false);
            return 1;
        }));
    }

    private ClientEvents() {}
}
