package com.omnimine.item;

import com.omnimine.data.StructureKind;
import com.omnimine.server.CheatStructures;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Structure evoluee : un clic droit POSE la structure cheat dans le monde, devant le joueur (facade tournee vers lui),
 * avec ses coffres de butin. Voir CheatStructures.
 */
public class EvolvedStructureItem extends Item {
    private static final int COOLDOWN_TICKS = 100;

    public final StructureKind kind;

    public EvolvedStructureItem(StructureKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            if (CheatStructures.start(sl, sp, kind)) {
                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }
        player.swing(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.omnimine.evolved").withStyle(ChatFormatting.GOLD));
        tip.add(Component.translatable("tooltip.omnimine.evolved_use").withStyle(ChatFormatting.GRAY));
    }
}
