package com.omnimine.registry;

import com.omnimine.Omnimine;
import com.omnimine.enchant.BedrockBreakerEnchantment;
import com.omnimine.enchant.OmniMiningEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Omnimine.MODID);

    /** Omniminage : present d'office sur la pioche en bedrock (mobs, ciel, eclairs, structures). */
    public static final RegistryObject<Enchantment> OMNI_MINING =
            ENCHANTMENTS.register("omni_mining", OmniMiningEnchantment::new);

    /** Bedrock Breaker : pioche en diamant / netherite capable de miner le bedrock. */
    public static final RegistryObject<Enchantment> BEDROCK_BREAKER =
            ENCHANTMENTS.register("bedrock_breaker", BedrockBreakerEnchantment::new);

    private ModEnchantments() {}
}
