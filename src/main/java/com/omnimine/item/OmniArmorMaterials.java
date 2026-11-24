package com.omnimine.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Materiaux des armures : eclair (casque, plastron, jambieres) et lune (bottes). Le dessin porte est genere (ArmorTextures). */
public enum OmniArmorMaterials implements ArmorMaterial {
    LIGHTNING("omnimine:lightning", 3200, 4, 9, 7, 4, 3.0F, 0.2F),
    MOON("omnimine:moon", 3200, 4, 9, 7, 5, 3.0F, 0.1F);

    private final String name;
    private final int durability;
    private final int helmet;
    private final int chest;
    private final int legs;
    private final int boots;
    private final float toughness;
    private final float knockback;

    OmniArmorMaterials(String name, int durability, int helmet, int chest, int legs, int boots,
                       float toughness, float knockback) {
        this.name = name;
        this.durability = durability;
        this.helmet = helmet;
        this.chest = chest;
        this.legs = legs;
        this.boots = boots;
        this.toughness = toughness;
        this.knockback = knockback;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return durability;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> helmet;
            case CHESTPLATE -> chest;
            case LEGGINGS -> legs;
            case BOOTS -> boots;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return 30;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.NETHERITE_INGOT);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockback;
    }
}
