package com.omnimine.net;

/** Identifiants des effets visuels envoyes du serveur vers les clients. */
public enum VfxType {
    // ---- minage ----
    /** Fissures progressives sur la cible en cours de minage (param = progression 0..1). */
    MINE_CRACK,
    /** La cible minee est aspiree vers le joueur (entityId = joueur, param = taille). */
    ABSORB,
    /** Trou dans le ciel la ou etait le soleil (param 0, direction dans x2,y2,z2) ou la lune (param 1). */
    SKY_HOLE,
    /** Le ciel se referme : le soleil ou la lune reviennent. */
    SKY_RETURN,
    /** Dissolution d'une structure minee (x2,y2,z2 = demi-dimensions de la zone). */
    DISSOLVE,
    /** Eclair capture, fige dans le ciel en attendant d'etre mine. */
    FROZEN_BOLT,
    /** Eclair mine qui eclate. */
    BOLT_SHATTER,
    /** Ceremonie d'ouverture d'une structure evoluee. */
    REWARD,

    // ---- epee du soleil ----
    /** Coup de l'epee du soleil. */
    SUN_FLARE,
    /** Rayon solaire orbital. */
    SUN_BEAM,

    // ---- bottes lunaires ----
    /** Saut lunaire. */
    MOON_LEAP,
    /** Impact lunaire. */
    MOON_SLAM,

    // ---- armure d'eclair ----
    /** Foudre du ciel (casque). */
    SMITE,
    /** Arc electrique entre deux points (casque). */
    ARC,
    /** Petit eclair (casque). */
    MINI,
    /** Decharge statique en sphere (plastron). */
    NOVA,
    /** Trainee electrique (jambieres). */
    ZAP,

    // ---- pouvoirs des mobs ----
    BONE_RAIN,
    BLAST,
    ROT,
    WEB,
    LASER,
    SONIC,
    QUAKE,
    WARP,

    /** Supprime les effets d'un type donne attaches a une entite. */
    CANCEL;

    public static VfxType byId(int id) {
        VfxType[] all = values();
        return id >= 0 && id < all.length ? all[id] : CANCEL;
    }
}
