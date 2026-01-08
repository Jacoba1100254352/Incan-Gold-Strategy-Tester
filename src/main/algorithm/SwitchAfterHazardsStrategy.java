package algorithm;

import model.RoundState;

/**
 * Strategy that switches to another strategy after a hazard threshold.
 */
public class SwitchAfterHazardsStrategy implements Strategy {
    private final int hazardThreshold;
    private final Strategy before;
    private final Strategy after;

    /**
     * Creates a strategy that switches when hazards reach a threshold.
     *
     * @param hazardThreshold hazard count that triggers the switch
     * @param before strategy to use before the threshold
     * @param after strategy to use once the threshold is reached
     */
    public SwitchAfterHazardsStrategy(int hazardThreshold, Strategy before, Strategy after) {
        this.hazardThreshold = hazardThreshold;
        this.before = before;
        this.after = after;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        Strategy active = state.getTotalHazardsRevealed() < hazardThreshold ? before : after;
        return active.shouldContinue(state);
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "SwitchAfterHazards(" + hazardThreshold + "," + before + "," + after + ")";
    }
}
