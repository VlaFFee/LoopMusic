package com.example.musicloop;

import net.minecraftforge.common.ForgeConfigSpec;

/** Клиентский конфиг: config/musicloop-client.toml */
public final class LoopConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.DoubleValue LOOP_VOLUME;
    private static final ForgeConfigSpec.DoubleValue DUCK_LEVEL;
    private static final ForgeConfigSpec.DoubleValue DUCK_SECONDS;
    private static final ForgeConfigSpec.BooleanValue SUPPRESS_VANILLA;
    private static final ForgeConfigSpec.DoubleValue MINIMIZED_LEVEL;
    private static final ForgeConfigSpec.BooleanValue DUCK_UNFOCUSED;
    private static boolean dirty;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        LOOP_VOLUME = b.comment("Громкость лупа (0..1). Меняется ползунком в Настройки -> Звук (Музыка лупа).")
                .defineInRange("loopVolume", 1.0, 0.0, 1.0);
        DUCK_LEVEL = b.comment("Громкость лупа (0..1), пока открыта записка/фото. 1 = не приглушать.")
                .defineInRange("duckLevel", 0.5, 0.0, 1.0);
        DUCK_SECONDS = b.comment("За сколько секунд музыка приглушается и возвращается обратно.")
                .defineInRange("duckFadeSeconds", 0.8, 0.05, 10.0);
        SUPPRESS_VANILLA = b.comment("Глушить ванильную музыку, пока играет луп.")
                .define("suppressVanillaMusic", true);
        MINIMIZED_LEVEL = b.comment("Громкость лупа (0..1), когда окно игры свёрнуто. Очень тихо, но слышно.")
                .defineInRange("minimizedLevel", 0.07, 0.0, 1.0);
        DUCK_UNFOCUSED = b.comment("Так же приглушать, если окно не свёрнуто, но потеряло фокус (alt-tab).")
                .define("duckWhenUnfocused", false);
        SPEC = b.build();
    }

    public static double loopVolume() {
        try { return LOOP_VOLUME.get(); } catch (Throwable t) { return 1.0; }
    }

    /** Меняет громкость в памяти (на диск пишется при закрытии экрана звука). */
    public static void setLoopVolume(double v) {
        try {
            LOOP_VOLUME.set(Math.max(0.0, Math.min(1.0, v)));
            dirty = true;
        } catch (Throwable ignored) {}
    }

    public static void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        try { SPEC.save(); } catch (Throwable ignored) {}
    }

    public static double duckLevel() {
        try { return DUCK_LEVEL.get(); } catch (Throwable t) { return 0.5; }
    }

    public static double duckSeconds() {
        try { return DUCK_SECONDS.get(); } catch (Throwable t) { return 0.8; }
    }

    public static boolean suppressVanilla() {
        try { return SUPPRESS_VANILLA.get(); } catch (Throwable t) { return true; }
    }

    public static double minimizedLevel() {
        try { return MINIMIZED_LEVEL.get(); } catch (Throwable t) { return 0.07; }
    }

    public static boolean duckWhenUnfocused() {
        try { return DUCK_UNFOCUSED.get(); } catch (Throwable t) { return false; }
    }

    private LoopConfig() {}
}
