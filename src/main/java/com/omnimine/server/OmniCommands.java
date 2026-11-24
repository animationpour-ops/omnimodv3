package com.omnimine.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.omnimine.Omnimine;
import com.omnimine.data.StructureKind;
import com.omnimine.item.BedrockPickaxeItem;
import com.omnimine.registry.ModItems;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.RegistryObject;

/** Commandes pratiques pour tourner la video (niveau 2 / operateur). */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class OmniCommands {

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("omnimine")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("kit").executes(ctx -> kit(ctx.getSource(), false)))
                .then(Commands.literal("tout").executes(ctx -> kit(ctx.getSource(), true)))
                .then(Commands.literal("orage").executes(ctx -> storm(ctx.getSource())))
                .then(Commands.literal("ciel").executes(ctx -> sky(ctx.getSource())))
                .then(Commands.literal("soleil").executes(ctx -> give(ctx.getSource(), ModItems.SUN_SWORD.get(), 1)))
                .then(Commands.literal("lune").executes(ctx -> give(ctx.getSource(), ModItems.MOON_BOOTS.get(), 1)))
                .then(Commands.literal("livre").executes(ctx -> giveStack(ctx.getSource(), BedrockPickaxeItem.breakerBook())))
                .then(Commands.literal("breaker")
                        .executes(ctx -> giveStack(ctx.getSource(), BedrockPickaxeItem.breakerDiamondPickaxe())))
                .then(Commands.literal("construire")
                        .then(Commands.argument("structure", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(StructureKind.values()).map(k -> k.id), builder))
                                .executes(ctx -> build(ctx.getSource(), StringArgumentType.getString(ctx, "structure"))))));
    }

    /** Pose directement la structure cheat devant toi (comme un clic droit sur la structure evoluee, sans l'objet). */
    private static int build(CommandSourceStack src, String id) {
        StructureKind kind = null;
        for (StructureKind k : StructureKind.values()) {
            if (k.id.equals(id)) {
                kind = k;
            }
        }
        if (kind == null) {
            src.sendFailure(Component.literal("Structure inconnue : " + id));
            return 0;
        }
        try {
            ServerPlayer p = src.getPlayerOrException();
            return CheatStructures.start(p.serverLevel(), p, kind) ? 1 : 0;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Il faut etre un joueur."));
            return 0;
        }
    }

    private static int giveStack(CommandSourceStack src, ItemStack stack) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            ItemHandlerHelper.giveItemToPlayer(p, stack);
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Il faut etre un joueur."));
            return 0;
        }
    }

    private static int give(CommandSourceStack src, Item item, int count) {
        return giveStack(src, new ItemStack(item, count));
    }

    /**
     * kit : la pioche en bedrock (Omniminage deja dessus) et le livre Bedrock Breaker. tout : en plus, tous les items
     * du mod (pour tester ou lancer la video).
     */
    private static int kit(CommandSourceStack src, boolean all) {
        try {
            ServerPlayer p = src.getPlayerOrException();
            ItemHandlerHelper.giveItemToPlayer(p, BedrockPickaxeItem.enchantedPickaxe());
            ItemHandlerHelper.giveItemToPlayer(p, BedrockPickaxeItem.breakerBook());
            if (all) {
                give(src, ModItems.SUN_SWORD.get(), 1);
                give(src, ModItems.MOON_BOOTS.get(), 1);
                give(src, ModItems.LIGHTNING_HELMET.get(), 1);
                give(src, ModItems.LIGHTNING_CHESTPLATE.get(), 1);
                give(src, ModItems.LIGHTNING_LEGGINGS.get(), 1);
                for (RegistryObject<Item> r : ModItems.MOBS.values()) {
                    give(src, r.get(), 1);
                }
                for (RegistryObject<Item> r : ModItems.STRUCTURES.values()) {
                    give(src, r.get(), 1);
                }
                for (RegistryObject<Item> r : ModItems.EVOLVED.values()) {
                    give(src, r.get(), 1);
                }
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("Il faut etre un joueur."));
            return 0;
        }
    }

    /**
     * Orage : lance simplement un orage (comme /weather thunder). Aucun eclair n'est cree par le mod : il faut un
     * paratonnerre en cuivre, et la foudre de l'orage viendra toute seule le frapper.
     */
    private static int storm(CommandSourceStack src) {
        ServerLevel level = src.getLevel();
        level.setWeatherParameters(0, 12000, true, true);
        return 1;
    }

    private static int sky(CommandSourceStack src) {
        SkyBodies.restoreNow();
        return 1;
    }

    private OmniCommands() {}
}
