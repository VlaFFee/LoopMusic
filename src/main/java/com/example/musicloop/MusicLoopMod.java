package com.example.musicloop;

import com.example.musicloop.network.NetworkHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MusicLoopMod.MODID)
public class MusicLoopMod {
    public static final String MODID = "musicloop";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MusicLoopMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, LoopConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        // при загрузке игры создаём папку music_loops
        e.enqueueWork(() -> {
            TrackFolder.ensure();
            NetworkHandler.register();
        });
    }
}
