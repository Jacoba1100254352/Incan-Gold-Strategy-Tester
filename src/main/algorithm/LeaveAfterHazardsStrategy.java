package algorithm;

import model.RoundState;

/**
 * Strategy that leaves once a hazard count threshold is reached.
 */
public class LeaveAfterHazardsStrategy implements Strategy {
    private final int maxHazards;

    /**
     * Creates a strategy with the maximum hazard count to tolerate.
     *
     * @param maxHazards hazard limit before leaving
     */
    public LeaveAfterHazardsStrategy(int maxHazards) {
        this.maxHazards = maxHazards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTotalHazardsRevealed() < maxHazards;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterHazards(" + maxHazards + ")";
    }
}
