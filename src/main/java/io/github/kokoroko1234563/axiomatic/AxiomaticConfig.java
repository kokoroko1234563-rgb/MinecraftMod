package io.github.kokoroko1234563.axiomatic;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-wide options, in config/axiomatic-common.toml. */
public final class AxiomaticConfig {
    private AxiomaticConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISASTER_BLOCK_DAMAGE = BUILDER
            .comment("Whether a refuted conjecture's disaster may destroy blocks. Off by default.")
            .define("disasterBlockDamage", false);

    public static final ModConfigSpec.IntValue CONJECTURE_UPKEEP = BUILDER
            .comment("Energy a conjecture block uses every 20 ticks while it is active.")
            .defineInRange("conjectureUpkeep", 20, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue CABLE_LOSS = BUILDER
            .comment("Fraction of energy lost per cable on the way to a machine, unless the network is a certified tree.")
            .defineInRange("cableLossPerBlock", 0.02, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue MAX_CABLE_LOSS = BUILDER
            .comment("The most energy a long cable can lose, as a fraction.")
            .defineInRange("maxCableLoss", 0.5, 0.0, 1.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
