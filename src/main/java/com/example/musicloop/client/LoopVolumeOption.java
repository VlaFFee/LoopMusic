package com.example.musicloop.client;

import com.example.musicloop.LoopConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Ползунок "Музыка лупа" в Настройки -> Звук (оформлен так же, как ванильные). */
@OnlyIn(Dist.CLIENT)
public final class LoopVolumeOption {
    public static OptionInstance<Double> create() {
        return new OptionInstance<>(
                "soundCategory.musicloop",
                OptionInstance.noTooltip(),
                (caption, value) -> value == 0.0
                        ? Component.translatable("options.generic_value", caption, CommonComponents.OPTION_OFF)
                        : Component.translatable("options.percent_value", caption, (int) (value * 100.0)),
                OptionInstance.UnitDouble.INSTANCE,
                LoopConfig.loopVolume(),
                LoopConfig::setLoopVolume);
    }

    private LoopVolumeOption() {}
}
