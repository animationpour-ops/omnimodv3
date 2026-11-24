package com.omnimine.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Les structures qu'on peut miner. Une structure minee ne sert a rien seule : au centre d'une table de craft,
 * entouree de 8 blocs qui lui correspondent, elle devient une structure evoluee qui donne des items cheat.
 */
public enum StructureKind {
    VILLAGE("village", 0x5FD35F, 160, () -> Items.HAY_BLOCK, false,
            List.of(BuiltinStructures.VILLAGE_PLAINS, BuiltinStructures.VILLAGE_DESERT,
                    BuiltinStructures.VILLAGE_SAVANNA, BuiltinStructures.VILLAGE_SNOWY,
                    BuiltinStructures.VILLAGE_TAIGA)),
    PILLAGER_OUTPOST("pillager_outpost", 0x9A7B55, 120, () -> Items.DARK_OAK_LOG, false,
            List.of(BuiltinStructures.PILLAGER_OUTPOST)),
    WOODLAND_MANSION("woodland_mansion", 0x7A4B2A, 200, () -> Items.DARK_OAK_PLANKS, false,
            List.of(BuiltinStructures.WOODLAND_MANSION)),
    OCEAN_MONUMENT("ocean_monument", 0x3FD6C8, 200, () -> Items.WATER_BUCKET, true,
            List.of(BuiltinStructures.OCEAN_MONUMENT)),
    DESERT_PYRAMID("desert_pyramid", 0xE8CF7A, 120, () -> Items.SANDSTONE, false,
            List.of(BuiltinStructures.DESERT_PYRAMID)),
    JUNGLE_TEMPLE("jungle_temple", 0x6FA85A, 120, () -> Items.MOSSY_COBBLESTONE, false,
            List.of(BuiltinStructures.JUNGLE_TEMPLE)),
    NETHER_FORTRESS("nether_fortress", 0xB03A3A, 180, () -> Items.NETHER_BRICKS, false,
            List.of(BuiltinStructures.FORTRESS)),
    BASTION("bastion", 0xF2C14E, 180, () -> Items.BLACKSTONE, false,
            List.of(BuiltinStructures.BASTION_REMNANT)),
    END_CITY("end_city", 0xD59BFF, 200, () -> Items.PURPUR_BLOCK, false,
            List.of(BuiltinStructures.END_CITY)),
    STRONGHOLD("stronghold", 0x9FB0A0, 160, () -> Items.STONE_BRICKS, false,
            List.of(BuiltinStructures.STRONGHOLD)),
    ANCIENT_CITY("ancient_city", 0x2FC6D8, 200, () -> Items.DEEPSLATE_BRICKS, false,
            List.of(BuiltinStructures.ANCIENT_CITY));

    public final String id;
    /** Couleur des effets visuels. */
    public final int color;
    /** Temps de minage de base, en ticks. */
    public final int mineTicks;
    /** Le bloc qui doit entourer la structure dans la table de craft. */
    public final Supplier<Item> surround;
    /** Les trous laisses par la structure sont combles d'eau sous le niveau de la mer. */
    public final boolean water;
    public final List<ResourceKey<Structure>> keys;

    StructureKind(String id, int color, int mineTicks, Supplier<Item> surround, boolean water,
                  List<ResourceKey<Structure>> keys) {
        this.id = id;
        this.color = color;
        this.mineTicks = mineTicks;
        this.surround = surround;
        this.water = water;
        this.keys = keys;
    }

    private static ItemStack n(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static ItemStack book(net.minecraft.world.item.enchantment.Enchantment e, int level) {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(e, level));
    }

    /** Les items cheat donnes par la structure evoluee. */
    public List<ItemStack> rewards() {
        List<ItemStack> out = new ArrayList<>();
        switch (this) {
            case VILLAGE -> {
                out.add(n(Items.TOTEM_OF_UNDYING, 2));
                out.add(n(Items.GOLDEN_APPLE, 16));
                out.add(n(Items.EMERALD_BLOCK, 8));
            }
            case PILLAGER_OUTPOST -> {
                ItemStack bow = n(Items.CROSSBOW, 1);
                bow.enchant(Enchantments.QUICK_CHARGE, 3);
                bow.enchant(Enchantments.PIERCING, 4);
                out.add(bow);
                out.add(n(Items.FIREWORK_ROCKET, 64));
                out.add(n(Items.TOTEM_OF_UNDYING, 1));
            }
            case WOODLAND_MANSION -> {
                out.add(n(Items.TOTEM_OF_UNDYING, 4));
                out.add(n(Items.ENCHANTED_GOLDEN_APPLE, 2));
            }
            case OCEAN_MONUMENT -> {
                ItemStack trident = n(Items.TRIDENT, 1);
                trident.enchant(Enchantments.IMPALING, 5);
                trident.enchant(Enchantments.LOYALTY, 3);
                trident.enchant(Enchantments.CHANNELING, 1);
                out.add(trident);
                out.add(n(Items.CONDUIT, 2));
                out.add(n(Items.HEART_OF_THE_SEA, 1));
            }
            case DESERT_PYRAMID -> {
                out.add(n(Items.ENCHANTED_GOLDEN_APPLE, 6));
                out.add(n(Items.TNT, 32));
            }
            case JUNGLE_TEMPLE -> {
                out.add(n(Items.GOLDEN_APPLE, 32));
                out.add(book(Enchantments.MENDING, 1));
            }
            case NETHER_FORTRESS -> {
                out.add(n(Items.NETHERITE_INGOT, 4));
                out.add(n(Items.WITHER_SKELETON_SKULL, 3));
            }
            case BASTION -> {
                out.add(n(Items.NETHERITE_INGOT, 6));
                out.add(n(Items.GOLD_BLOCK, 16));
                out.add(n(Items.ENCHANTED_GOLDEN_APPLE, 1));
            }
            case END_CITY -> {
                out.add(n(Items.ELYTRA, 1));
                out.add(n(Items.FIREWORK_ROCKET, 64));
                out.add(n(Items.SHULKER_BOX, 2));
                out.add(n(Items.DRAGON_HEAD, 1));
            }
            case STRONGHOLD -> {
                out.add(n(Items.ENDER_EYE, 12));
                out.add(n(Items.ENDER_PEARL, 16));
                out.add(n(Items.TOTEM_OF_UNDYING, 1));
            }
            case ANCIENT_CITY -> {
                out.add(book(Enchantments.MENDING, 1));
                out.add(n(Items.ECHO_SHARD, 8));
                out.add(n(Items.TOTEM_OF_UNDYING, 2));
                out.add(n(Items.ENCHANTED_GOLDEN_APPLE, 2));
            }
        }
        return out;
    }
}
