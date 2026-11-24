package com.omnimine.server;

import com.omnimine.Omnimine;
import com.omnimine.item.BedrockPickaxeItem;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Le libraire vend le livre Bedrock Breaker contre un bloc d'emeraude, des son premier niveau (echange garanti). */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class OmniTrades {

    @SubscribeEvent
    public static void onTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) {
            return;
        }
        List<VillagerTrades.ItemListing> novice = event.getTrades().get(1);
        novice.clear();
        novice.add((trader, random) -> new MerchantOffer(new ItemStack(Items.EMERALD_BLOCK),
                BedrockPickaxeItem.breakerBook(), 4, 20, 0.05F));
        novice.add((trader, random) -> new MerchantOffer(new ItemStack(Items.PAPER, 24),
                new ItemStack(Items.EMERALD), 16, 2, 0.05F));
    }

    private OmniTrades() {}
}
