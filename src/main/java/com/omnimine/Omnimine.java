package com.omnimine;

import com.mojang.logging.LogUtils;
import com.omnimine.net.ModNetwork;
import com.omnimine.registry.ModEnchantments;
import com.omnimine.registry.ModItems;
import com.omnimine.registry.ModSounds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Omnimine : Minecraft mais tout se mine (mobs, soleil, lune, eclairs, structures). */
@Mod(Omnimine.MODID)
public class Omnimine {
    public static final String MODID = "omnimine";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Omnimine() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        ModSounds.SOUNDS.register(bus);
        ModEnchantments.ENCHANTMENTS.register(bus);
        bus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, OmniConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }
}
