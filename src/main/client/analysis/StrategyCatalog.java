package client.analysis;

import algorithm.AlwaysContinueStrategy;
import algorithm.ArtifactChaserStrategy;
import algorithm.ArtifactOpportunistStrategy;
import algorithm.ArtifactSoloExitStrategy;
import algorithm.ArtifactValueRiskStrategy;
import algorithm.LeaveAfterHazardsOrTurnsStrategy;
import algorithm.LeaveAfterHazardsStrategy;
import algorithm.LeaveAfterHazardsWithMemoryStrategy;
import algorithm.LeaveAfterHazardRiskStrategy;
import algorithm.LeaveAfterTempleTreasureStrategy;
import algorithm.LeaveAfterTreasureOrHazardsStrategy;
import algorithm.LeaveAfterTreasureOrTurnsStrategy;
import algorithm.LeaveAfterTreasureStrategy;
import algorithm.LeaveAfterTurnsStrategy;
import algorithm.RiskAverseStrategy;
import algorithm.Strategy;
import algorithm.SwitchAfterHazardsStrategy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Centralized sweep configuration for strategy testing and AI selection.
 */
public final class StrategyCatalog {
    // Minimum hazards for the hazard-only sweep.
    private static final int HAZARD_SWEEP_MIN = 3;
    // Maximum hazards for the hazard-only sweep.
    private static final int HAZARD_SWEEP_MAX = 3;
    // Minimum turns for the turn-only sweep.
    private static final int TURN_SWEEP_MIN = 6;
    // Maximum turns for the turn-only sweep.
    private static final int TURN_SWEEP_MAX = 8;
    // Minimum treasure for the treasure-only sweep.
    private static final int TREASURE_SWEEP_MIN = 6;
    // Maximum treasure for the treasure-only sweep.
    private static final int TREASURE_SWEEP_MAX = 7;
    // Step size for the treasure-only sweep.
    private static final int TREASURE_SWEEP_STEP = 1;
    // Minimum temple treasure for the temple-treasure sweep.
    private static final int TEMPLE_TREASURE_MIN = 5;
    // Maximum temple treasure for the temple-treasure sweep.
    private static final int TEMPLE_TREASURE_MAX = 6;
    // Step size for the temple-treasure sweep.
    private static final int TEMPLE_TREASURE_STEP = 1;
    // Minimum hazards for the hazards-or-turns sweep.
    private static final int HAZARDS_OR_TURNS_HAZARD_MIN = 4;
    // Maximum hazards for the hazards-or-turns sweep.
    private static final int HAZARDS_OR_TURNS_HAZARD_MAX = 5;
    // Minimum turns for the hazards-or-turns sweep.
    private static final int HAZARDS_OR_TURNS_TURN_MIN = 7;
    // Maximum turns for the hazards-or-turns sweep.
    private static final int HAZARDS_OR_TURNS_TURN_MAX = 8;
    // Step size for the hazards-or-turns sweep.
    private static final int HAZARDS_OR_TURNS_TURN_STEP = 1;
    // Minimum treasure for the treasure-or-hazards sweep.
    private static final int TREASURE_OR_HAZARDS_TREASURE_MIN = 7;
    // Maximum treasure for the treasure-or-hazards sweep.
    private static final int TREASURE_OR_HAZARDS_TREASURE_MAX = 8;
    // Step size for the treasure-or-hazards sweep.
    private static final int TREASURE_OR_HAZARDS_TREASURE_STEP = 1;
    // Minimum hazards for the treasure-or-hazards sweep.
    private static final int TREASURE_OR_HAZARDS_HAZARD_MIN = 4;
    // Maximum hazards for the treasure-or-hazards sweep.
    private static final int TREASURE_OR_HAZARDS_HAZARD_MAX = 5;
    // Minimum treasure for the treasure-or-turns sweep.
    private static final int TREASURE_OR_TURNS_TREASURE_MIN = 7;
    // Maximum treasure for the treasure-or-turns sweep.
    private static final int TREASURE_OR_TURNS_TREASURE_MAX = 8;
    // Step size for the treasure-or-turns sweep.
    private static final int TREASURE_OR_TURNS_TREASURE_STEP = 1;
    // Minimum turns for the treasure-or-turns sweep.
    private static final int TREASURE_OR_TURNS_TURN_MIN = 7;
    // Maximum turns for the treasure-or-turns sweep.
    private static final int TREASURE_OR_TURNS_TURN_MAX = 8;
    // Step size for the treasure-or-turns sweep.
    private static final int TREASURE_OR_TURNS_TURN_STEP = 1;
    // Minimum hazards for the switch-after-hazards sweep.
    private static final int SWITCH_AFTER_HAZARDS_MIN = 1;
    // Maximum hazards for the switch-after-hazards sweep.
    private static final int SWITCH_AFTER_HAZARDS_MAX = 2;
    // Minimum turns for the switch-after-hazards sweep.
    private static final int SWITCH_AFTER_HAZARDS_TURN_MIN = 6;
    // Maximum turns for the switch-after-hazards sweep.
    private static final int SWITCH_AFTER_HAZARDS_TURN_MAX = 7;
    // Step size for the switch-after-hazards sweep.
    private static final int SWITCH_AFTER_HAZARDS_TURN_STEP = 1;
    // Risk-score thresholds for hazard-aware strategies.
    private static final int HAZARD_RISK_MIN = 2;
    // Maximum risk-score threshold for hazard-aware strategies.
    private static final int HAZARD_RISK_MAX = 3;
    // Minimum total hazards allowed for hazard-risk strategies.
    private static final int HAZARD_RISK_HAZARDS_MIN = 3;
    // Maximum total hazards allowed for hazard-risk strategies.
    private static final int HAZARD_RISK_HAZARDS_MAX = 4;
    private static final int VALIDATION_TREASURE_MIN = 4;
    private static final int VALIDATION_TREASURE_MAX = 12;
    private static final int VALIDATION_TURN_MIN = 4;
    private static final int VALIDATION_TURN_MAX = 12;

