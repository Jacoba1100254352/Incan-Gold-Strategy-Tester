package algorithm;

import model.RoundState;

public class LeaveAfterHazardsStrategy implements Strategy {
    private final int maxHazards;

    public LeaveAfterHazardsStrategy(int maxHazards) {
        this.maxHazards = maxHazards;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTotalHazardsRevealed() < maxHazards;
    }

    @Override
    public String toString() {
        return "LeaveAfterHazards(" + maxHazards + ")";
    }
}
