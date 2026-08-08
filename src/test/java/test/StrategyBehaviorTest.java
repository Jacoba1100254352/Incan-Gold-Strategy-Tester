package test;

import algorithm.AlwaysContinueStrategy;
import algorithm.ArtifactChaserStrategy;
import algorithm.ArtifactOpportunistStrategy;
import algorithm.ArtifactSoloExitStrategy;
import algorithm.ArtifactValueRiskStrategy;
import algorithm.LeaveAfterHazardRiskStrategy;
import algorithm.LeaveAfterHazardsOrTurnsStrategy;
import algorithm.LeaveAfterHazardsStrategy;
import algorithm.LeaveAfterHazardsWithMemoryStrategy;
import algorithm.LeaveAfterTempleTreasureStrategy;
import algorithm.LeaveAfterTreasureOrHazardsStrategy;
import algorithm.LeaveAfterTreasureOrTurnsStrategy;
import algorithm.LeaveAfterTreasureStrategy;
import algorithm.LeaveAfterTurnsStrategy;
import algorithm.LeaveWhenSoloStrategy;
import algorithm.RiskAverseStrategy;
import algorithm.SwitchAfterHazardsForTurnsStrategy;
import algorithm.SwitchAfterHazardsStrategy;
import model.Hazard;
import model.RoundState;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyBehaviorTest {
    @Test
    void thresholdStrategiesLeaveAtTheirConfiguredLimits() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        RoundState base = new RoundState(2, 3, 4, 5, counts, 0);

        assertTrue(new AlwaysContinueStrategy().shouldContinue(base));
        assertTrue(new RiskAverseStrategy().shouldContinue(base));
        assertTrue(new LeaveAfterHazardsStrategy(2).shouldContinue(base));
        assertTrue(new LeaveAfterTurnsStrategy(3).shouldContinue(base));
        assertTrue(new LeaveAfterTreasureStrategy(6).shouldContinue(base));
        assertTrue(new LeaveAfterTempleTreasureStrategy(5).shouldContinue(base));
        assertTrue(new LeaveWhenSoloStrategy().shouldContinue(base));

        counts.put(Hazard.SNAKE, 1);
        RoundState oneHazard = new RoundState(2, 3, 4, 5, counts, 0);
        assertFalse(new RiskAverseStrategy().shouldContinue(oneHazard));

        counts.put(Hazard.SPIDER, 1);
        RoundState twoHazards = new RoundState(2, 3, 4, 5, counts, 0);
        assertFalse(new LeaveAfterHazardsStrategy(2).shouldContinue(twoHazards));
        assertFalse(new LeaveAfterHazardsOrTurnsStrategy(2, 3).shouldContinue(twoHazards));
        assertFalse(new LeaveAfterTreasureOrHazardsStrategy(6, 2).shouldContinue(twoHazards));

        assertFalse(new LeaveAfterTurnsStrategy(3)
                .shouldContinue(new RoundState(3, 3, 4, 5, counts, 0)));
        assertFalse(new LeaveAfterTreasureStrategy(6)
                .shouldContinue(new RoundState(2, 3, 4, 6, counts, 0)));
        assertFalse(new LeaveAfterTempleTreasureStrategy(5)
                .shouldContinue(new RoundState(2, 3, 5, 5, counts, 0)));
        assertFalse(new LeaveAfterTreasureOrTurnsStrategy(6, 3)
                .shouldContinue(new RoundState(3, 3, 4, 6, counts, 0)));
        assertFalse(new LeaveWhenSoloStrategy()
                .shouldContinue(new RoundState(2, 1, 0, 0, counts, 0)));
    }

    @Test
    void switchingStrategiesTrackHazardTriggers() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        counts.put(Hazard.SNAKE, 1);

        SwitchAfterHazardsStrategy switchStrategy = new SwitchAfterHazardsStrategy(
                1,
                new AlwaysContinueStrategy(),
                new LeaveAfterTurnsStrategy(2)
        );
        assertTrue(switchStrategy.shouldContinue(new RoundState(10, 2, 0, 0,
                new EnumMap<>(Hazard.class), 0)));
        assertFalse(switchStrategy.shouldContinue(new RoundState(2, 2, 0, 0, counts, 0)));

        SwitchAfterHazardsForTurnsStrategy stayAfterHazard = new SwitchAfterHazardsForTurnsStrategy(1, 2);
        assertTrue(stayAfterHazard.shouldContinue(new RoundState(3, 2, 0, 0, counts, 0)));
        assertTrue(stayAfterHazard.shouldContinue(new RoundState(4, 2, 0, 0, counts, 0)));
        assertFalse(stayAfterHazard.shouldContinue(new RoundState(5, 2, 0, 0, counts, 0)));
        assertTrue(stayAfterHazard.shouldContinue(new RoundState(1, 2, 0, 0,
                new EnumMap<>(Hazard.class), 0)));
    }

    @Test
    void artifactStrategiesUseSoloAndRiskContext() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        Map<Hazard, Integer> copies = TestFixtures.hazardCopies(3);

        assertFalse(new ArtifactSoloExitStrategy()
                .shouldContinue(new RoundState(1, 1, 0, 0, counts, copies, 1, 0)));
        assertTrue(new ArtifactSoloExitStrategy()
                .shouldContinue(new RoundState(1, 3, 0, 0, counts, copies, 1, 0)));

        ArtifactOpportunistStrategy opportunist = new ArtifactOpportunistStrategy(
                1,
                2,
                5,
                1,
                new LeaveAfterTurnsStrategy(3)
        );
        assertFalse(opportunist.shouldContinue(new RoundState(2, 2, 0, 5, counts, copies, 1, 0)));
        assertTrue(opportunist.shouldContinue(new RoundState(2, 3, 0, 6, counts, copies, 1, 0)));

        Map<Hazard, Integer> hazardCounts = new EnumMap<>(Hazard.class);
        hazardCounts.put(Hazard.SNAKE, 1);
        ArtifactValueRiskStrategy riskStrategy = new ArtifactValueRiskStrategy(8, 2, 2,
                new LeaveAfterTurnsStrategy(7));
        assertFalse(riskStrategy.shouldContinue(new RoundState(2, 2, 0, 3,
                hazardCounts, copies, 1, 0)));
        assertTrue(riskStrategy.shouldContinue(new RoundState(2, 3, 0, 3,
                hazardCounts, copies, 1, 0)));

        ArtifactChaserStrategy chaser = new ArtifactChaserStrategy(7, 4, 1, 1, 2);
        assertFalse(chaser.shouldContinue(new RoundState(7, 2, 0, 0, counts, copies, 0, 0)));
        assertTrue(chaser.shouldContinue(new RoundState(7, 2, 0, 0, hazardCounts, copies, 1, 0)));
        assertFalse(chaser.shouldContinue(new RoundState(3, 1, 0, 0, counts, copies, 1, 0)));
    }

    @Test
    void hazardMemoryStrategyUsesRemainingCopyInformation() {
        Map<Hazard, Integer> lowCopies = TestFixtures.hazardCopies(3);
        lowCopies.put(Hazard.SNAKE, 1);
        lowCopies.put(Hazard.SPIDER, 1);

        Map<Hazard, Integer> twoHazards = new EnumMap<>(Hazard.class);
        twoHazards.put(Hazard.SNAKE, 1);
        twoHazards.put(Hazard.SPIDER, 1);

        LeaveAfterHazardsWithMemoryStrategy memoryStrategy =
                new LeaveAfterHazardsWithMemoryStrategy(2, 1, 1);

        assertTrue(memoryStrategy.shouldContinue(new RoundState(3, 2, 0, 0,
                twoHazards, lowCopies, 0, 0)));
        assertFalse(memoryStrategy.shouldContinue(new RoundState(3, 2, 0, 0,
                twoHazards, TestFixtures.hazardCopies(3), 0, 0)));
    }

    @Test
    void hazardRiskStrategyUsesCopyInformationAndHazardCeiling() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        counts.put(Hazard.SNAKE, 1);
        counts.put(Hazard.SPIDER, 1);

        LeaveAfterHazardRiskStrategy strategy = new LeaveAfterHazardRiskStrategy(2, 3);

        assertFalse(strategy.shouldContinue(new RoundState(4, 2, 0, 0,
                counts, 0)));

        Map<Hazard, Integer> lowRiskCopies = TestFixtures.hazardCopies(3);
        lowRiskCopies.put(Hazard.SNAKE, 1);
        lowRiskCopies.put(Hazard.SPIDER, 1);
        assertTrue(strategy.shouldContinue(new RoundState(4, 2, 0, 0,
                counts, lowRiskCopies, 0, 0)));

        Map<Hazard, Integer> threeHazards = new EnumMap<>(counts);
        threeHazards.put(Hazard.ROCK, 1);
        assertFalse(strategy.shouldContinue(new RoundState(4, 2, 0, 0,
                threeHazards, lowRiskCopies, 0, 0)));

        assertFalse(new LeaveAfterHazardRiskStrategy(-1, -1)
                .shouldContinue(new RoundState(0, 2, 0, 0, new EnumMap<>(Hazard.class), 0)));
    }
}
