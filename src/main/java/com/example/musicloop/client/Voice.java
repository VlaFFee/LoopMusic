package com.example.musicloop.client;

import com.example.musicloop.ClockSync;
import com.example.musicloop.TrackFolder;
import org.lwjgl.openal.AL10;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.concurrent.locks.LockSupport;

/**
 * Один играющий трек: свой OpenAL-источник, потоковая подгрузка кусками, бесшовный луп.
 *
 * Синхронизация: позиция трека считается от серверного времени старта (startServerNanos), переведённого
 * в клиентское через ClockSync. Реальная позиция сверяется с ожидаемой; небольшой дрейф выравнивается
 * микроскопическим изменением скорости (0.3-0.6%), большой - пересинхронизацией.
 *
 * Все методы вызываются ТОЛЬКО из аудио-потока.
 */
final class Voice {
    private static final int CHUNK = 8192;      // кадров в буфере (~0.19 с при 44.1 кГц)
    private static final int NBUF = 4;
    private static final int AL_SAMPLE_OFFSET = 0x1025;

    final String track;
    final long startServerNanos;
    boolean dying;
    boolean needsResync;

    private final PcmSource pcm;
    private final int rate, ch, fmt;
    private final long total;
    private final ByteBuffer scratch;
    private final int source;
    private final int[] bufs = new int[NBUF];
    private final ArrayDeque<Integer> queuedFrames = new ArrayDeque<>();

    private long readPos;       // следующий кадр для декодирования (в пределах трека)
    private long anchorAbs;     // абсолютный кадр, с которого началась очередь
    private long playedFrames;  // кадров уже проиграно (буферы сняты с очереди)

    private float fade, fadeTarget = 1f, fadeRate = 1f;
    private float age, driftTimer;
    private double ema;
    private boolean emaInit;
    private int correcting;     // -1 = замедляемся, +1 = ускоряемся, 0 = идём ровно
    private int bigDrift;
    private boolean destroyed;

    Voice(String track, long startServerNanos, float fadeInSec) throws IOException {
        this.track = track;
        this.startServerNanos = startServerNanos;
        Path file = TrackFolder.resolve(track);
        if (file == null) throw new IOException("файл не найден в папке music_loops");
        this.pcm = PcmSource.open(file);
        int src = 0;
        try {
            this.rate = pcm.rate();
            this.ch = pcm.channels();
            this.total = pcm.frames();
            if (total <= 0 || ch < 1 || ch > 2 || rate <= 0) throw new IOException("пустой или неподдерживаемый файл");
            this.fmt = ch == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
            this.scratch = ByteBuffer.allocateDirect(CHUNK * ch * 2).order(ByteOrder.nativeOrder());

            AL10.alGetError();
            src = AL10.alGenSources();
            AL10.alGenBuffers(bufs);
            checkError("создание источника");
            this.source = src;

            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcef(source, AL10.AL_GAIN, 0f);

            fade = 0f;
            fadeTarget = 1f;
            fadeRate = 1f / Math.max(0.02f, fadeInSec);

            // Стартуем с небольшим запасом вперёд (120 мс): пока декодируем первые куски, время идёт,
            // и play() вызывается ровно тогда, когда ожидаемая позиция дошла до начала очереди.
            anchorAbs = Math.max(0L, expectedAbs(System.nanoTime()) + (long) (0.12 * rate));
            readPos = anchorAbs % total;
            pcm.seek(readPos);
            for (int b : bufs) fill(b);
            checkError("загрузка буферов");

            long waitFrames = anchorAbs - expectedAbs(System.nanoTime());
            if (waitFrames > 0) {
                LockSupport.parkNanos(Math.min(300_000_000L, (long) (waitFrames * 1.0e9 / rate)));
            }
            AL10.alSourcePlay(source);
            checkError("запуск");
        } catch (IOException | RuntimeException e) {
            destroyPartial(src);
            throw e;
        }
    }

