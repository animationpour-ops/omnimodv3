package com.omnimine.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Bedrock Breaker : une pioche en diamant ou en netherite qui porte cet enchantement peut miner le bedrock (maintiens
 * le clic gauche dessus) et en rapporte un bloc. C'est ce bloc qui sert a fabriquer la pioche en bedrock.
 */
public class BedrockBreakerEnchantment extends Enchantment {

    public BedrockBreakerEnchantment() {
        super(Enchantment.Rarity.VERY_RARE, EnchantmentCategory.DIGGER, new EquipmentSlot[] {EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int level) {
        return 30;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    /** Seulement sur une pioche en diamant ou en netherite (enclume, livre...). */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem pick
                && (pick.getTier() == Tiers.DIAMOND || pick.getTier() == Tiers.NETHERITE);
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return true;
    }
}
