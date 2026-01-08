package algorithm;

import model.RoundState;

public class LeaveAfterTempleTreasureStrategy implements Strategy {
    private final int templeThreshold;

    public LeaveAfterTempleTreasureStrategy(int templeThreshold) {
        this.templeThreshold = templeThreshold;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTempleTreasure() < templeThreshold;
    }

    @Override
    public String toString() {
        return "LeaveAfterTempleTreasure(" + templeThreshold + ")";
    }
}
