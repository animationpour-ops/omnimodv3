package com.omnimine.item;

import com.omnimine.client.ArmorTextures;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.DistExecutor;

/** Bottes lunaires (modele et decoupe des bottes Netherite vanilla, repeints). Pouvoir 1 : double saut lunaire. Pouvoir 2 : impact lunaire. */
public class LunarBootsItem extends ArmorItem {

    public LunarBootsItem(Properties properties) {
        super(OmniArmorMaterials.MOON, Type.BOOTS, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    /** Dessin porte : la Netherite vanilla repeinte en argent lunaire (zones transparentes conservees). */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        int tick = entity.tickCount;
        return DistExecutor.unsafeRunForDist(() -> () -> ArmorTextures.moon(tick),
                () -> () -> "minecraft:textures/models/armor/netherite_layer_1.png");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.omnimine.moon_boots.desc1").withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("item.omnimine.moon_boots.desc2").withStyle(ChatFormatting.AQUA));
    }

    public static boolean wornBy(LivingEntity e) {
        return e.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof LunarBootsItem;
    }
}
