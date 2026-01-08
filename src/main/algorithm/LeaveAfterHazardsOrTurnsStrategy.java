package algorithm;

import model.RoundState;

/**
 * Strategy that leaves after reaching a hazard or turn threshold.
 */
public class LeaveAfterHazardsOrTurnsStrategy implements Strategy {
    private final int maxHazards;
    private final int maxTurns;

    /**
     * Creates a strategy with hazard and turn thresholds.
     *
     * @param maxHazards hazard limit before leaving
     * @param maxTurns turn limit before leaving
     */
    public LeaveAfterHazardsOrTurnsStrategy(int maxHazards, int maxTurns) {
        this.maxHazards = maxHazards;
        this.maxTurns = maxTurns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTotalHazardsRevealed() < maxHazards && state.getTurnNumber() < maxTurns;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterHazardsOrTurns(" + maxHazards + "," + maxTurns + ")";
    }
}
