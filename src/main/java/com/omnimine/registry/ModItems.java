package com.omnimine.registry;

import com.omnimine.Omnimine;
import com.omnimine.data.MobKind;
import com.omnimine.data.StructureKind;
import com.omnimine.item.BedrockPickaxeItem;
import com.omnimine.item.EvolvedStructureItem;
import com.omnimine.item.LightningArmorItem;
import com.omnimine.item.LunarBootsItem;
import com.omnimine.item.MobItem;
import com.omnimine.item.StructureItem;
import com.omnimine.item.SunSwordItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Omnimine.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Omnimine.MODID);

    private static Item.Properties gear() {
        return new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(1);
    }

    public static final RegistryObject<Item> BEDROCK_PICKAXE = ITEMS.register("bedrock_pickaxe",
            () -> new BedrockPickaxeItem(gear()));
    public static final RegistryObject<Item> SUN_SWORD = ITEMS.register("sun_sword",
            () -> new SunSwordItem(gear()));
    public static final RegistryObject<Item> MOON_BOOTS = ITEMS.register("moon_boots",
            () -> new LunarBootsItem(gear()));
    public static final RegistryObject<Item> LIGHTNING_HELMET = ITEMS.register("lightning_helmet",
            () -> new LightningArmorItem(ArmorItem.Type.HELMET, gear()));
    public static final RegistryObject<Item> LIGHTNING_CHESTPLATE = ITEMS.register("lightning_chestplate",
            () -> new LightningArmorItem(ArmorItem.Type.CHESTPLATE, gear()));
    public static final RegistryObject<Item> LIGHTNING_LEGGINGS = ITEMS.register("lightning_leggings",
            () -> new LightningArmorItem(ArmorItem.Type.LEGGINGS, gear()));

    /** Un item par mob mine : mob_skeleton, mob_creeper... */
    public static final Map<MobKind, RegistryObject<Item>> MOBS = new EnumMap<>(MobKind.class);
    /** Structures minees : structure_village... */
    public static final Map<StructureKind, RegistryObject<Item>> STRUCTURES = new EnumMap<>(StructureKind.class);
    /** Structures evoluees : evolved_village... */
    public static final Map<StructureKind, RegistryObject<Item>> EVOLVED = new EnumMap<>(StructureKind.class);

    static {
        for (MobKind kind : MobKind.values()) {
            MOBS.put(kind, ITEMS.register("mob_" + kind.id,
                    () -> new MobItem(kind, new Item.Properties().rarity(Rarity.RARE).stacksTo(1))));
        }
        for (StructureKind kind : StructureKind.values()) {
            STRUCTURES.put(kind, ITEMS.register("structure_" + kind.id,
                    () -> new StructureItem(kind, new Item.Properties().rarity(Rarity.UNCOMMON).stacksTo(16))));
            EVOLVED.put(kind, ITEMS.register("evolved_" + kind.id,
                    () -> new EvolvedStructureItem(kind, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16))));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.omnimine"))
                    .icon(() -> new ItemStack(BEDROCK_PICKAXE.get()))
                    .displayItems((params, out) -> {
                        out.accept(BedrockPickaxeItem.enchantedPickaxe());
                        out.accept(BedrockPickaxeItem.breakerBook());
                        out.accept(BedrockPickaxeItem.breakerDiamondPickaxe());
                        ITEMS.getEntries().forEach(e -> {
                            if (e.get() != BEDROCK_PICKAXE.get()) {
                                out.accept(e.get());
                            }
                        });
                    })
                    .build());

    private ModItems() {}
}
