package com.omnimine.client;

import com.omnimine.net.VfxPacket;
import com.omnimine.net.VfxType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Liste des effets actifs cote client. */
public final class VfxManager {
    public static final List<Fx> ACTIVE = new ArrayList<>();

    public static void receive(VfxPacket p) {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        switch (p.type) {
            case CANCEL -> cancel(p);

            // ---- minage
            case MINE_CRACK -> {
                // meme cible : on met a jour les fissures sur place, l'animation ne saute pas
                for (Fx fx : ACTIVE) {
                    if (fx instanceof FxMining.MineCrack crack && fx.seed == p.seed && !fx.finished()) {
                        crack.refresh(p);
                        return;
                    }
                }
                ACTIVE.add(new FxMining.MineCrack(p));
            }
            case ABSORB -> ACTIVE.add(new FxMining.Absorb(p));
            case SKY_HOLE -> {
                int which = p.param >= 0.5F ? 1 : 0;
                for (Fx fx : ACTIVE) {
                    if (fx instanceof FxMining.SkyHole hole && hole.which() == which && !fx.finished()) {
                        hole.refresh(p);
                        return;
                    }
                }
                ACTIVE.add(new FxMining.SkyHole(p));
            }
            case SKY_RETURN -> {
                int which = p.param >= 0.5F ? 1 : 0;
                for (Fx fx : ACTIVE) {
                    if (fx instanceof FxMining.SkyHole hole && hole.which() == which) {
                        hole.age = hole.duration;
                    }
                }
                ACTIVE.add(new FxMining.SkyReturn(p));
            }
            case DISSOLVE -> ACTIVE.add(new FxMining.Dissolve(p));
            case FROZEN_BOLT -> {
                for (Fx fx : ACTIVE) {
                    if (fx.type == VfxType.FROZEN_BOLT && fx.seed == p.seed && !fx.finished()) {
                        return;
                    }
                }
                ACTIVE.add(new FxMining.FrozenBolt(p));
            }
            case BOLT_SHATTER -> ACTIVE.add(new FxMining.BoltShatter(p));
            case REWARD -> ACTIVE.add(new FxMining.Reward(p));

            // ---- epee du soleil
            case SUN_FLARE -> ACTIVE.add(new FxSun.Flare(p));
            case SUN_BEAM -> ACTIVE.add(new FxSun.Beam(p));

            // ---- bottes lunaires
            case MOON_LEAP -> ACTIVE.add(new FxMoon.Leap(p));
            case MOON_SLAM -> ACTIVE.add(new FxMoon.Slam(p));

            // ---- armure d'eclair
            case SMITE -> ACTIVE.add(new FxLight.Smite(p));
            case ARC -> ACTIVE.add(new FxLight.Arc(p));
            case MINI -> ACTIVE.add(new FxLight.Mini(p));
            case NOVA -> ACTIVE.add(new FxStorm.Nova(p));
            case ZAP -> ACTIVE.add(new FxStorm.Zap(p));

            // ---- pouvoirs des mobs
            case BONE_RAIN -> ACTIVE.add(new FxMobs.BoneRain(p));
            case BLAST -> ACTIVE.add(new FxMobs.Blast(p));
            case ROT -> ACTIVE.add(new FxMobs.Rot(p));
            case WEB -> ACTIVE.add(new FxMobs.Web(p));
            case LASER -> ACTIVE.add(new FxMobs.Laser(p));
            case SONIC -> ACTIVE.add(new FxMobs.Sonic(p));
            case QUAKE -> ACTIVE.add(new FxMobs.Quake(p));
            case WARP -> ACTIVE.add(new FxMobs.Warp(p));
        }
    }

    /** CANCEL : coupe les effets d'un type, soit ceux qui suivent une entite, soit ceux qui ont une graine donnee. */
    private static void cancel(VfxPacket p) {
        VfxType wanted = VfxType.byId((int) p.param);
        for (Fx fx : ACTIVE) {
            if (fx.type != wanted) {
                continue;
            }
            if (p.entityId >= 0 ? fx.entityId == p.entityId : fx.seed == p.seed) {
                fx.age = fx.duration;
            }
        }
    }

    public static void tick() {
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        Iterator<Fx> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Fx fx = it.next();
            fx.age++;
            if (fx.finished()) {
                it.remove();
            }
        }
    }

    private VfxManager() {}
}
