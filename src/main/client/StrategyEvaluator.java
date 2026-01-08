package client;

import algorithm.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Evaluates strategies by running repeated simulations and averaging results.
 */
public class StrategyEvaluator {
    /**
     * Produces averaged scores for each strategy spec.
     */
    public static List<StrategyScore> evaluate(List<StrategyCatalog.StrategySpec> strategies,
                                               int repeats,
                                               int simulations,
                                               int playersPerGame) {
        List<StrategyScore> scores = new ArrayList<>();
        for (StrategyCatalog.StrategySpec spec : strategies) {
            double totalAverage = 0;
            for (int run = 0; run < repeats; run++) {
                totalAverage += StrategySimulator.simulateAverageTreasure(spec.factory, simulations, playersPerGame);
            }
            scores.add(new StrategyScore(spec.name, spec.factory, totalAverage / repeats));
        }
        return scores;
    }

    /**
     * Aggregated scoring result for a strategy.
     */
    public static class StrategyScore {
        public final String name;
        public final Supplier<Strategy> factory;
        public final double average;

        public StrategyScore(String name, Supplier<Strategy> factory, double average) {
            this.name = name;
            this.factory = factory;
            this.average = average;
        }
    }
}
