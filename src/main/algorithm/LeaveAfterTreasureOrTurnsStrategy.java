package algorithm;

import model.RoundState;

/**
 * Leaves after reaching a treasure threshold or a turn threshold.
 */
public class LeaveAfterTreasureOrTurnsStrategy implements Strategy {
    private final int treasureThreshold;
    private final int turnThreshold;

    /**
     * Creates a strategy with treasure and turn thresholds.
     *
     * @param treasureThreshold personal treasure limit
     * @param turnThreshold turn limit before leaving
     */
    public LeaveAfterTreasureOrTurnsStrategy(int treasureThreshold, int turnThreshold) {
        this.treasureThreshold = treasureThreshold;
        this.turnThreshold = turnThreshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getRoundTreasure() < treasureThreshold
                && state.getTurnNumber() < turnThreshold;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterTreasureOrTurns(" + treasureThreshold + "," + turnThreshold + ")";
    }
}
