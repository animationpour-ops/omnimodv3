package com.omnimine.server;

import com.omnimine.Omnimine;
import com.omnimine.data.MobKind;
import com.omnimine.item.BedrockPickaxeItem;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Evenements communs : avec la pioche enchantee, un clic gauche ne frappe pas un mob minable et ne casse pas de bloc accroupi. */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class OmniEvents {

    /** Les mobs minables ne se frappent pas avec la pioche : on les mine. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (BedrockPickaxeItem.holdsOmni(event.getEntity()) && MobKind.of(event.getTarget()) != null) {
            event.setCanceled(true);
        }
    }

    /** Accroupi avec la pioche : le clic sur un bloc sert a miner les structures, pas a casser le bloc. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().isShiftKeyDown() && BedrockPickaxeItem.holdsOmni(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private OmniEvents() {}
}
