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

    public static final ModConfigSpec SPEC = BUILDER.build();
}
