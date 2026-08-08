package test;

import algorithm.AlwaysContinueStrategy;
import algorithm.LeaveAfterTurnsStrategy;
import algorithm.RiskAverseStrategy;
import client.analysis.StrategyCatalog;
import client.analysis.StrategyEvaluator;
import client.analysis.StrategyInteractionEvaluator;
import client.analysis.StrategyRatings;
import client.analysis.StrategySimulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyAnalysisTest {
    @TempDir
    private Path tempDir;

    @Test
    void simulatorRejectsInvalidCountsInsteadOfReturningMisleadingZeros() {
        assertThrows(IllegalArgumentException.class,
                () -> StrategySimulator.simulateAverageTreasure(AlwaysContinueStrategy::new, 0, 4));
        assertThrows(IllegalArgumentException.class,
                () -> StrategySimulator.simulateAverageTreasure(AlwaysContinueStrategy::new, 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> StrategySimulator.simulateMatchup(
                        AlwaysContinueStrategy::new,
                        RiskAverseStrategy::new,
                        10,
                        4,
                        0));
        assertThrows(IllegalArgumentException.class,
                () -> StrategySimulator.simulateMatchupAgainstField(
                        AlwaysContinueStrategy::new,
                        List.of(RiskAverseStrategy::new),
                        0,
                        4,
                        new Random(0)));
        assertThrows(IllegalArgumentException.class,
                () -> StrategySimulator.simulateAverageTurnsUntilDoubleHazard(0));
    }

    @Test
    void seededSimulatorRunsAreRepeatable() {
        double firstAverage = StrategySimulator.simulateAverageTreasure(
                () -> new LeaveAfterTurnsStrategy(4),
                50,
                4,
                new Random(12345));
        double secondAverage = StrategySimulator.simulateAverageTreasure(
                () -> new LeaveAfterTurnsStrategy(4),
                50,
                4,
                new Random(12345));
        assertEquals(firstAverage, secondAverage);

        StrategySimulator.MatchupStats firstMatchup = StrategySimulator.simulateMatchup(
                () -> new LeaveAfterTurnsStrategy(4),
                RiskAverseStrategy::new,
                50,
                4,
                1,
                new Random(12345));
        StrategySimulator.MatchupStats secondMatchup = StrategySimulator.simulateMatchup(
                () -> new LeaveAfterTurnsStrategy(4),
                RiskAverseStrategy::new,
                50,
                4,
                1,
                new Random(12345));
        assertEquals(firstMatchup.averageTreasure(), secondMatchup.averageTreasure());
        assertEquals(firstMatchup.winRate(), secondMatchup.winRate());

        double firstTurns = StrategySimulator.simulateAverageTurnsUntilDoubleHazard(50, new Random(12345));
        double secondTurns = StrategySimulator.simulateAverageTurnsUntilDoubleHazard(50, new Random(12345));
        assertEquals(firstTurns, secondTurns);
    }

    @Test
    void simulatorReportsGameLevelUncertainty() {
        long[] seeds = StrategySimulator.createGameSeeds(100, new Random(12345));
        StrategySimulator.SimulationStats stats = StrategySimulator.simulateTreasureStats(
                () -> new LeaveAfterTurnsStrategy(7),
                4,
                seeds
        );

        assertEquals(100, stats.simulations());
        assertTrue(stats.averageTreasure() >= 0.0);
        assertTrue(stats.standardDeviation() >= 0.0);
        assertTrue(stats.standardError() >= 0.0);
        assertEquals(stats.standardError() * 1.96, stats.confidence95Margin(), 1e-12);
    }

    @Test
    void defaultCatalogIncludesHazardMemoryStrategies() {
        assertTrue(StrategyCatalog.buildDefaultStrategies().stream()
                .anyMatch(spec -> spec.name().contains("with memory")));
    }

    @Test
    void validationCatalogExpandsTheTreasureTurnNeighborhoodWithoutDuplicates() {
        List<StrategyCatalog.StrategySpec> defaults = StrategyCatalog.buildDefaultStrategies();
        List<StrategyCatalog.StrategySpec> validation = StrategyCatalog.buildValidationStrategies();
        long uniqueNames = validation.stream().map(StrategyCatalog.StrategySpec::name).distinct().count();

        assertTrue(validation.size() > defaults.size());
        assertEquals(validation.size(), uniqueNames);
        assertTrue(validation.stream()
                .anyMatch(spec -> spec.name().equals("Leave after 4 treasure or 4 turns")));
        assertTrue(validation.stream()
                .anyMatch(spec -> spec.name().equals("Leave after 12 treasure or 12 turns")));
    }

    @Test
    void evaluatorTracksTiesAndRejectsInvalidRuns() {
        List<StrategyCatalog.StrategySpec> strategies = List.of(
                new StrategyCatalog.StrategySpec("Turns A", () -> new LeaveAfterTurnsStrategy(4)),
                new StrategyCatalog.StrategySpec("Turns B", () -> new LeaveAfterTurnsStrategy(4))
        );

        List<StrategyEvaluator.StrategyScore> tieScores =
                StrategyEvaluator.evaluate(strategies, 3, 20, 2, new Random(0));
        for (StrategyEvaluator.StrategyScore score : tieScores) {
            assertEquals(3, score.runs);
            assertEquals(3, score.wins);
            assertTrue(score.average >= 0.0);
            assertTrue(score.confidence95Margin >= 0.0);
        }
        assertEquals(tieScores.get(0).average, tieScores.get(1).average);
        StrategyEvaluator.PairedDifferenceStats tieDifference =
                tieScores.get(0).pairedDifferenceFrom(tieScores.get(1));
        assertEquals(0.0, tieDifference.meanDifference());
        assertEquals(0.0, tieDifference.standardError());
        assertEquals(3, tieDifference.batches());

        assertThrows(IllegalArgumentException.class,
                () -> StrategyEvaluator.evaluate(strategies, 0, 1, 2, new Random(0)));
        assertThrows(IllegalArgumentException.class,
                () -> StrategyEvaluator.evaluate(strategies, 1, 0, 2, new Random(0)));
    }

    @Test
    void pairedEvaluatorResultsDoNotDependOnCatalogOrder() {
        List<StrategyCatalog.StrategySpec> forward = List.of(
                new StrategyCatalog.StrategySpec("Always", AlwaysContinueStrategy::new),
                new StrategyCatalog.StrategySpec("Risk", RiskAverseStrategy::new),
                new StrategyCatalog.StrategySpec("Turns", () -> new LeaveAfterTurnsStrategy(7))
        );
        List<StrategyCatalog.StrategySpec> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        Map<String, StrategyEvaluator.StrategyScore> forwardScores = StrategyEvaluator
                .evaluate(forward, 3, 50, 4, new Random(12345))
                .stream()
                .collect(Collectors.toMap(score -> score.name, Function.identity()));
        Map<String, StrategyEvaluator.StrategyScore> reverseScores = StrategyEvaluator
                .evaluate(reverse, 3, 50, 4, new Random(12345))
                .stream()
                .collect(Collectors.toMap(score -> score.name, Function.identity()));

        for (String name : forwardScores.keySet()) {
            assertEquals(forwardScores.get(name).average, reverseScores.get(name).average);
            assertEquals(forwardScores.get(name).wins, reverseScores.get(name).wins);
            assertEquals(forwardScores.get(name).confidence95Margin,
                    reverseScores.get(name).confidence95Margin);
        }

        StrategyEvaluator.PairedDifferenceStats forwardDifference =
                forwardScores.get("Turns").pairedDifferenceFrom(forwardScores.get("Risk"));
        StrategyEvaluator.PairedDifferenceStats reverseDifference =
                reverseScores.get("Turns").pairedDifferenceFrom(reverseScores.get("Risk"));
        assertEquals(forwardDifference.meanDifference(), reverseDifference.meanDifference());
        assertEquals(forwardDifference.standardError(), reverseDifference.standardError());
    }

    @Test
    void interactionEvaluatorWritesSeededStructuredReportToInjectablePath() throws IOException {
        Path previousPath = StrategyInteractionEvaluator.getOutputPath();
        Path reportPath = tempDir.resolve("interactions.json");
        try {
            StrategyInteractionEvaluator.setOutputPath(reportPath);
            List<StrategyCatalog.StrategySpec> strategies = List.of(
                    new StrategyCatalog.StrategySpec("Always \"quoted\"", AlwaysContinueStrategy::new),
                    new StrategyCatalog.StrategySpec("Risk\\slash", RiskAverseStrategy::new)
            );

            Map<String, StrategyRatings.InteractionPerformance> results =
                    StrategyInteractionEvaluator.evaluateAndWrite(strategies, 2, 2, 12345L);

            assertEquals(2, results.size());
            JsonObject report = JsonParser.parseString(Files.readString(reportPath, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals(2, report.get("simulations").getAsInt());
            assertEquals(2, report.get("playersPerGame").getAsInt());
            assertEquals(12345L, report.get("seed").getAsLong());
            assertTrue(report.get("randomization").getAsString().contains("common game seeds"));
            assertTrue(report.get("winRateDefinition").getAsString().contains("artifact-count"));
            assertEquals(2, report.getAsJsonArray("strategies").size());
            assertTrue(report.has("mostAffected"));
        } finally {
            StrategyInteractionEvaluator.setOutputPath(previousPath);
        }
    }

    @Test
    void interactionEvaluatorRejectsAmbiguousStrategyCatalogs() {
        StrategyCatalog.StrategySpec first = new StrategyCatalog.StrategySpec(
                "Duplicate",
                AlwaysContinueStrategy::new
        );
        StrategyCatalog.StrategySpec second = new StrategyCatalog.StrategySpec(
                "Duplicate",
                RiskAverseStrategy::new
        );

        assertThrows(IllegalArgumentException.class, () ->
                StrategyInteractionEvaluator.evaluateAndWrite(List.of(first, second), 1, 2, 12345L));
        assertThrows(NullPointerException.class, () ->
                StrategyInteractionEvaluator.evaluateAndWrite(
                        Arrays.asList(first, null),
                        1,
                        2,
                        12345L
                ));
    }

    @Test
    void strategyRatingsUseAnInjectablePathAndStructuredJson() throws IOException {
        Path previousPath = StrategyRatings.getRatingsPath();
        Path ratingsPath = tempDir.resolve("strategy-ratings.json");
        try {
            StrategyRatings.setRatingsPath(ratingsPath);

            List<StrategyRatings.StrategyPerformance> firstRun = new ArrayList<>();
            firstRun.add(new StrategyRatings.StrategyPerformance("Alpha", 10.0, 3, 4));
            firstRun.add(new StrategyRatings.StrategyPerformance("Beta", 5.0, 1, 4));
            StrategyRatings.updateRatings(firstRun, "test-run-1");

            JsonObject firstAlpha = findStrategy(ratingsPath, "Alpha");
            JsonObject firstBeta = findStrategy(ratingsPath, "Beta");
            assertEquals(5.0, firstAlpha.get("rating").getAsDouble(), 1e-6);
            assertEquals(0.0, firstBeta.get("rating").getAsDouble(), 1e-6);
            assertEquals(75.0, firstAlpha.get("winRate").getAsDouble(), 1e-6);
            assertEquals(25.0, firstBeta.get("winRate").getAsDouble(), 1e-6);

            List<StrategyRatings.StrategyPerformance> secondRun = new ArrayList<>();
            secondRun.add(new StrategyRatings.StrategyPerformance("Beta", 12.0, 3, 4));
            secondRun.add(new StrategyRatings.StrategyPerformance("Alpha", 6.0, 1, 4));
            StrategyRatings.updateRatings(secondRun, "test-run-2");

            JsonObject secondAlpha = findStrategy(ratingsPath, "Alpha");
            JsonObject secondBeta = findStrategy(ratingsPath, "Beta");
            assertEquals(2.5, secondAlpha.get("rating").getAsDouble(), 1e-6);
            assertEquals(2.5, secondBeta.get("rating").getAsDouble(), 1e-6);
        } finally {
            StrategyRatings.setRatingsPath(previousPath);
        }
    }

    @Test
    void strategyRatingsIgnoreEmptyUpdatesAndClampInteractionWinRates() throws IOException {
        Path previousPath = StrategyRatings.getRatingsPath();
        Path ratingsPath = tempDir.resolve("strategy-ratings-clamped.json");
        try {
            StrategyRatings.setRatingsPath(ratingsPath);
            StrategyRatings.updateRatings(null, "null");
            StrategyRatings.updateRatings(List.of(), "empty");
            assertFalse(Files.exists(ratingsPath));

            Map<String, StrategyRatings.InteractionPerformance> interactions = new HashMap<>();
            interactions.put("Alpha", new StrategyRatings.InteractionPerformance("Alpha", 1.0, 150.0));
            StrategyRatings.updateRatings(
                    List.of(new StrategyRatings.StrategyPerformance("Alpha", 1.0, 1, 1)),
                    "clamp",
                    interactions,
                    true);

            JsonObject alpha = findStrategy(ratingsPath, "Alpha");
            assertEquals(100.0, alpha.get("interactionWinRate").getAsDouble(), 1e-6);
        } finally {
            StrategyRatings.setRatingsPath(previousPath);
        }
    }

    @Test
    void strategyRatingsCanBlendInteractionResults() throws IOException {
        Path previousPath = StrategyRatings.getRatingsPath();
        Path ratingsPath = tempDir.resolve("strategy-ratings-interactions.json");
        try {
            StrategyRatings.setRatingsPath(ratingsPath);

            List<StrategyRatings.StrategyPerformance> performances = new ArrayList<>();
            performances.add(new StrategyRatings.StrategyPerformance("Alpha", 10.0, 4, 4));
            performances.add(new StrategyRatings.StrategyPerformance("Beta", 8.0, 2, 4));

            Map<String, StrategyRatings.InteractionPerformance> interactions = new HashMap<>();
            interactions.put("Alpha", new StrategyRatings.InteractionPerformance("Alpha", 0.0, 0.0));
            interactions.put("Beta", new StrategyRatings.InteractionPerformance("Beta", 20.0, 100.0));
            StrategyRatings.updateRatings(performances, "test-interactions", interactions, true);

            JsonObject alpha = findStrategy(ratingsPath, "Alpha");
            JsonObject beta = findStrategy(ratingsPath, "Beta");
            assertEquals(0.0, alpha.get("rating").getAsDouble(), 1e-6);
            assertEquals(5.0, beta.get("rating").getAsDouble(), 1e-6);
            assertEquals(30.0, alpha.get("winRate").getAsDouble(), 1e-6);
            assertEquals(85.0, beta.get("winRate").getAsDouble(), 1e-6);
        } finally {
            StrategyRatings.setRatingsPath(previousPath);
        }
    }

    @Test
    void currentRatingsCanReplaceStaleHistoricalResults() throws IOException {
        Path previousPath = StrategyRatings.getRatingsPath();
        Path ratingsPath = tempDir.resolve("strategy-ratings-current.json");
        try {
            StrategyRatings.setRatingsPath(ratingsPath);
            StrategyRatings.updateRatings(List.of(
                    new StrategyRatings.StrategyPerformance("Alpha", 10.0, 4, 4),
                    new StrategyRatings.StrategyPerformance("Beta", 5.0, 0, 4)
            ), "old");

            StrategyRatings.updateRatings(List.of(
                    new StrategyRatings.StrategyPerformance("Alpha", 5.0, 0, 4),
                    new StrategyRatings.StrategyPerformance("Beta", 10.0, 4, 4)
            ), "current", Map.of(), false, false);

            JsonObject document = JsonParser.parseString(Files.readString(ratingsPath)).getAsJsonObject();
            assertFalse(document.get("historyBlended").getAsBoolean());
            assertEquals(1.0, document.get("ratingWeight").getAsDouble());
            assertEquals(0.0, findStrategy(ratingsPath, "Alpha").get("rating").getAsDouble(), 1e-6);
            assertEquals(5.0, findStrategy(ratingsPath, "Beta").get("rating").getAsDouble(), 1e-6);
        } finally {
            StrategyRatings.setRatingsPath(previousPath);
        }
    }

    @Test
    void equalMetricsReceiveEqualRanksAndRatings() throws IOException {
        Path previousPath = StrategyRatings.getRatingsPath();
        Path ratingsPath = tempDir.resolve("strategy-ratings-ties.json");
        try {
            StrategyRatings.setRatingsPath(ratingsPath);
            StrategyRatings.updateRatings(List.of(
                    new StrategyRatings.StrategyPerformance("Alpha", 10.0, 3, 4),
                    new StrategyRatings.StrategyPerformance("Beta", 10.0, 3, 4),
                    new StrategyRatings.StrategyPerformance("Gamma", 5.0, 0, 4)
            ), "ties", Map.of(), false, false);

            JsonObject alpha = findStrategy(ratingsPath, "Alpha");
            JsonObject beta = findStrategy(ratingsPath, "Beta");
            assertEquals(alpha.get("rating").getAsDouble(), beta.get("rating").getAsDouble(), 1e-9);
            assertEquals(alpha.get("ratingRank").getAsInt(), beta.get("ratingRank").getAsInt());
            assertEquals(alpha.get("scoreRank").getAsInt(), beta.get("scoreRank").getAsInt());
        } finally {
            StrategyRatings.setRatingsPath(previousPath);
        }
    }

    private static JsonObject findStrategy(Path ratingsPath, String name) throws IOException {
        String content = Files.readString(ratingsPath, StandardCharsets.UTF_8);
        JsonArray strategies = JsonParser.parseString(content)
                .getAsJsonObject()
                .getAsJsonArray("strategies");
        for (int i = 0; i < strategies.size(); i++) {
            JsonObject strategy = strategies.get(i).getAsJsonObject();
            if (name.equals(strategy.get("name").getAsString())) {
                return strategy;
            }
        }
        throw new AssertionError("Missing strategy " + name);
    }
}
