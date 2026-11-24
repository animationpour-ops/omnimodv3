package com.omnimine.client;

import com.omnimine.Omnimine;
import com.omnimine.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Les objets qui existent deja dans Minecraft (pioche, epee, bottes, casque, plastron, jambieres) gardent le modele et la
 * texture vanilla : on ne fait que les teinter (calque 0). Le calque 1 (motifs) n'est pas teinte.
 */
@Mod.EventBusSubscriber(modid = Omnimine.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    /** Bedrock : gris-bleu sombre (la pioche vanilla en pierre, teintee). */
    public static final int BEDROCK = 0xA4A4BC;
    /** Soleil : orange dore. */
    public static final int SUN = 0xFFA040;
    /** Lune : blanc bleute. */
    public static final int MOON = 0xCCE0FF;
    /** Eclair : bleu electrique. */
    public static final int LIGHTNING = 0x7FC4FF;

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? BEDROCK : 0xFFFFFF, ModItems.BEDROCK_PICKAXE.get());
        event.register((stack, tint) -> tint == 0 ? SUN : 0xFFFFFF, ModItems.SUN_SWORD.get());
        event.register((stack, tint) -> tint == 0 ? MOON : 0xFFFFFF, ModItems.MOON_BOOTS.get());
        event.register((stack, tint) -> tint == 0 ? LIGHTNING : 0xFFFFFF, ModItems.LIGHTNING_HELMET.get(),
                ModItems.LIGHTNING_CHESTPLATE.get(), ModItems.LIGHTNING_LEGGINGS.get());
    }

    private ClientSetup() {}
}