    private StrategyCatalog() {
    }

    /**
     * Builds the default set of strategies and sweep ranges.
     */
    public static List<StrategySpec> buildDefaultStrategies() {
        List<StrategySpec> strategies = new ArrayList<>();
        strategies.add(new StrategySpec("Leave after 1 hazard", RiskAverseStrategy::new));
        addHazardSweep(strategies, HAZARD_SWEEP_MIN, HAZARD_SWEEP_MAX);
        addTurnSweep(strategies, TURN_SWEEP_MIN, TURN_SWEEP_MAX);
        addTreasureSweep(strategies, TREASURE_SWEEP_MIN, TREASURE_SWEEP_MAX, TREASURE_SWEEP_STEP);
        addTempleTreasureSweep(strategies, TEMPLE_TREASURE_MIN, TEMPLE_TREASURE_MAX, TEMPLE_TREASURE_STEP);
        addHazardRiskStrategies(strategies, HAZARD_RISK_MIN, HAZARD_RISK_MAX,
                HAZARD_RISK_HAZARDS_MIN, HAZARD_RISK_HAZARDS_MAX);
        addHazardsOrTurnsSweep(strategies, HAZARDS_OR_TURNS_HAZARD_MIN, HAZARDS_OR_TURNS_HAZARD_MAX,
                HAZARDS_OR_TURNS_TURN_MIN, HAZARDS_OR_TURNS_TURN_MAX, HAZARDS_OR_TURNS_TURN_STEP);
        addTreasureOrHazardsSweep(strategies, TREASURE_OR_HAZARDS_TREASURE_MIN, TREASURE_OR_HAZARDS_TREASURE_MAX,
                TREASURE_OR_HAZARDS_TREASURE_STEP, TREASURE_OR_HAZARDS_HAZARD_MIN, TREASURE_OR_HAZARDS_HAZARD_MAX);
        addTreasureOrTurnsSweep(strategies, TREASURE_OR_TURNS_TREASURE_MIN, TREASURE_OR_TURNS_TREASURE_MAX,
                TREASURE_OR_TURNS_TREASURE_STEP, TREASURE_OR_TURNS_TURN_MIN, TREASURE_OR_TURNS_TURN_MAX,
                TREASURE_OR_TURNS_TURN_STEP);
        addSwitchAfterHazardsSweep(strategies, SWITCH_AFTER_HAZARDS_MIN, SWITCH_AFTER_HAZARDS_MAX,
                SWITCH_AFTER_HAZARDS_TURN_MIN, SWITCH_AFTER_HAZARDS_TURN_MAX, SWITCH_AFTER_HAZARDS_TURN_STEP);
        addHazardMemoryStrategies(strategies);
        addArtifactStrategies(strategies);

        return strategies;
    }

