package algorithm;

import model.RoundState;

/**
 * Strategy that leaves after reaching a treasure or hazard threshold.
 */
public class LeaveAfterTreasureOrHazardsStrategy implements Strategy {
    private final int treasureThreshold;
    private final int maxHazards;

    /**
     * Creates a strategy with treasure and hazard thresholds.
     *
     * @param treasureThreshold personal treasure limit
     * @param maxHazards hazard limit before leaving
     */
    public LeaveAfterTreasureOrHazardsStrategy(int treasureThreshold, int maxHazards) {
        this.treasureThreshold = treasureThreshold;
        this.maxHazards = maxHazards;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getRoundTreasure() < treasureThreshold
                && state.getTotalHazardsRevealed() < maxHazards;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterTreasureOrHazards(" + treasureThreshold + "," + maxHazards + ")";
    }
}
