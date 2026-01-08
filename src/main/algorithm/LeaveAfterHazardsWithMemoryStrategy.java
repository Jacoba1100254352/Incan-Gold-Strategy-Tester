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

    public LeaveAfterHazardsWithMemoryStrategy(int baseHazardLimit,
                                               int lowRemainingThreshold,
                                               int bonusPerLowRemaining) {
        this.baseHazardLimit = baseHazardLimit;
        this.lowRemainingThreshold = lowRemainingThreshold;
        this.bonusPerLowRemaining = bonusPerLowRemaining;
    }

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

    @Override
    public String toString() {
        return "LeaveAfterHazardsWithMemory";
    }
}
