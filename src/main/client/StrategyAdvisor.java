package client;

import algorithm.Strategy;
import model.RoundState;

import java.util.List;

/**
 * Chooses between continuing and leaving based on the strongest strategies.
 */
public class StrategyAdvisor {
    private final List<StrategyEvaluator.StrategyScore> scores;

    public StrategyAdvisor(List<StrategyEvaluator.StrategyScore> scores) {
        this.scores = scores;
    }

    /**
     * Builds an advisor using the default strategy catalog at the given difficulty.
     */
    public static StrategyAdvisor buildDefault(AIDifficulty difficulty, int playersPerGame) {
        List<StrategyCatalog.StrategySpec> strategies = StrategyCatalog.buildDefaultStrategies();
        List<StrategyEvaluator.StrategyScore> scores = StrategyEvaluator.evaluate(
                strategies,
                difficulty.getRepeats(),
                difficulty.getSimulations(),
                playersPerGame
        );
        return new StrategyAdvisor(scores);
    }

    /**
     * Evaluates the current round state and returns a continue/leave decision.
     */
    public Decision decide(RoundState state) {
        double bestContinueScore = Double.NEGATIVE_INFINITY;
        String bestContinueName = null;
        double bestLeaveScore = Double.NEGATIVE_INFINITY;
        String bestLeaveName = null;

        for (StrategyEvaluator.StrategyScore score : scores) {
            Strategy strategy = score.factory.get();
            if (strategy.shouldContinue(state)) {
                if (score.average > bestContinueScore) {
                    bestContinueScore = score.average;
                    bestContinueName = score.name;
                }
            } else if (score.average > bestLeaveScore) {
                bestLeaveScore = score.average;
                bestLeaveName = score.name;
            }
        }

        if (bestContinueName == null) {
            return new Decision(false, bestLeaveName, bestLeaveScore);
        }
        if (bestLeaveName == null) {
            return new Decision(true, bestContinueName, bestContinueScore);
        }

        if (bestContinueScore >= bestLeaveScore) {
            return new Decision(true, bestContinueName, bestContinueScore);
        }
        return new Decision(false, bestLeaveName, bestLeaveScore);
    }

    /**
     * Encapsulates a decision and the strategy that informed it.
     */
    public static class Decision {
        public final boolean shouldContinue;
        public final String strategyName;
        public final double score;

        public Decision(boolean shouldContinue, String strategyName, double score) {
            this.shouldContinue = shouldContinue;
            this.strategyName = strategyName;
            this.score = score;
        }
    }
}
