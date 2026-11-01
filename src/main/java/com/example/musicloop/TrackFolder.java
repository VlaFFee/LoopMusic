package com.example.musicloop;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Папка с музыкой: <папка игры>/music_loops */
public final class TrackFolder {

    public static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("music_loops");
    }

    public static void ensure() {
        try {
            Path d = dir();
            boolean fresh = !Files.exists(d);
            Files.createDirectories(d);
            if (fresh) {
                Files.writeString(d.resolve("README.txt"),
                        "Положи сюда музыку в формате .ogg (Vorbis) или .wav и запусти в игре:\n"
                                + "/musicloop play имя_файла.ogg\n"
                                + "Остановить плавно: /musicloop stop\n"
                                + "Если у тебя mp3 - конвертируй в ogg (например, ffmpeg -i in.mp3 -c:a libvorbis -q:a 5 out.ogg).\n",
                        StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {}
    }

    public static boolean isAudio(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".ogg") || l.endsWith(".wav");
    }

    public static boolean validName(String n) {
        return n != null && !n.isEmpty() && n.length() <= 200
                && n.indexOf('/') < 0 && n.indexOf('\\') < 0
                && n.indexOf(':') < 0 && !n.contains("..");
    }

    public static List<String> list() {
        try (Stream<Path> s = Files.list(dir())) {
            return s.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(TrackFolder::isAudio)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Ищет файл по имени (можно без расширения). null, если не найден. */
    public static Path resolve(String name) {
        if (!validName(name)) return null;
        Path root = dir().normalize();
        for (String cand : new String[]{name, name + ".ogg", name + ".wav"}) {
            if (!isAudio(cand)) continue;
            Path p = root.resolve(cand).normalize();
            if (p.startsWith(root) && Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private TrackFolder() {}
}