    /**
     * Builds the default catalog plus a broad neighborhood around treasure/turn thresholds.
     *
     * <p>This catalog is intended for periodic validation of the optimized default
     * ranges, not full pairwise interaction runs.</p>
     */
    public static List<StrategySpec> buildValidationStrategies() {
        Map<String, StrategySpec> uniqueStrategies = new LinkedHashMap<>();
        for (StrategySpec strategy : buildDefaultStrategies()) {
            uniqueStrategies.put(strategy.name(), strategy);
        }

        List<StrategySpec> treasureTurnGrid = new ArrayList<>();
        addTreasureOrTurnsSweep(
                treasureTurnGrid,
                VALIDATION_TREASURE_MIN,
                VALIDATION_TREASURE_MAX,
                1,
                VALIDATION_TURN_MIN,
                VALIDATION_TURN_MAX,
                1
        );
        for (StrategySpec strategy : treasureTurnGrid) {
            uniqueStrategies.putIfAbsent(strategy.name(), strategy);
        }
        return new ArrayList<>(uniqueStrategies.values());
    }

    /**
     * Describes a strategy name and factory for instantiation.
     */
    public record StrategySpec(String name, Supplier<Strategy> factory) {
        /**
         * Validates the strategy name and factory.
         */
        public StrategySpec {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(factory, "factory");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
        }
    }
    /**
     * Adds hazard sweep.
     */
    private static void addHazardSweep(List<StrategySpec> strategies, int min, int max) {
        for (int hazards = min; hazards <= max; hazards++) {
            final int threshold = hazards;
            strategies.add(new StrategySpec("Leave after " + hazards + " hazards",
                    () -> new LeaveAfterHazardsStrategy(threshold)));
        }
    }
    /**
     * Adds temple treasure sweep.
     */
    private static void addTempleTreasureSweep(List<StrategySpec> strategies, int min, int max, int step) {
        for (int treasure = min; treasure <= max; treasure += step) {
            final int threshold = treasure;
            strategies.add(new StrategySpec("Leave after temple treasure " + treasure,
                    () -> new LeaveAfterTempleTreasureStrategy(threshold)));
        }
    }
    /**
     * Adds turn sweep.
     */
    private static void addTurnSweep(List<StrategySpec> strategies, int min, int max) {
        for (int turns = min; turns <= max; turns++) {
            final int threshold = turns;
            strategies.add(new StrategySpec("Leave after " + turns + " turns",
                    () -> new LeaveAfterTurnsStrategy(threshold)));
        }
    }
    /**
     * Adds treasure sweep.
     */
    private static void addTreasureSweep(List<StrategySpec> strategies, int min, int max, int step) {
        for (int treasure = min; treasure <= max; treasure += step) {
            final int threshold = treasure;
            strategies.add(new StrategySpec("Leave after " + treasure + " treasure",
                    () -> new LeaveAfterTreasureStrategy(threshold)));
        }
    }
    /**
     * Adds hazards or turns sweep.
     */
    private static void addHazardsOrTurnsSweep(List<StrategySpec> strategies, int hazardMin, int hazardMax,
                                               int turnMin, int turnMax, int turnStep) {
        for (int hazards = hazardMin; hazards <= hazardMax; hazards++) {
            for (int turns = turnMin; turns <= turnMax; turns += turnStep) {
                final int hazardThreshold = hazards;
                final int turnThreshold = turns;
                strategies.add(new StrategySpec("Leave after " + hazards + " hazards or " + turns + " turns",
                        () -> new LeaveAfterHazardsOrTurnsStrategy(hazardThreshold, turnThreshold)));
            }
        }
    }
    /**
     * Adds treasure or hazards sweep.
     */
    private static void addTreasureOrHazardsSweep(List<StrategySpec> strategies, int treasureMin, int treasureMax,
                                                  int treasureStep, int hazardMin, int hazardMax) {
        for (int treasure = treasureMin; treasure <= treasureMax; treasure += treasureStep) {
            for (int hazards = hazardMin; hazards <= hazardMax; hazards++) {
                final int treasureThreshold = treasure;
                final int hazardThreshold = hazards;
                strategies.add(new StrategySpec("Leave after " + treasure + " treasure or " + hazards + " hazards",
                        () -> new LeaveAfterTreasureOrHazardsStrategy(treasureThreshold, hazardThreshold)));
            }
        }
    }
    /**
     * Adds treasure or turns sweep.
     */
    private static void addTreasureOrTurnsSweep(List<StrategySpec> strategies, int treasureMin, int treasureMax,
                                                int treasureStep, int turnMin, int turnMax, int turnStep) {
        for (int treasure = treasureMin; treasure <= treasureMax; treasure += treasureStep) {
            for (int turns = turnMin; turns <= turnMax; turns += turnStep) {
                final int treasureThreshold = treasure;
                final int turnThreshold = turns;
                strategies.add(new StrategySpec("Leave after " + treasure + " treasure or " + turns + " turns",
                        () -> new LeaveAfterTreasureOrTurnsStrategy(treasureThreshold, turnThreshold)));
            }
        }
    }
    /**
     * Adds hazard risk strategies.
     */
    private static void addHazardRiskStrategies(List<StrategySpec> strategies,
                                                int riskMin,
                                                int riskMax,
                                                int hazardsMin,
                                                int hazardsMax) {
        for (int risk = riskMin; risk <= riskMax; risk++) {
            int maxHazards = Math.min(hazardsMax, hazardsMin + (risk - riskMin));
            final int riskThreshold = risk;
            final int hazardLimit = maxHazards;
            strategies.add(new StrategySpec(
                    "Leave after hazard risk " + riskThreshold + " (max hazards " + hazardLimit + ")",
                    () -> new LeaveAfterHazardRiskStrategy(riskThreshold, hazardLimit)));
        }
    }
    /**
     * Adds switch after hazards sweep.
     */
    private static void addSwitchAfterHazardsSweep(List<StrategySpec> strategies, int hazardMin, int hazardMax,
                                                   int turnMin, int turnMax, int turnStep) {
        for (int hazards = hazardMin; hazards <= hazardMax; hazards++) {
            for (int turns = turnMin; turns <= turnMax; turns += turnStep) {
                final int hazardThreshold = hazards;
                final int turnThreshold = turns;
                strategies.add(new StrategySpec("Switch after " + hazards + " hazards (stay->leave after " + turns + " turns)",
                        () -> new SwitchAfterHazardsStrategy(
                                hazardThreshold,
                                new AlwaysContinueStrategy(),
                                new LeaveAfterTurnsStrategy(turnThreshold))));
            }
        }
    }
    /**
     * Adds artifact strategies.
     */
    private static void addArtifactStrategies(List<StrategySpec> strategies) {
        strategies.add(new StrategySpec("Leave when solo with artifact (base 7 turns)",
                () -> new ArtifactSoloExitStrategy(new LeaveAfterTurnsStrategy(7))));
        strategies.add(new StrategySpec("Artifact opportunist (<=2 players, base hazards 4 or 7 turns)",
                () -> new ArtifactOpportunistStrategy(1, 2, 5, 1,
                        new LeaveAfterHazardsOrTurnsStrategy(4, 7))));
        strategies.add(new StrategySpec("Artifact opportunist (<=3 players, 2+ artifacts, base 7 turns)",
                () -> new ArtifactOpportunistStrategy(2, 3, 4, 2,
                        new LeaveAfterTurnsStrategy(7))));
        strategies.add(new StrategySpec("Artifact value vs risk (bank 10, risk 2, <=2 players, base 7 turns)",
                () -> new ArtifactValueRiskStrategy(10, 2, 2,
                        new LeaveAfterTurnsStrategy(7))));
        strategies.add(new StrategySpec("Chase artifact (base 7/4, bonus +1/+1, <=2 players)",
                () -> new ArtifactChaserStrategy(7, 4, 1, 1, 2)));
        strategies.add(new StrategySpec("Chase artifact (base 7/4, bonus +2/+1, <=3 players)",
                () -> new ArtifactChaserStrategy(7, 4, 2, 1, 3)));
    }
    /**
     * Adds hazard memory strategies.
     */
    private static void addHazardMemoryStrategies(List<StrategySpec> strategies) {
        strategies.add(new StrategySpec("Leave after hazards with memory (base 4)",
                () -> new LeaveAfterHazardsWithMemoryStrategy(4, 1, 1)));
        strategies.add(new StrategySpec("Leave after hazards with memory (base 5)",
                () -> new LeaveAfterHazardsWithMemoryStrategy(5, 1, 1)));
    }
}
