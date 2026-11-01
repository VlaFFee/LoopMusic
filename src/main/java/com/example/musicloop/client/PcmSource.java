package com.example.musicloop.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Locale;

/** Источник 16-bit PCM с возможностью перемотки (для лупа и точной синхронизации). */
interface PcmSource {
    int channels();

    int rate();

    long frames();

    /** Перейти к кадру (frame). */
    void seek(long frame);

    /** Записывает до maxFrames кадров в dst начиная с dst.position() (позицию НЕ двигает). Возвращает число кадров. */
    int read(ByteBuffer dst, int maxFrames);

    void close();

    static PcmSource open(Path p) throws IOException {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".ogg")) return OggSource.open(p);
        if (n.endsWith(".wav")) return WavSource.open(p);
        throw new IOException("поддерживаются только .ogg и .wav");
    }
}
