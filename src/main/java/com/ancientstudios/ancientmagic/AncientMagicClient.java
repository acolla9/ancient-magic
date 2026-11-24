// FILE: src/main/java/com/ancientstudios/ancientmagic/AncientMagicClient.java   (REPLACES the existing file)
// Changes: registers the CLIENT config and initialises SkyEffects.
package com.ancientstudios.ancientmagic;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = AncientMagic.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = AncientMagic.MODID, value = Dist.CLIENT)
public class AncientMagicClient {
    public AncientMagicClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // Client-only visuals + comfort settings (config/ancientmagic-client.toml). Shows up in the same config screen.
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AncientMagic.LOGGER.info("HELLO FROM CLIENT SETUP");
        AncientMagic.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        StaffCastClient.registerAnimations(event);
        // Veil post-processing hook for the impact frames (uniform upload each frame).
        event.enqueueWork(ImpactFrames::init);
        // Veil post-processing hook for the Blood Moon / Heaven's Gate sky + fog grade.
        event.enqueueWork(SkyEffects::init);
        // Veil bloom glow pipeline (fed by SkyEffects + ImpactFrames pulses).
        event.enqueueWork(BloomFx::init);
    }
}