package algorithm;

import model.RoundState;

/**
 * Strategy that leaves once a personal treasure threshold is reached.
 */
public class LeaveAfterTreasureStrategy implements Strategy {
    private final int treasureThreshold;

    /**
     * Creates a strategy with the treasure threshold to bank.
     *
     * @param treasureThreshold treasure amount that triggers leaving
     */
    public LeaveAfterTreasureStrategy(int treasureThreshold) {
        this.treasureThreshold = treasureThreshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getRoundTreasure() < treasureThreshold;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterTreasure(" + treasureThreshold + ")";
    }
}
