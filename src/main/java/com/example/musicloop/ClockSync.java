package com.example.musicloop;

import java.util.ArrayDeque;

/**
 * Оценка разницы часов клиент/сервер (как в NTP): offset = серверные_наносекунды - клиентские_наносекунды.
 * Из последних замеров берём тот, где пинг был минимальным - он самый точный.
 * Потокобезопасен: замеры приходят из сетевого потока.
 */
public final class ClockSync {
    private static final long KEEP_NANOS = 40_000_000_000L;
    private static final int MAX_SAMPLES = 16;
    private static final ArrayDeque<long[]> SAMPLES = new ArrayDeque<>();   // {offset, rtt, момент}
    private static volatile long offset;
    private static long lastPing;

    public static long offset() {
        return offset;
    }

    /** t0 - клиентское время отправки пинга, serverNanos - серверное время ответа, t1 - клиентское время получения. */
    public static void onPong(long t0, long serverNanos, long t1) {
        long rtt = t1 - t0;
        if (rtt < 0) return;
        add(serverNanos - (t0 + rtt / 2), rtt);
    }

    /** Первичная оценка по самому пакету "играть" (без учёта задержки сети), пока нет точных замеров. */
    public static void provisional(long offsetEstimate) {
        add(offsetEstimate, 400_000_000L);
    }

    private static synchronized void add(long off, long rtt) {
        long now = System.nanoTime();
        SAMPLES.addLast(new long[]{off, rtt, now});
        while (SAMPLES.size() > MAX_SAMPLES) SAMPLES.removeFirst();
        while (SAMPLES.size() > 1 && now - SAMPLES.peekFirst()[2] > KEEP_NANOS) SAMPLES.removeFirst();
        long[] best = null;
        for (long[] s : SAMPLES) {
            if (best == null || s[1] <= best[1]) best = s;
        }
        offset = best[0];
    }

    /** Пора ли слать очередной пинг: сначала часто (набираем замеры), потом редко. */
    public static synchronized boolean needsPing(long now) {
        long interval = SAMPLES.size() < 8 ? 120_000_000L : 4_000_000_000L;
        if (now - lastPing < interval) return false;
        lastPing = now;
        return true;
    }

    public static synchronized void reset() {
        SAMPLES.clear();
        offset = 0;
        lastPing = 0;
    }

    private ClockSync() {}
}
