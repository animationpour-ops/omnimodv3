package com.omnimine.item;

import com.omnimine.server.SunPowers;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Epee du soleil (forme de l'epee vanilla). Pouvoir 1 : chaque coup embrase. Pouvoir 2 (clic droit) : rayon solaire. */
public class SunSwordItem extends SwordItem {
    public static final int COOLDOWN = 20 * 12;

    public SunSwordItem(Properties properties) {
        super(Tiers.NETHERITE, 9, -2.4F, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level() instanceof ServerLevel level) {
            SunPowers.hit(level, attacker, target);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return PowerUse.use(this, SunPowers::beam, COOLDOWN, level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.omnimine.sun_sword.desc1").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("item.omnimine.sun_sword.desc2").withStyle(ChatFormatting.YELLOW));
        tip.add(Component.translatable("tooltip.omnimine.cooldown", COOLDOWN / 20).withStyle(ChatFormatting.DARK_AQUA));
    }
}