    private void destroyPartial(int src) {
        try {
            if (src != 0) {
                AL10.alSourceStop(src);
                AL10.alSourcei(src, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(src);
            }
            AL10.alDeleteBuffers(bufs);
        } catch (Throwable ignored) {}
        try { pcm.close(); } catch (Throwable ignored) {}
        AL10.alGetError();
        destroyed = true;
    }

    private static void checkError(String what) throws IOException {
        int e = AL10.alGetError();
        if (e != AL10.AL_NO_ERROR) throw new IOException("ошибка OpenAL (" + what + "): 0x" + Integer.toHexString(e));
    }

    /** Сколько кадров трека должно быть проиграно к моменту nanos (клиентское время). */
    private long expectedAbs(long nanos) {
        long serverNow = nanos + ClockSync.offset();
        return (long) ((serverNow - startServerNanos) * (double) rate / 1.0e9);
    }

    /** Декодирует кусок (с зацикливанием по краю трека) и ставит его в очередь. */
    private void fill(int buf) {
        scratch.clear();
        int got = 0, guard = 0;
        while (got < CHUNK && guard++ < 8) {
            if (readPos >= total) {
                readPos = 0;
                pcm.seek(0);
            }
            int want = (int) Math.min(CHUNK - got, total - readPos);
            scratch.position(got * ch * 2);
            int n = pcm.read(scratch, want);
            if (n <= 0) {
                readPos = total;   // файл кончился раньше заявленного - заворачиваем
                continue;
            }
            got += n;
            readPos += n;
        }
        if (got == 0) return;
        scratch.position(0);
        scratch.limit(got * ch * 2);
        AL10.alBufferData(buf, fmt, scratch, rate);
        AL10.alSourceQueueBuffers(source, buf);
        queuedFrames.add(got);
    }

    void fadeOut(float seconds) {
        dying = true;
        fadeTarget = 0f;
        fadeRate = Math.max(0.0001f, fade / Math.max(0.02f, seconds));
    }

    /** @return false, если голос нужно удалить */
    boolean update(float dt, long nowNanos, float volume) {
        if (fade < fadeTarget) fade = Math.min(fadeTarget, fade + fadeRate * dt);
        else if (fade > fadeTarget) fade = Math.max(fadeTarget, fade - fadeRate * dt);
        if (dying && fade <= 0f) return false;

        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int id = AL10.alSourceUnqueueBuffers(source);
            Integer f = queuedFrames.poll();
            if (f != null) playedFrames += f;
            fill(id);
        }
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
            AL10.alSourcePlay(source);   // восстановление после пропуска (underrun)
        }

        age += dt;
        driftTimer += dt;
        if (driftTimer >= 0.25f && age > 0.8f) {
            driftTimer = 0f;
            int off = AL10.alGetSourcei(source, AL_SAMPLE_OFFSET);
            long actual = anchorAbs + playedFrames + off;
            double drift = (actual - expectedAbs(nowNanos)) / (double) rate;   // сек; >0 = мы впереди

            // большой рассинхрон два замера подряд -> перезапуск с правильной позиции
            if (Math.abs(drift) > 0.15) {
                if (++bigDrift >= 2 && !dying) needsResync = true;
            } else {
                bigDrift = 0;
            }

            // OpenAL отдаёт позицию с шагом ~20 мс, поэтому сглаживаем и корректируем с гистерезисом,
            // чтобы скорость не "дрожала": один из режимов держится секундами, пока ошибка не уйдёт.
            ema = emaInit ? ema * 0.8 + drift * 0.2 : drift;
            emaInit = true;
            if (correcting == 0) {
                if (ema > 0.025) correcting = -1;
                else if (ema < -0.025) correcting = 1;
            } else if (correcting == -1 && ema < 0.004) {
                correcting = 0;
            } else if (correcting == 1 && ema > -0.004) {
                correcting = 0;
            }
            float mag = Math.abs(ema) > 0.12 ? 0.006f : 0.003f;
            AL10.alSourcef(source, AL10.AL_PITCH, 1f + correcting * mag);
        }

        float gain = (float) Math.sin(fade * Math.PI / 2.0) * volume;   // равномерная по мощности кривая
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
        AL10.alGetError();
        return true;
    }

    void destroy() {
        if (destroyed) return;
        destroyed = true;
        try {
            AL10.alSourceStop(source);
            AL10.alSourcei(source, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(bufs);
        } catch (Throwable ignored) {}
        try { pcm.close(); } catch (Throwable ignored) {}
        AL10.alGetError();
    }

    /** Контекст OpenAL пересоздан (перезагрузка звука) - AL-объекты уже мертвы, освобождаем только файл. */
    void abandon() {
        if (destroyed) return;
        destroyed = true;
        try { pcm.close(); } catch (Throwable ignored) {}
    }
}
