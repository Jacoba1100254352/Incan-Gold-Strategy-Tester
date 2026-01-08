package algorithm;

import model.RoundState;

public class LeaveAfterHazardsOrTurnsStrategy implements Strategy {
    private final int maxHazards;
    private final int maxTurns;

    public LeaveAfterHazardsOrTurnsStrategy(int maxHazards, int maxTurns) {
        this.maxHazards = maxHazards;
        this.maxTurns = maxTurns;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTotalHazardsRevealed() < maxHazards && state.getTurnNumber() < maxTurns;
    }

    @Override
    public String toString() {
        return "LeaveAfterHazardsOrTurns(" + maxHazards + "," + maxTurns + ")";
    }
}
