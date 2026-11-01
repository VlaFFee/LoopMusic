package com.example.musicloop.client;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class OggSource implements PcmSource {
    private final ByteBuffer data;
    private final long handle;
    private final int ch, rate;
    private final long frames;

    private OggSource(ByteBuffer data, long handle, int ch, int rate, long frames) {
        this.data = data;
        this.handle = handle;
        this.ch = ch;
        this.rate = rate;
        this.frames = frames;
    }

    static OggSource open(Path p) throws IOException {
        ByteBuffer data;
        try (FileChannel fc = FileChannel.open(p, StandardOpenOption.READ)) {
            data = MemoryUtil.memAlloc((int) fc.size());
            while (data.hasRemaining()) {
                if (fc.read(data) < 0) break;
            }
            data.flip();
        }
        try (MemoryStack st = MemoryStack.stackPush()) {
            IntBuffer err = st.mallocInt(1);
            long h = STBVorbis.stb_vorbis_open_memory(data, err, null);
            if (h == 0L) {
                MemoryUtil.memFree(data);
                throw new IOException("не удалось прочитать OGG (нужен именно Vorbis, код " + err.get(0) + ")");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc(st);
            STBVorbis.stb_vorbis_get_info(h, info);
            int channels = info.channels();
            int rate = info.sample_rate();
            long len = STBVorbis.stb_vorbis_stream_length_in_samples(h);
            if (len <= 0 || channels < 1) {
                STBVorbis.stb_vorbis_close(h);
                MemoryUtil.memFree(data);
                throw new IOException("не удалось определить длину трека");
            }
            return new OggSource(data, h, Math.min(channels, 2), rate, len);
        }
    }

    @Override public int channels() { return ch; }

    @Override public int rate() { return rate; }

    @Override public long frames() { return frames; }

    @Override
    public void seek(long frame) {
        STBVorbis.stb_vorbis_seek(handle, (int) frame);
    }

    @Override
    public int read(ByteBuffer dst, int maxFrames) {
        ShortBuffer sb = dst.asShortBuffer();   // порядок байт берётся у dst (native)
        sb.limit(maxFrames * ch);
        return STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, ch, sb);
    }

    @Override
    public void close() {
        STBVorbis.stb_vorbis_close(handle);
        MemoryUtil.memFree(data);
    }
}
