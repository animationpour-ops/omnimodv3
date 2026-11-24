package com.omnimine.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.omnimine.client.ArmorTextures;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.DistExecutor;

/**
 * Armure d'eclair (modele ET decoupe de la Netherite vanilla, repeints : le skin du joueur reste visible). Un pouvoir
 * par piece : casque = foudre en chaine, plastron = decharge statique, jambieres = course foudroyante (vitesse).
 */
public class LightningArmorItem extends ArmorItem {
    private static final UUID SPEED_ID = UUID.fromString("5f1c3a7e-2b44-4c1d-9d0e-6a7b8c9d0e1f");
    /** Bonus de vitesse permanent des jambieres (+60 %), qui se cumule avec l'effet Vitesse donne en sprint. */
    public static final double LEGGINGS_SPEED = 0.6D;

    private final boolean leggings;

    public LightningArmorItem(Type type, Properties properties) {
        super(OmniArmorMaterials.LIGHTNING, type, properties);
        this.leggings = type == Type.LEGGINGS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    /** Les jambieres donnent une vraie vitesse en permanence (attribut de vitesse de deplacement). */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getDefaultAttributeModifiers(slot);
        if (!leggings || slot != EquipmentSlot.LEGS) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);
        builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED_ID, "Lightning leggings speed",
                LEGGINGS_SPEED, AttributeModifier.Operation.MULTIPLY_TOTAL));
        return builder.build();
    }

    /**
     * Dessin porte : la Netherite vanilla repeinte (zones transparentes conservees = skin visible), animee selon l'age
     * de l'entite. Les images sont generees a l'execution cote client (voir ArmorTextures).
     */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        int tick = entity.tickCount;
        return DistExecutor.unsafeRunForDist(() -> () -> ArmorTextures.lightning(layer, tick),
                () -> () -> "minecraft:textures/models/armor/netherite_layer_" + layer + ".png");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable(getDescriptionId() + ".desc1").withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable(getDescriptionId() + ".desc2").withStyle(ChatFormatting.GRAY));
    }

    public static boolean wears(LivingEntity e, EquipmentSlot slot) {
        return e.getItemBySlot(slot).getItem() instanceof LightningArmorItem;
    }
}
