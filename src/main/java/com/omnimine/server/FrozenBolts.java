package com.omnimine.server;

import com.omnimine.Omnimine;
import com.omnimine.OmniConfig;
import com.omnimine.item.BedrockPickaxeItem;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModSounds;
import com.omnimine.util.BoltShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Recolte naturelle de la foudre. Pendant un orage, la foudre vanilla est attiree par les paratonnerres en cuivre :
 * quand elle en frappe un et qu'un joueur portant la pioche (enchantee) est a portee, l'eclair est fige (c'est le
 * VRAI eclair de l'orage, dessine par le rendu vanilla). On peut le miner ; si on le laisse, il frappe pour de vrai.
 * Le mod ne fait jamais apparaitre d'eclair de lui-meme.
 */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class FrozenBolts {
    public static final String TAG_REAL = "omnimine_real_bolt";
    private static final int RESEND_EVERY = 40;

    public static final class Bolt {
        public final int id;
        /** Graine du dessin : le client et le serveur en tirent la meme forme (voir BoltShape). */
        public final int seed;
        public final Vec3 base;
        /** Les 9 sommets du tronc, du paratonnerre jusqu'a 128 blocs de haut. */
        public final Vec3[] trunk;
        public final long expiry;
        public final UUID cause;

        Bolt(int id, Vec3 base, long expiry, UUID cause) {
            this.id = id;
            this.seed = id * 7919 + 4242;
            this.base = base;
            this.trunk = BoltShape.trunk(this.seed, base);
            this.expiry = expiry;
            this.cause = cause;
        }
    }

    private static final Map<ServerLevel, List<Bolt>> BOLTS = new HashMap<>();
    private static int nextId = 1;

    public static List<Bolt> in(ServerLevel level) {
        List<Bolt> list = BOLTS.get(level);
        return list == null ? List.of() : list;
    }

    /** Un joueur a portee qui porte la pioche enchantee (dans son inventaire, pas forcement en main). */
    private static ServerPlayer holder(ServerLevel level, Vec3 pos) {
        double r = OmniConfig.BOLT_CAPTURE_RADIUS.get();
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(pos) <= r * r && BedrockPickaxeItem.carriesOmni(p)) {
                return p;
            }
        }
        return null;
    }

    /** La foudre naturelle frappe le bloc situe juste sous le point d'apparition de l'eclair : est-ce un paratonnerre ? */
    private static boolean hitsRod(ServerLevel level, LightningBolt bolt) {
        BlockPos strike = BlockPos.containing(bolt.getX(), bolt.getY() - 1.0E-6D, bolt.getZ());
        return level.getBlockState(strike).is(Blocks.LIGHTNING_ROD);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LightningBolt bolt) || !(event.getLevel() instanceof ServerLevel level)
                || bolt.getPersistentData().getBoolean(TAG_REAL)) {
            return;
        }
        if (OmniConfig.BOLT_NEEDS_ROD.get() && !hitsRod(level, bolt)) {
            return;
        }
        ServerPlayer owner = holder(level, bolt.position());
        if (owner == null) {
            return;
        }
        event.setCanceled(true);
        capture(level, bolt.position(), owner);
    }

    private static void capture(ServerLevel level, Vec3 pos, ServerPlayer cause) {
        int ticks = OmniConfig.BOLT_FREEZE_SECONDS.get() * 20;
        Bolt b = new Bolt(nextId++, pos, level.getGameTime() + ticks, cause.getUUID());
        BOLTS.computeIfAbsent(level, l -> new ArrayList<>()).add(b);
        VfxPacket.of(VfxType.FROZEN_BOLT, pos).duration(ticks).seed(b.seed).color(0xBFE8FF).send(level);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10.0F, 0.9F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 4.0F, 0.6F);
        ModSounds.play(level, pos, ModSounds.THUNDER, 6.0F, 1.0F);
        for (ServerPlayer p : level.players()) {
            if (BedrockPickaxeItem.carriesOmni(p) && p.position().distanceToSqr(pos) < 96.0D * 96.0D) {
                p.displayClientMessage(Component.translatable("message.omnimine.bolt_caught")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    /** L'eclair a ete mine : il disparait (il ne frappera jamais). */
    public static void remove(ServerLevel level, Bolt bolt) {
        List<Bolt> list = BOLTS.get(level);
        if (list != null) {
            list.remove(bolt);
        }
        VfxPacket.cancel(VfxType.FROZEN_BOLT, bolt.seed, bolt.base).send(level);
    }

    /** Laisse l'eclair retomber la ou il etait (c'est la meme foudre naturelle, sans la recapturer). */
    public static void strikeReal(ServerLevel level, Vec3 pos, ServerPlayer cause) {
        LightningBolt real = EntityType.LIGHTNING_BOLT.create(level);
        if (real == null) {
            return;
        }
        real.moveTo(pos.x, pos.y, pos.z);
        real.getPersistentData().putBoolean(TAG_REAL, true);
        if (cause != null) {
            real.setCause(cause);
        }
        level.addFreshEntity(real);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        List<Bolt> list = BOLTS.get(level);
        if (list == null || list.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Bolt> it = list.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            if (now >= b.expiry) {
                it.remove();
                ServerPlayer cause = null;
                for (ServerPlayer p : level.players()) {
                    if (p.getUUID().equals(b.cause)) {
                        cause = p;
                    }
                }
                strikeReal(level, b.base, cause);
            } else {
                if (now % 14L == b.id % 14L) {
                    ModSounds.play(level, b.base, ModSounds.ZAP, 2.5F, 0.7F + level.random.nextFloat() * 0.6F);
                }
                // un joueur qui arrive plus tard (ou revient de loin) voit quand meme l'eclair
                if (now % RESEND_EVERY == b.id % RESEND_EVERY) {
                    int left = (int) (b.expiry - now);
                    VfxPacket.of(VfxType.FROZEN_BOLT, b.base).duration(left).seed(b.seed).color(0xBFE8FF).send(level);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BOLTS.clear();
    }

    private FrozenBolts() {}
}
