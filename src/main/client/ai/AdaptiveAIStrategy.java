package client.ai;

import algorithm.Strategy;
import model.RoundState;

/**
 * AI strategy that adapts decisions using a precomputed advisor table.
 */
public class AdaptiveAIStrategy implements Strategy {
    private final String name;
    private final StrategyAdvisor advisor;
    private final boolean verbose;

    /**
     * Creates an adaptive AI strategy backed by a strategy advisor.
     *
     * @param name display name for logs
     * @param advisor advisor that ranks strategies
     * @param verbose whether to log decision details
     */
    public AdaptiveAIStrategy(String name, StrategyAdvisor advisor, boolean verbose) {
        this.name = name;
        this.advisor = advisor;
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        StrategyAdvisor.Decision decision = advisor.decide(state);
        if (verbose) {
            System.out.printf(
                    "%s chooses to %s (best strategy: %s, score %.2f)%n",
                    name,
                    decision.shouldContinue ? "continue" : "leave",
                    decision.strategyName,
                    decision.score
            );
        }
        return decision.shouldContinue;
    }
}
