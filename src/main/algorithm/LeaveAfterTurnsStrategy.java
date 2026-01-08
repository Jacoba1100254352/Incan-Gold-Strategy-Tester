package algorithm;

import model.RoundState;

public class LeaveAfterTurnsStrategy implements Strategy {
    private final int maxTurns;

    public LeaveAfterTurnsStrategy(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTurnNumber() < maxTurns;
    }

    @Override
    public String toString() {
        return "LeaveAfterTurns(" + maxTurns + ")";
    }
}
