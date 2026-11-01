package com.example.musicloop.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

final class WavSource implements PcmSource {
    private final File file;
    private final AudioFormat target;
    private final int ch, rate, frameSize;
    private final long frames;
    private AudioInputStream in;
    private byte[] tmp = new byte[0];

    private WavSource(File file, AudioFormat target, int ch, int rate, long frames) {
        this.file = file;
        this.target = target;
        this.ch = ch;
        this.rate = rate;
        this.frames = frames;
        this.frameSize = ch * 2;
    }

    static WavSource open(Path p) throws IOException {
        File f = p.toFile();
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(f)) {
            AudioFormat fmt = raw.getFormat();
            long fl = raw.getFrameLength();
            int ch = fmt.getChannels();
            if (fl <= 0 || fmt.getSampleRate() <= 0) throw new IOException("не удалось определить длину WAV");
            if (ch < 1 || ch > 2) throw new IOException("поддерживается моно и стерео WAV");
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    fmt.getSampleRate(), 16, ch, ch * 2, fmt.getSampleRate(), false);
            WavSource w = new WavSource(f, target, ch, (int) fmt.getSampleRate(), fl);
            w.reopen(0);
            return w;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("неподдерживаемый WAV: " + e.getMessage());
        }
    }

    private void reopen(long skipFrames) {
        closeStream();
        try {
            AudioInputStream raw = AudioSystem.getAudioInputStream(file);
            in = AudioSystem.getAudioInputStream(target, raw);
            long toSkip = skipFrames * frameSize;
            byte[] junk = null;
            while (toSkip > 0) {
                long s = in.skip(toSkip);
                if (s <= 0) {
                    if (junk == null) junk = new byte[8192];
                    int r = in.read(junk, 0, (int) Math.min(junk.length, toSkip));
                    if (r < 0) break;
                    s = r;
                }
                toSkip -= s;
            }
        } catch (Exception e) {
            in = null;
        }
    }

    private void closeStream() {
        if (in != null) {
            try { in.close(); } catch (IOException ignored) {}
            in = null;
        }
    }

    @Override public int channels() { return ch; }

    @Override public int rate() { return rate; }

    @Override public long frames() { return frames; }

    @Override
    public void seek(long frame) {
        reopen(frame);
    }

    @Override
    public int read(ByteBuffer dst, int maxFrames) {
        if (in == null) return 0;
        int want = maxFrames * frameSize;
        if (tmp.length < want) tmp = new byte[want];
        int got = 0;
        try {
            while (got < want) {
                int r = in.read(tmp, got, want - got);
                if (r < 0) break;
                got += r;
            }
        } catch (IOException e) {
            // отдаём что успели
        }
        int n = got / frameSize;
        ByteBuffer d = dst.duplicate();
        d.put(tmp, 0, n * frameSize);
        return n;
    }

    @Override
    public void close() {
        closeStream();
    }
}
