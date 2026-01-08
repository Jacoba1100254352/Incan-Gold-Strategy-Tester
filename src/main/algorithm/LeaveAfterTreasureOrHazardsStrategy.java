package algorithm;

import model.RoundState;

public class LeaveAfterTreasureOrHazardsStrategy implements Strategy {
    private final int treasureThreshold;
    private final int maxHazards;

    public LeaveAfterTreasureOrHazardsStrategy(int treasureThreshold, int maxHazards) {
        this.treasureThreshold = treasureThreshold;
        this.maxHazards = maxHazards;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getRoundTreasure() < treasureThreshold
                && state.getTotalHazardsRevealed() < maxHazards;
    }

    @Override
    public String toString() {
        return "LeaveAfterTreasureOrHazards(" + treasureThreshold + "," + maxHazards + ")";
    }
}
