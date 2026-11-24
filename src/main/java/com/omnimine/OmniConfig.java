package com.omnimine;

import net.minecraftforge.common.ForgeConfigSpec;

/** Reglages (fichier config/omnimine-common.toml). Pratique pour tourner une video. */
public final class OmniConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MINING_SPEED;
    public static final ForgeConfigSpec.IntValue SUN_RETURN_SECONDS;
    public static final ForgeConfigSpec.IntValue MOON_RETURN_SECONDS;
    public static final ForgeConfigSpec.IntValue BOLT_FREEZE_SECONDS;
    public static final ForgeConfigSpec.IntValue BOLT_CAPTURE_RADIUS;
    public static final ForgeConfigSpec.BooleanValue BOLT_NEEDS_ROD;
    public static final ForgeConfigSpec.IntValue DISSOLVE_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.BooleanValue TERRAIN_DESTRUCTION;
    public static final ForgeConfigSpec.BooleanValue FORCE_NIGHT_WITHOUT_SUN;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("minage");
        MINING_SPEED = b.comment("Vitesse du minage des mobs, du ciel, des eclairs et des structures (2.0 = deux fois plus vite).")
                .defineInRange("miningSpeed", 1.0D, 0.1D, 20.0D);
        BOLT_FREEZE_SECONDS = b.comment("Duree pendant laquelle un eclair capture reste fige, avant de frapper pour de vrai.")
                .defineInRange("boltFreezeSeconds", 14, 3, 120);
        BOLT_CAPTURE_RADIUS = b.comment("Rayon autour d'un joueur portant la pioche (enchantee) dans lequel les eclairs sont captures.")
                .defineInRange("boltCaptureRadius", 64, 8, 256);
        BOLT_NEEDS_ROD = b.comment("true : seuls les vrais eclairs d'orage qui tombent sur un paratonnerre en cuivre sont captures"
                + " (recolte naturelle). false : tout eclair proche d'un joueur avec la pioche est capture.")
                .define("boltNeedsRod", true);
        DISSOLVE_BLOCKS_PER_TICK = b.comment("Vitesse de dissolution d'une structure minee (blocs retires par tick).")
                .defineInRange("dissolveBlocksPerTick", 2500, 200, 50000);
        b.pop();
        b.push("ciel");
        SUN_RETURN_SECONDS = b.comment("Temps avant que le soleil mine reapparaisse.")
                .defineInRange("sunReturnSeconds", 90, 10, 3600);
        MOON_RETURN_SECONDS = b.comment("Temps avant que la lune minee reapparaisse.")
                .defineInRange("moonReturnSeconds", 90, 10, 3600);
        FORCE_NIGHT_WITHOUT_SUN = b.comment("Sans soleil, la nuit tombe et reste jusqu'a son retour.")
                .define("forceNightWithoutSun", true);
        b.pop();
        b.push("pouvoirs");
        TERRAIN_DESTRUCTION = b.comment("Les pouvoirs detruisent le decor (cratere du rayon solaire, impact lunaire, explosions...).")
                .define("terrainDestruction", true);
        b.pop();
        SPEC = b.build();
    }

    private OmniConfig() {}
}
