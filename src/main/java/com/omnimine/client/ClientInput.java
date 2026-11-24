package com.omnimine.client;

import com.omnimine.Omnimine;
import com.omnimine.data.MobKind;
import com.omnimine.item.BedrockPickaxeItem;
import com.omnimine.item.LunarBootsItem;
import com.omnimine.net.JumpPacket;
import com.omnimine.net.MineHoldPacket;
import com.omnimine.net.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Entrees du joueur : maintien du clic de minage (pioche en bedrock) et double saut lunaire (bottes). */
@Mod.EventBusSubscriber(modid = Omnimine.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean wasJump;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            wasJump = false;
            return;
        }

        // 1. minage universel : tant que le clic gauche est maintenu avec la pioche en bedrock (ou une pioche Bedrock
        //    Breaker face au bedrock), on le dit au serveur (accroupi : meme face a un bloc, c'est la structure qu'on
        //    mine ; sinon seulement si on ne vise pas un bloc, sauf le bedrock qui se mine comme un bloc)
        if (mc.screen == null && mc.options.keyAttack.isDown() && BedrockPickaxeItem.canMineSpecial(p.getMainHandItem())) {
            HitResult hit = mc.hitResult;
            boolean onBlock = hit != null && hit.getType() == HitResult.Type.BLOCK;
            boolean onBedrock = onBlock && hit instanceof BlockHitResult blockHit
                    && mc.level.getBlockState(blockHit.getBlockPos()).is(Blocks.BEDROCK);
            if (p.isShiftKeyDown() || !onBlock || onBedrock) {
                ModNetwork.CHANNEL.sendToServer(new MineHoldPacket());
            }
        }

        // 2. double saut lunaire : une nouvelle pression sur saut pendant qu'on est en l'air avec les bottes
        boolean jump = mc.screen == null && mc.options.keyJump.isDown();
        if (jump && !wasJump && !p.onGround() && !p.isInWaterOrBubble() && !p.isInLava() && !p.getAbilities().flying
                && !p.onClimbable() && !p.isPassenger() && LunarBootsItem.wornBy(p)) {
            ModNetwork.CHANNEL.sendToServer(new JumpPacket());
        }
        wasJump = jump;
    }

    /** Un clic gauche sur un mob minable ne le frappe pas : on le mine (le maintien est envoye chaque tick). */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !BedrockPickaxeItem.holdsOmni(p)) {
            return;
        }
        if (mc.hitResult instanceof EntityHitResult entityHit && MobKind.of(entityHit.getEntity()) != null) {
            event.setCanceled(true);
        }
    }

    private ClientInput() {}
}
