package algorithm;

import model.RoundState;

/**
 * Strategy that leaves once the shared temple treasure reaches a threshold.
 */
public class LeaveAfterTempleTreasureStrategy implements Strategy {
    private final int templeThreshold;

    /**
     * Creates a strategy with the temple treasure threshold.
     *
     * @param templeThreshold shared treasure amount that triggers leaving
     */
    public LeaveAfterTempleTreasureStrategy(int templeThreshold) {
        this.templeThreshold = templeThreshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTempleTreasure() < templeThreshold;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterTempleTreasure(" + templeThreshold + ")";
    }
}
