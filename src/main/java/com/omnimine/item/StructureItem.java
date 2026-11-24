package com.omnimine.item;

import com.omnimine.data.StructureKind;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Une structure minee. Seule elle ne sert a rien : il faut la craft (au centre, entouree de 8 blocs correspondants). */
public class StructureItem extends Item {
    public final StructureKind kind;

    public StructureItem(StructureKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("tooltip.omnimine.structure_useless").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("tooltip.omnimine.structure_craft",
                Component.translatable(kind.surround.get().getDescriptionId())).withStyle(ChatFormatting.DARK_AQUA));
    }
}
