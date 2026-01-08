package algorithm;

import model.Hazard;
import model.RoundState;

/**
 * Leaves after hazards, but relaxes when repeated hazards are less likely.
 */
public class LeaveAfterHazardsWithMemoryStrategy implements Strategy {
    private static final int DEFAULT_HAZARD_COPIES = 3;

    private final int baseHazardLimit;
    private final int lowRemainingThreshold;
    private final int bonusPerLowRemaining;

    /**
     * Creates a strategy that increases its hazard limit when copies are scarce.
     *
     * @param baseHazardLimit base hazard limit before leaving
     * @param lowRemainingThreshold remaining copies considered "low"
     * @param bonusPerLowRemaining bonus hazards allowed per low-copy hazard
     */
    public LeaveAfterHazardsWithMemoryStrategy(int baseHazardLimit,
                                               int lowRemainingThreshold,
                                               int bonusPerLowRemaining) {
        this.baseHazardLimit = baseHazardLimit;
        this.lowRemainingThreshold = lowRemainingThreshold;
        this.bonusPerLowRemaining = bonusPerLowRemaining;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        int bonus = 0;
        boolean hasCopiesInfo = !state.getHazardCopiesRemainingMap().isEmpty();

        for (Hazard hazard : Hazard.values()) {
            int seen = state.getHazardCount(hazard);
            if (seen > 0) {
                int totalCopies = hasCopiesInfo ? state.getHazardCopiesRemaining(hazard) : DEFAULT_HAZARD_COPIES;
                int remaining = Math.max(0, totalCopies - seen);
                if (remaining <= lowRemainingThreshold) {
                    bonus += bonusPerLowRemaining;
                }
            }
        }

        int limit = baseHazardLimit + bonus;
        return state.getTotalHazardsRevealed() < limit;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterHazardsWithMemory";
    }
}
