package com.example.musicloop.client;

import com.example.musicloop.LoopConfig;
import com.example.musicloop.MusicLoopMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.openal.ALC10;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Аудио-поток лупа: плавные фейды, приглушение (записка / свёрнутое окно), отдельная громкость лупа,
 * синхронизация, перезапуск при смене контекста OpenAL.
 */
@OnlyIn(Dist.CLIENT)
public final class LoopEngine {
    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    private static Thread thread;
    private static volatile boolean active;
    private static volatile boolean noteOpen;
    private static volatile boolean minimized;

    // только аудио-поток:
    private static final List<Voice> voices = new ArrayList<>();
    private static float duck = 1f, mini = 1f, vol = -1f;
    private static long lastCtx;

    public static boolean isActive() {
        return active;
    }

    /** Вызывается каждый клиентский тик. */
    public static void updateState(boolean noteScreenOpen, boolean windowMinimized) {
        noteOpen = noteScreenOpen;
        minimized = windowMinimized;
    }

    public static void requestPlay(String track, long startServerNanos, int fadeMs) {
        active = true;
        post(() -> doPlay(track, startServerNanos, fadeMs));
    }

    public static void requestStop(int fadeMs) {
        post(() -> doStop(fadeMs));
    }

    public static void stopNow() {
        if (thread == null) return;
        post(() -> doStop(0));
    }

    private static synchronized void post(Runnable r) {
        TASKS.add(r);
        if (thread == null || !thread.isAlive()) {
            thread = new Thread(LoopEngine::run, "MusicLoop-Audio");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private static void run() {
        long last = System.nanoTime();
        while (true) {
            try {
                long now = System.nanoTime();
                float dt = Math.min(0.25f, (now - last) / 1.0e9f);
                last = now;

                Runnable r;
                while ((r = TASKS.poll()) != null) {
                    try { r.run(); } catch (Throwable t) { MusicLoopMod.LOGGER.error("MusicLoop task failed", t); }
                }
                checkContext();
                if (!voices.isEmpty()) tick(dt, System.nanoTime());
                if (TASKS.isEmpty()) active = voices.stream().anyMatch(v -> !v.dying);

                Thread.sleep(voices.isEmpty() ? 100 : 15);
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                MusicLoopMod.LOGGER.error("MusicLoop audio loop error", t);
                try { Thread.sleep(200); } catch (InterruptedException e) { return; }
            }
        }
    }

    private static void checkContext() {
        long ctx = ALC10.alcGetCurrentContext();
        if (ctx == lastCtx) return;
        if (lastCtx != 0 && !voices.isEmpty()) {
            // звуковой движок пересоздан (F3+S, смена устройства...) - поднимаем лупы заново
            List<Voice> old = new ArrayList<>(voices);
            voices.clear();
            for (Voice v : old) {
                v.abandon();
                if (v.dying || ctx == 0) continue;
                try {
                    voices.add(new Voice(v.track, v.startServerNanos, 0.5f));
                } catch (Exception e) {
                    report("Не удалось восстановить луп: " + e.getMessage());
                }
            }
        }
        lastCtx = ctx;
    }

    private static void doPlay(String track, long startServerNanos, int fadeMs) {
        long ctx = ALC10.alcGetCurrentContext();
        if (ctx == 0) {
            report("Звук недоступен (OpenAL не инициализирован).");
            return;
        }
        lastCtx = ctx;
        float fade = Math.max(0.05f, fadeMs / 1000f);
        try {
            Voice nv = new Voice(track, startServerNanos, fade);
            for (Voice v : voices) if (!v.dying) v.fadeOut(fade);   // кроссфейд со старым треком
            voices.add(nv);
            active = true;
        } catch (Exception e) {
            report("Не удалось запустить '" + track + "': " + e.getMessage());
        }
    }

    private static void doStop(int fadeMs) {
        if (fadeMs <= 0) {
            for (Voice v : voices) v.destroy();
            voices.clear();
            active = false;
            return;
        }
        for (Voice v : voices) if (!v.dying) v.fadeOut(fadeMs / 1000f);
    }

    private static void tick(float dt, long now) {
        // приглушение при записке (плавно) и при свёрнутом окне (плавно, сильно)
        float duckTarget = noteOpen ? (float) LoopConfig.duckLevel() : 1f;
        float tau = Math.max(0.05f, (float) LoopConfig.duckSeconds() / 3f);
        duck += (duckTarget - duck) * (1f - (float) Math.exp(-dt / tau));

        float miniTarget = minimized ? (float) LoopConfig.minimizedLevel() : 1f;
        mini += (miniTarget - mini) * (1f - (float) Math.exp(-dt / 0.35f));

        // собственная громкость лупа (ползунок в настройках звука), без привязки к ползунку "Музыка"
        float volTarget = (float) LoopConfig.loopVolume();
        vol = vol < 0f ? volTarget : vol + (volTarget - vol) * (1f - (float) Math.exp(-dt / 0.06f));

        float total = duck * mini * vol;

        List<Voice> add = null;
        Iterator<Voice> it = voices.iterator();
        while (it.hasNext()) {
            Voice v = it.next();
            boolean keep;
            try {
                keep = v.update(dt, now, total);
                if (keep && v.needsResync) {
                    v.needsResync = false;
                    Voice nv = new Voice(v.track, v.startServerNanos, 0.2f);   // перезапуск с правильной позиции
                    v.fadeOut(0.2f);
                    if (add == null) add = new ArrayList<>();
                    add.add(nv);
                }
            } catch (Throwable t) {
                MusicLoopMod.LOGGER.error("MusicLoop voice error", t);
                keep = false;
            }
            if (!keep) {
                v.destroy();
                it.remove();
            }
        }
        if (add != null) voices.addAll(add);
    }

    private static void report(String msg) {
        MusicLoopMod.LOGGER.warn(msg);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.displayClientMessage(Component.literal("§c[MusicLoop] " + msg), false);
        });
    }

    private LoopEngine() {}
}
