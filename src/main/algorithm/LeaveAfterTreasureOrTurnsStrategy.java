package algorithm;

import model.RoundState;

/**
 * Leaves after reaching a treasure threshold or a turn threshold.
 */
public class LeaveAfterTreasureOrTurnsStrategy implements Strategy {
    private final int treasureThreshold;
    private final int turnThreshold;

    public LeaveAfterTreasureOrTurnsStrategy(int treasureThreshold, int turnThreshold) {
        this.treasureThreshold = treasureThreshold;
        this.turnThreshold = turnThreshold;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getRoundTreasure() < treasureThreshold
                && state.getTurnNumber() < turnThreshold;
    }

    @Override
    public String toString() {
        return "LeaveAfterTreasureOrTurns(" + treasureThreshold + "," + turnThreshold + ")";
    }
}
