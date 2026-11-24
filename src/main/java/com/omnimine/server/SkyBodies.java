package com.omnimine.server;

import com.omnimine.Omnimine;
import com.omnimine.OmniConfig;
import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import com.omnimine.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Le soleil et la lune peuvent etre minés. Le trou dans le ciel est dessine cote client ; ici on garde l'etat
 * (non persistant) : sans soleil la nuit tombe et reste, puis le ciel se referme apres un delai.
 */
@Mod.EventBusSubscriber(modid = Omnimine.MODID)
public final class SkyBodies {
    private static boolean sunGone;
    private static boolean moonGone;
    private static long sunBack;
    private static long moonBack;
    /** Direction figee du trou du soleil (null : le trou suit le soleil). */
    private static Vec3 sunHole;
    private static long forcedTime;

    /** Direction du soleil dans le ciel (formule du rendu vanilla : YP(-90) puis XP(angle)). */
    public static Vec3 sunDir(Level level) {
        double th = level.getTimeOfDay(1.0F) * (Math.PI * 2.0D);
        return new Vec3(-Math.sin(th), Math.cos(th), 0.0D);
    }

    public static Vec3 moonDir(Level level) {
        return sunDir(level).scale(-1.0D);
    }

    public static boolean sunGone() {
        return sunGone;
    }

    public static boolean moonGone() {
        return moonGone;
    }

    public static void mineSun(ServerLevel level) {
        boolean force = OmniConfig.FORCE_NIGHT_WITHOUT_SUN.get();
        sunGone = true;
        sunBack = level.getGameTime() + OmniConfig.SUN_RETURN_SECONDS.get() * 20L;
        sunHole = force ? sunDir(level) : null;
        forcedTime = (level.getDayTime() / 24000L) * 24000L + 16500L;
        if (force) {
            level.setDayTime(forcedTime);
        }
        broadcastHole(level, 0);
        tell(level, "message.omnimine.sun_gone");
    }

    /** Fait revenir le soleil et la lune tout de suite (commande). */
    public static void restoreNow() {
        sunBack = 0L;
        moonBack = 0L;
    }

    public static void mineMoon(ServerLevel level) {
        moonGone = true;
        moonBack = level.getGameTime() + OmniConfig.MOON_RETURN_SECONDS.get() * 20L;
        broadcastHole(level, 1);
        tell(level, "message.omnimine.moon_gone");
    }

    private static void tell(ServerLevel level, String key) {
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void broadcastHole(ServerLevel level, int which) {
        VfxPacket p = VfxPacket.of(VfxType.SKY_HOLE, Vec3.ZERO).param(which).duration(110);
        if (which == 0 && sunHole != null) {
            p.to(sunHole);
        }
        p.sendAll(level);
    }

    private static void returnBody(ServerLevel level, int which) {
        Vec3 dir;
        if (which == 0) {
            long t = level.getDayTime();
            level.setDayTime(t - (t % 24000L) + 23500L);
            dir = sunHole != null ? sunHole : sunDir(level);
        } else {
            dir = moonDir(level);
        }
        VfxPacket.of(VfxType.SKY_RETURN, Vec3.ZERO).to(dir).param(which).duration(70).sendAll(level);
        for (ServerPlayer p : level.players()) {
            ModSounds.play(level, p.position(), ModSounds.RIFT_OPEN, 4.0F, 1.3F);
            ModSounds.play(level, p.position(), ModSounds.FORGE_DONE, 4.0F, 0.8F);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD || !(sunGone || moonGone)) {
            return;
        }
        long now = level.getGameTime();
        if (sunGone) {
            if (now >= sunBack) {
                sunGone = false;
                returnBody(level, 0);
            } else if (OmniConfig.FORCE_NIGHT_WITHOUT_SUN.get()) {
                level.setDayTime(forcedTime);
            }
        }
        if (moonGone && now >= moonBack) {
            moonGone = false;
            returnBody(level, 1);
        }
        if (now % 80L == 0L) {
            if (sunGone) {
                broadcastHole(level, 0);
            }
            if (moonGone) {
                broadcastHole(level, 1);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        sunGone = false;
        moonGone = false;
        sunHole = null;
    }

    private SkyBodies() {}
}
