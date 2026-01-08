package algorithm;

import model.RoundState;

public class LeaveAfterTreasureStrategy implements Strategy {
    private final int treasureThreshold;

    public LeaveAfterTreasureStrategy(int treasureThreshold) {
        this.treasureThreshold = treasureThreshold;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getRoundTreasure() < treasureThreshold;
    }

    @Override
    public String toString() {
        return "LeaveAfterTreasure(" + treasureThreshold + ")";
    }
}
