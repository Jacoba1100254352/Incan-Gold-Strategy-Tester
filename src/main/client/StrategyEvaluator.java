package client;

import algorithm.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Evaluates strategies by running repeated simulations and averaging results.
 */
public class StrategyEvaluator {
    // Tolerance for treating averages as ties.
    private static final double TIE_EPSILON = 1e-9;

    /**
     * Produces averaged scores for each strategy spec.
     */
    public static List<StrategyScore> evaluate(List<StrategyCatalog.StrategySpec> strategies,
                                               int repeats,
                                               int simulations,
                                               int playersPerGame) {
        List<StrategyScore> scores = new ArrayList<>();
        for (StrategyCatalog.StrategySpec spec : strategies) {
            scores.add(new StrategyScore(spec.name, spec.factory));
        }

        for (int run = 0; run < repeats; run++) {
            double bestAverage = Double.NEGATIVE_INFINITY;
            List<StrategyScore> runWinners = new ArrayList<>();

            for (StrategyScore score : scores) {
                double average = StrategySimulator.simulateAverageTreasure(score.factory, simulations, playersPerGame);
                score.recordRun(average);

                if (average > bestAverage + TIE_EPSILON) {
                    bestAverage = average;
                    runWinners.clear();
                    runWinners.add(score);
                } else if (Math.abs(average - bestAverage) <= TIE_EPSILON) {
                    runWinners.add(score);
                }
            }

            for (StrategyScore winner : runWinners) {
                winner.recordWin();
            }
        }

        for (StrategyScore score : scores) {
            score.finalizeAverage();
        }
        return scores;
    }

    /**
     * Aggregated scoring result for a strategy.
     */
    public static class StrategyScore {
        public final String name;
        public final Supplier<Strategy> factory;
        public double average;
        public int wins;
        public int runs;
        private double totalAverage;

        public StrategyScore(String name, Supplier<Strategy> factory) {
            this.name = name;
            this.factory = factory;
        }

        private void recordRun(double average) {
            totalAverage += average;
            runs++;
        }

        private void recordWin() {
            wins++;
        }

        private void finalizeAverage() {
            average = runs == 0 ? 0.0 : totalAverage / runs;
        }
    }
}
