package algorithm;

import model.Hazard;
import model.RoundState;

/**
 * Leaves when remaining hazard copies make a repeat too likely.
 */
public class LeaveAfterHazardRiskStrategy implements Strategy {
    private static final int DEFAULT_HAZARD_COPIES = 3;

    private final int maxRiskScore;
    private final int maxHazards;

    /**
     * Creates a strategy with a risk score and hazard count ceiling.
     *
     * @param maxRiskScore maximum allowed remaining-copy risk score
     * @param maxHazards maximum total hazards allowed before leaving
     */
    public LeaveAfterHazardRiskStrategy(int maxRiskScore, int maxHazards) {
        this.maxRiskScore = Math.max(0, maxRiskScore);
        this.maxHazards = Math.max(0, maxHazards);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        if (state.getTotalHazardsRevealed() >= maxHazards) {
            return false;
        }

        boolean hasCopiesInfo = !state.getHazardCopiesRemainingMap().isEmpty();
        int riskScore = 0;
        for (Hazard hazard : Hazard.values()) {
            int seen = state.getHazardCount(hazard);
            if (seen > 0) {
                int totalCopies = hasCopiesInfo
                        ? state.getHazardCopiesRemaining(hazard)
                        : DEFAULT_HAZARD_COPIES;
                int remaining = Math.max(0, totalCopies - seen);
                riskScore += remaining;
            }
        }

        return riskScore <= maxRiskScore;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterHazardRisk";
    }
}
