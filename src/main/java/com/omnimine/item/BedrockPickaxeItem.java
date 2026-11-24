package com.omnimine.item;

import com.omnimine.registry.ModEnchantments;
import com.omnimine.registry.ModItems;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pioche en bedrock : une pioche vanilla, tres rapide et incassable, qui porte d'office l'enchantement Omniminage :
 * elle mine les mobs, le soleil, la lune, les eclairs et (accroupi) les structures, et aussi le bedrock. Elle ne fait
 * jamais apparaitre d'eclair. On la fabrique a la smithing table : pioche en diamant + 1 bloc de bedrock.
 */
public class BedrockPickaxeItem extends PickaxeItem {
    public BedrockPickaxeItem(Properties properties) {
        super(Tiers.NETHERITE, 1, -2.4F, properties);
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        float base = super.getDestroySpeed(stack, state);
        return base > 1.0F ? base * 2.5F : base;
    }

    /** Toute pioche en bedrock, d'ou qu'elle vienne (commande, creatif, smithing), porte l'enchantement. */
    @Override
    public ItemStack getDefaultInstance() {
        return enchantedPickaxe();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide) {
            ensureOmni(stack);
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        ensureOmni(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.omnimine.bedrock_pickaxe.desc1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.omnimine.bedrock_pickaxe.desc2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.omnimine.bedrock_pickaxe.desc3").withStyle(ChatFormatting.DARK_AQUA));
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.OMNI_MINING.get(), stack) <= 0) {
            // l'enchantement est ajoute a la fabrication ; en attendant (apercu de la smithing table) on l'affiche quand meme
            tip.add(ModEnchantments.OMNI_MINING.get().getFullname(1));
        }
    }

    /** Ajoute l'enchantement Omniminage s'il manque (par exemple sur le resultat tout frais de la smithing table). */
    private static void ensureOmni(ItemStack stack) {
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.OMNI_MINING.get(), stack) <= 0) {
            stack.enchant(ModEnchantments.OMNI_MINING.get(), 1);
        }
    }

    // ---- aides ----

    /** Omniminage : toute pioche en bedrock l'a d'office. */
    public static boolean hasOmni(ItemStack stack) {
        return stack.getItem() instanceof BedrockPickaxeItem;
    }

    /** Le joueur tient-il la pioche en bedrock en main principale ? */
    public static boolean holdsOmni(Player player) {
        return hasOmni(player.getMainHandItem());
    }

    /** Le joueur porte-t-il la pioche en bedrock quelque part (inventaire, barre rapide ou seconde main) ? */
    public static boolean carriesOmni(Player player) {
        for (ItemStack s : player.getInventory().items) {
            if (hasOmni(s)) {
                return true;
            }
        }
        return hasOmni(player.getOffhandItem());
    }

    /** Peut-elle miner le bedrock : la pioche en bedrock, ou une pioche diamant / netherite avec Bedrock Breaker. */
    public static boolean canBreakBedrock(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BedrockPickaxeItem) {
            return true;
        }
        return item instanceof PickaxeItem pick && (pick.getTier() == Tiers.DIAMOND || pick.getTier() == Tiers.NETHERITE)
                && EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.BEDROCK_BREAKER.get(), stack) > 0;
    }

    /** Cet objet permet-il un minage « special » (mobs, ciel, eclairs, structures ou bedrock) quand on maintient le clic ? */
    public static boolean canMineSpecial(ItemStack stack) {
        return hasOmni(stack) || canBreakBedrock(stack);
    }

    public static ItemStack enchantedPickaxe() {
        ItemStack stack = new ItemStack(ModItems.BEDROCK_PICKAXE.get());
        ensureOmni(stack);
        return stack;
    }

    /** Le livre Bedrock Breaker (vendu par le libraire). */
    public static ItemStack breakerBook() {
        return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ModEnchantments.BEDROCK_BREAKER.get(), 1));
    }

    /** Une pioche en diamant avec Bedrock Breaker (pour miner du bedrock et fabriquer la pioche en bedrock). */
    public static ItemStack breakerDiamondPickaxe() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.enchant(ModEnchantments.BEDROCK_BREAKER.get(), 1);
        return stack;
    }
}
