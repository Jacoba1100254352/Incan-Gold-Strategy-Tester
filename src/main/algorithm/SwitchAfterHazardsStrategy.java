package algorithm;

import model.RoundState;

public class SwitchAfterHazardsStrategy implements Strategy {
    private final int hazardThreshold;
    private final Strategy before;
    private final Strategy after;

    public SwitchAfterHazardsStrategy(int hazardThreshold, Strategy before, Strategy after) {
        this.hazardThreshold = hazardThreshold;
        this.before = before;
        this.after = after;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        Strategy active = state.getTotalHazardsRevealed() < hazardThreshold ? before : after;
        return active.shouldContinue(state);
    }

    @Override
    public String toString() {
        return "SwitchAfterHazards(" + hazardThreshold + "," + before + "," + after + ")";
    }
}
