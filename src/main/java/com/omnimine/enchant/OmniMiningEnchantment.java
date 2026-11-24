package com.omnimine.enchant;

import com.omnimine.item.BedrockPickaxeItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/** Omniminage : permet a la pioche en bedrock de miner les mobs, le ciel, les eclairs et les structures. */
public class OmniMiningEnchantment extends Enchantment {

    public OmniMiningEnchantment() {
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

    /** Seulement sur la pioche en bedrock (enclume, livre...). */
    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() instanceof BedrockPickaxeItem;
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
        return false;
    }
}
