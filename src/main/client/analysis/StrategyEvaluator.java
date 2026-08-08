package client.analysis;

import algorithm.Strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Evaluates strategies with repeated, paired Monte Carlo samples.
 */
public final class StrategyEvaluator {
    private static final double TIE_EPSILON = 1e-9;
    private static final double CONFIDENCE_95_Z = 1.96;

    private StrategyEvaluator() {
    }

    /**
     * Produces averaged scores using a generated seed.
     */
    public static List<StrategyScore> evaluate(List<StrategyCatalog.StrategySpec> strategies,
                                               int repeats,
                                               int simulations,
                                               int playersPerGame) {
        return evaluate(strategies, repeats, simulations, playersPerGame, new Random());
    }

    /**
     * Produces averaged scores using common game seeds for every strategy in each repeat.
     *
     * <p>Common random numbers make the comparison invariant to catalog ordering
     * and reduce noise in pairwise strategy differences.</p>
     */
    public static List<StrategyScore> evaluate(List<StrategyCatalog.StrategySpec> strategies,
                                               int repeats,
                                               int simulations,
                                               int playersPerGame,
                                               Random seedGenerator) {
        return evaluate(strategies, repeats, simulations, playersPerGame, seedGenerator, ignored -> {
        });
    }

    /**
     * Produces paired scores and reports each completed repeat to a callback.
     */
    public static List<StrategyScore> evaluate(List<StrategyCatalog.StrategySpec> strategies,
                                               int repeats,
                                               int simulations,
                                               int playersPerGame,
                                               Random seedGenerator,
                                               IntConsumer completedRepeatConsumer) {
        validateInputs(strategies, repeats, simulations, playersPerGame, seedGenerator);
        Objects.requireNonNull(completedRepeatConsumer, "completedRepeatConsumer");

        List<StrategyScore> scores = new ArrayList<>(strategies.size());
        for (StrategyCatalog.StrategySpec spec : strategies) {
            scores.add(new StrategyScore(spec.name(), spec.factory()));
        }

        for (int run = 0; run < repeats; run++) {
            long[] gameSeeds = StrategySimulator.createGameSeeds(simulations, seedGenerator);
            double bestAverage = Double.NEGATIVE_INFINITY;
            List<StrategyScore> runWinners = new ArrayList<>();

            for (StrategyScore score : scores) {
                StrategySimulator.SimulationStats result = StrategySimulator.simulateTreasureStats(
                        score.factory,
                        playersPerGame,
                        gameSeeds
                );
                score.recordRun(result);

                if (result.averageTreasure() > bestAverage + TIE_EPSILON) {
                    bestAverage = result.averageTreasure();
                    runWinners.clear();
                    runWinners.add(score);
                } else if (Math.abs(result.averageTreasure() - bestAverage) <= TIE_EPSILON) {
                    runWinners.add(score);
                }
            }

            for (StrategyScore winner : runWinners) {
                winner.recordWin();
            }
            completedRepeatConsumer.accept(run + 1);
        }

        for (StrategyScore score : scores) {
            score.finalizeStatistics();
        }
        return scores;
    }

    private static void validateInputs(List<StrategyCatalog.StrategySpec> strategies,
                                       int repeats,
                                       int simulations,
                                       int playersPerGame,
                                       Random seedGenerator) {
        Objects.requireNonNull(strategies, "strategies");
        Objects.requireNonNull(seedGenerator, "seedGenerator");
        if (strategies.isEmpty()) {
            throw new IllegalArgumentException("strategies cannot be empty");
        }
        if (repeats <= 0) {
            throw new IllegalArgumentException("repeats must be positive: " + repeats);
        }
        if (simulations <= 0) {
            throw new IllegalArgumentException("simulations must be positive: " + simulations);
        }
        if (playersPerGame <= 0) {
            throw new IllegalArgumentException("playersPerGame must be positive: " + playersPerGame);
        }

        Set<String> names = new HashSet<>();
        for (StrategyCatalog.StrategySpec spec : strategies) {
            Objects.requireNonNull(spec, "strategies cannot contain null");
            if (!names.add(spec.name())) {
                throw new IllegalArgumentException("Duplicate strategy name: " + spec.name());
            }
        }
    }

    /**
     * Aggregated result for one strategy.
     */
    public static class StrategyScore {
        public final String name;
        public final Supplier<Strategy> factory;
        public double average;
        public double standardError;
        public double confidence95Margin;
        public int wins;
        public int runs;

        private long sampleCount;
        private double sampleMean;
        private double squaredDeviationSum;
        private final List<Double> runAverages = new ArrayList<>();

        /**
         * Creates an empty strategy score accumulator.
         */
        public StrategyScore(String name, Supplier<Strategy> factory) {
            this.name = Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name cannot be blank");
            }
            this.factory = Objects.requireNonNull(factory, "factory");
        }

        private void recordRun(StrategySimulator.SimulationStats result) {
            mergeSamples(
                    result.simulations(),
                    result.averageTreasure(),
                    result.standardDeviation()
            );
            runAverages.add(result.averageTreasure());
            runs++;
        }

        /**
         * Compares this score with another score using their paired batch averages.
         */
        public PairedDifferenceStats pairedDifferenceFrom(StrategyScore comparison) {
            Objects.requireNonNull(comparison, "comparison");
            if (runAverages.size() != comparison.runAverages.size()) {
                throw new IllegalArgumentException("Scores must contain the same number of paired batches");
            }
            if (runAverages.size() < 2) {
                throw new IllegalStateException("At least two paired batches are required");
            }

            int count = 0;
            double meanDifference = 0.0;
            double squaredDeviation = 0.0;
            for (int index = 0; index < runAverages.size(); index++) {
                double difference = runAverages.get(index) - comparison.runAverages.get(index);
                count++;
                double delta = difference - meanDifference;
                meanDifference += delta / count;
                squaredDeviation += delta * (difference - meanDifference);
            }
            double variance = squaredDeviation / (count - 1);
            double standardError = Math.sqrt(Math.max(0.0, variance)) / Math.sqrt(count);
            return new PairedDifferenceStats(meanDifference, standardError, count);
        }

        private void mergeSamples(long addedCount, double addedMean, double addedStandardDeviation) {
            double addedSquaredDeviation =
                    Math.max(0, addedCount - 1) * addedStandardDeviation * addedStandardDeviation;
            if (sampleCount == 0) {
                sampleCount = addedCount;
                sampleMean = addedMean;
                squaredDeviationSum = addedSquaredDeviation;
                return;
            }

            long combinedCount = sampleCount + addedCount;
            double delta = addedMean - sampleMean;
            squaredDeviationSum += addedSquaredDeviation
                    + delta * delta * sampleCount * addedCount / combinedCount;
            sampleMean += delta * addedCount / combinedCount;
            sampleCount = combinedCount;
        }

        private void recordWin() {
            wins++;
        }

        private void finalizeStatistics() {
            average = sampleMean;
            double variance = sampleCount > 1
                    ? squaredDeviationSum / (sampleCount - 1)
                    : 0.0;
            standardError = sampleCount == 0
                    ? 0.0
                    : Math.sqrt(Math.max(0.0, variance)) / Math.sqrt(sampleCount);
            confidence95Margin = CONFIDENCE_95_Z * standardError;
        }
    }

    /**
     * Sampling statistics for differences between paired batch averages.
     */
    public record PairedDifferenceStats(double meanDifference,
                                        double standardError,
                                        int batches) {
        /**
         * Returns the normal-approximation 95% confidence-interval margin.
         */
        public double confidence95Margin() {
            return CONFIDENCE_95_Z * standardError;
        }
    }
}
