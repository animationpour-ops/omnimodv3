package com.omnimine.server;

import com.omnimine.OmniConfig;
import com.omnimine.Omnimine;
import com.omnimine.item.BedrockPickaxeItem;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModItems;
import com.omnimine.registry.ModSounds;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Minage universel, cote serveur. Tant que le joueur maintient le clic gauche avec la pioche en bedrock (ou une
 * pioche Bedrock Breaker pour le bedrock), on suit une
 * progression (comme le minage d'un bloc : il faut maintenir, ca se fissure, ca se relache = ca repart).
 * Quand elle atteint 100 %, la cible arrive dans l'inventaire.
 */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class MiningManager {
    public static final String TAG_FROZEN = "omnimine_frozen";
    private static final int HOLD_GRACE_TICKS = 3;
    private static final int LOST_GRACE_TICKS = 8;

    private static final class Session {
        MineTargets.Target target;
        float progress;
        int age;
        int lost;
        BlockPos crackPos;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> HOLD = new HashMap<>();

    /** Appele a chaque tick tant que le client maintient le clic gauche. */
    public static void hold(ServerPlayer player) {
        HOLD.put(player.getUUID(), player.serverLevel().getServer().getTickCount());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) {
            return;
        }
        UUID id = sp.getUUID();
        Session s = SESSIONS.get(id);
        Integer last = HOLD.get(id);
        boolean holding = last != null && sp.serverLevel().getServer().getTickCount() - last <= HOLD_GRACE_TICKS;
        if (!holding || !BedrockPickaxeItem.canMineSpecial(sp.getMainHandItem()) || !sp.isAlive()) {
            if (s != null) {
                stop(sp, s);
            }
            return;
        }
        ServerLevel level = sp.serverLevel();
        MineTargets.Target t = MineTargets.pick(sp);
        if (t == null) {
            if (s != null && ++s.lost > LOST_GRACE_TICKS) {
                stop(sp, s);
            } else if (s != null) {
                s.progress = Math.max(0.0F, s.progress - 0.03F);
            }
            return;
        }
        if (s != null && !s.target.key.equals(t.key)) {
            stop(sp, s);
            s = null;
        }
        if (s == null) {
            s = new Session();
            s.target = t;
            SESSIONS.put(id, s);
            begin(level, sp, t);
        }
        s.target = t;
        s.lost = 0;
        s.age++;

        float speed = OmniConfig.MINING_SPEED.get().floatValue();
        s.progress += speed / Math.max(1.0F, t.mineTicks);
        feedback(level, sp, s);
        if (s.progress >= 1.0F) {
            SESSIONS.remove(id);
            finish(level, sp, s);
        }
    }

    // ------------------------------------------------------------- deroulement

    private static void begin(ServerLevel level, ServerPlayer sp, MineTargets.Target t) {
        if (t.kind == MineTargets.Kind.MOB && t.entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.getPersistentData().putBoolean(TAG_FROZEN, true);
        }
        ModSounds.play(level, sp.position(), SoundEvents.STONE_HIT, 1.0F, 0.6F);
    }

    private static int seedOf(MineTargets.Target t) {
        return t.key.hashCode();
    }

    private static void feedback(ServerLevel level, ServerPlayer sp, Session s) {
        MineTargets.Target t = s.target;
        float p = Math.min(1.0F, s.progress);
        // fissures de minage VANILLA (destroy_stage) : sur le mob / l'eclair / le disque du ciel ; pour une structure
        // ce sont les vraies fissures de bloc (destroyBlockProgress, plus bas)
        if (s.age % 2 == 0 && t.kind != MineTargets.Kind.STRUCTURE && t.kind != MineTargets.Kind.BEDROCK) {
            double w = t.size;
            double h = t.size;
            if (t.entity != null) {
                w = t.entity.getBbWidth();
                h = t.entity.getBbHeight();
            }
            Vec3 at = t.center;
            Vec3 extra = new Vec3(w, h, t.kind.ordinal());
            if (t.kind == MineTargets.Kind.BOLT && t.bolt != null) {
                // un eclair : le client dessine un manchon de fissures le long de son tronc, il lui faut le pied de
                // l'eclair et la graine de sa forme (un entier : il tient sans perte dans un double)
                at = t.bolt.base;
                extra = new Vec3(t.bolt.seed, 0.0D, t.kind.ordinal());
            }
            VfxPacket crack = VfxPacket.of(VfxType.MINE_CRACK, at).param(p).color(t.color).seed(seedOf(t))
                    .duration(7).to(extra);
            if (t.entity != null) {
                crack.follow(t.entity);
            }
            crack.send(level);
        }
        if (s.age % 4 == 0) {
            ModSounds.play(level, sp.position(), SoundEvents.STONE_HIT, 0.9F, 0.7F + p * 0.8F);
        }
        if (s.age % 6 == 0) {
            sp.swing(InteractionHand.MAIN_HAND, true);
        }
        if (s.age % 20 == 0 && p > 0.2F) {
            ModSounds.play(level, t.center, ModSounds.CRACK, 1.2F, 0.7F + p * 0.7F);
        }
        if ((t.kind == MineTargets.Kind.STRUCTURE || t.kind == MineTargets.Kind.BEDROCK) && t.pos != null) {
            level.destroyBlockProgress(sp.getId(), t.pos, Math.min(9, (int) (p * 10.0F)));
            if (s.crackPos != null && !s.crackPos.equals(t.pos)) {
                level.destroyBlockProgress(sp.getId(), s.crackPos, -1);
            }
            s.crackPos = t.pos;
        }
        if (s.age % 2 == 0) {
            int bars = Math.round(p * 20.0F);
            String bar = "|".repeat(bars) + ChatFormatting.DARK_GRAY + "|".repeat(20 - bars);
            sp.displayClientMessage(Component.translatable("message.omnimine.mining", t.name,
                    Math.round(p * 100.0F)).withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("  " + ChatFormatting.YELLOW + bar)), true);
        }
    }

    /** Le joueur relache, change de cible ou range la pioche : la cible est liberee. */
    private static void stop(ServerPlayer sp, Session s) {
        SESSIONS.remove(sp.getUUID());
        release(sp.serverLevel(), sp, s);
    }

    private static void release(ServerLevel level, ServerPlayer sp, Session s) {
        MineTargets.Target t = s.target;
        if (t.entity instanceof Mob mob && mob.isAlive()) {
            unfreeze(mob);
        }
        if (s.crackPos != null) {
            level.destroyBlockProgress(sp.getId(), s.crackPos, -1);
        }
        VfxPacket.cancel(VfxType.MINE_CRACK, seedOf(t), t.center).send(level);
    }

    private static void unfreeze(Mob mob) {
        mob.setNoAi(false);
        mob.getPersistentData().remove(TAG_FROZEN);
    }

    private static void give(ServerPlayer sp, ItemStack stack) {
        ItemHandlerHelper.giveItemToPlayer(sp, stack);
    }

    private static void finish(ServerLevel level, ServerPlayer sp, Session s) {
        MineTargets.Target t = s.target;
        release(level, sp, s);
        Vec3 from = t.center;
        VfxPacket.of(VfxType.ABSORB, from).follow(sp).color(t.color).param(t.size)
                .duration(t.kind == MineTargets.Kind.STRUCTURE ? 50 : 34).send(level);
        ModSounds.play(level, sp.position(), ModSounds.ABSORB, 2.0F, 1.0F);
        sp.displayClientMessage(Component.translatable("message.omnimine.mined", t.name)
                .withStyle(ChatFormatting.GREEN), true);
        switch (t.kind) {
            case MOB -> {
                t.entity.discard();
                give(sp, new ItemStack(ModItems.MOBS.get(t.mob).get()));
            }
            case SUN -> {
                SkyBodies.mineSun(level);
                give(sp, new ItemStack(ModItems.SUN_SWORD.get()));
                ModSounds.play(level, sp.position(), ModSounds.VOID_HOLE, 5.0F, 0.8F);
            }
            case MOON -> {
                SkyBodies.mineMoon(level);
                give(sp, new ItemStack(ModItems.MOON_BOOTS.get()));
                ModSounds.play(level, sp.position(), ModSounds.VOID_HOLE, 5.0F, 1.2F);
            }
            case BOLT -> {
                Vec3 base = t.bolt.base;
                FrozenBolts.remove(level, t.bolt);
                VfxPacket.of(VfxType.BOLT_SHATTER, base).seed(t.bolt.seed).color(0xBFE8FF).duration(30).send(level);
                ModSounds.play(level, base, ModSounds.THUNDER, 5.0F, 1.3F);
                give(sp, new ItemStack(StormPowers.nextPiece(sp)));
            }
            case BEDROCK -> {
                level.destroyBlock(t.pos, false);
                give(sp, new ItemStack(Items.BEDROCK));
                ModSounds.play(level, t.center, ModSounds.BOOM, 2.0F, 0.9F);
            }
            case STRUCTURE -> {
                give(sp, new ItemStack(ModItems.STRUCTURES.get(t.structure).get()));
                StructureDissolve.start(level, sp, t.structure, t.start, t.pos);
                ModSounds.play(level, t.center, ModSounds.BOOM, 3.0F, 0.6F);
            }
        }
    }

    // ------------------------------------------------------------------ securite

    /** Un mob fige par un minage interrompu (arret du serveur, deconnexion) est libere des qu'il est recharge. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Mob mob
                && mob.getPersistentData().getBoolean(TAG_FROZEN)) {
            boolean active = false;
            for (Session s : SESSIONS.values()) {
                if (s.target.entity == mob) {
                    active = true;
                }
            }
            if (!active) {
                unfreeze(mob);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            Session s = SESSIONS.get(sp.getUUID());
            if (s != null) {
                stop(sp, s);
            }
            HOLD.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (Iterator<Session> it = SESSIONS.values().iterator(); it.hasNext(); ) {
            Session s = it.next();
            if (s.target.entity instanceof Mob mob && mob.isAlive()) {
                unfreeze(mob);
            }
            it.remove();
        }
        HOLD.clear();
    }

    private MiningManager() {}
}
