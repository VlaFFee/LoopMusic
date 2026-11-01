package com.example.musicloop;

import com.example.musicloop.network.LoopPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * /musicloop play <файл> [секунд_нарастания]
 * /musicloop stop [секунд_затухания]
 * /musicloop list
 */
public final class LoopCommand {
    private static final SuggestionProvider<CommandSourceStack> TRACKS = (ctx, b) ->
            SharedSuggestionProvider.suggest(TrackFolder.list().stream().map(LoopCommand::quote), b);

    private static String quote(String n) {
        if (n.matches("[A-Za-z0-9_.+\\-]+")) return n;
        return "\"" + n.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("musicloop")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("track", StringArgumentType.string())
                                .suggests(TRACKS)
                                .executes(c -> play(c, 2.0f))
                                .then(Commands.argument("fade", FloatArgumentType.floatArg(0f, 60f))
                                        .executes(c -> play(c, FloatArgumentType.getFloat(c, "fade"))))))
                .then(Commands.literal("stop")
                        .executes(c -> stop(c, 4.0f))
                        .then(Commands.argument("seconds", FloatArgumentType.floatArg(0f, 120f))
                                .executes(c -> stop(c, FloatArgumentType.getFloat(c, "seconds")))))
                .then(Commands.literal("list").executes(LoopCommand::list)));
    }

    private static int play(CommandContext<CommandSourceStack> c, float fadeSec) {
        String track = StringArgumentType.getString(c, "track");
        CommandSourceStack src = c.getSource();
        if (!TrackFolder.validName(track)) {
            src.sendFailure(Component.literal("Недопустимое имя файла."));
            return 0;
        }
        boolean found = TrackFolder.resolve(track) != null;
        if (!found && src.getServer().isSingleplayer()) {
            src.sendFailure(Component.literal("Файл '" + track + "' не найден в папке music_loops."));
            return 0;
        }
        LoopServer.start(track);
        LoopServer.broadcast(LoopServer.playPacket(Math.round(fadeSec * 1000)));
        src.sendSuccess(() -> Component.literal("Луп запущен: " + track
                + (found ? "" : " (на сервере файла нет - клиенты берут его из своей папки)")), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> c, float sec) {
        if (LoopServer.current() == null) {
            c.getSource().sendFailure(Component.literal("Сейчас ничего не играет."));
            return 0;
        }
        LoopServer.stop();
        LoopServer.broadcast(new LoopPacket(LoopPacket.STOP, "", 0, 0, Math.round(sec * 1000)));
        c.getSource().sendSuccess(() -> Component.literal("Луп плавно затухает (" + sec + " с)."), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<String> l = TrackFolder.list();
        if (l.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("Папка пуста: " + TrackFolder.dir()), false);
        } else {
            c.getSource().sendSuccess(() -> Component.literal("Треки (" + l.size() + "): " + String.join(", ", l)), false);
        }
        return l.size();
    }

    private LoopCommand() {}
}
