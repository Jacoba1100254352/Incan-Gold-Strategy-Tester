package algorithm;

import model.Hazard;
import model.RoundState;

/**
 * Leaves when artifact value plus current haul outweighs the risk of continuing.
 */
public class ArtifactValueRiskStrategy implements Strategy {
    private static final int DEFAULT_HAZARD_COPIES = 3;
    private static final int ARTIFACT_LOW_VALUE = 5;
    private static final int ARTIFACT_HIGH_VALUE = 10;
    private static final int ARTIFACT_LOW_COUNT = 3;

    private final Strategy fallbackStrategy;
    private final int minBankValue;
    private final int riskThreshold;
    private final int maxPlayersToContest;

    /**
     * Creates an artifact value/risk strategy with an always-continue fallback.
     *
     * @param minBankValue minimum value to justify leaving
     * @param riskThreshold minimum risk score to justify leaving
     * @param maxPlayersToContest maximum players allowed to contest artifacts
     */
    public ArtifactValueRiskStrategy(int minBankValue, int riskThreshold, int maxPlayersToContest) {
        this(minBankValue, riskThreshold, maxPlayersToContest, new AlwaysContinueStrategy());
    }

    /**
     * Creates an artifact value/risk strategy with a fallback baseline.
     *
     * @param minBankValue minimum value to justify leaving
     * @param riskThreshold minimum risk score to justify leaving
     * @param maxPlayersToContest maximum players allowed to contest artifacts
     * @param fallbackStrategy baseline strategy used before value/risk checks
     */
    public ArtifactValueRiskStrategy(int minBankValue,
                                     int riskThreshold,
                                     int maxPlayersToContest,
                                     Strategy fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy == null ? new AlwaysContinueStrategy() : fallbackStrategy;
        this.minBankValue = minBankValue;
        this.riskThreshold = riskThreshold;
        this.maxPlayersToContest = maxPlayersToContest;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        if (!fallbackStrategy.shouldContinue(state)) {
            return false;
        }
        if (state.getArtifactsOnPath() == 0) {
            return true;
        }
        if (state.getActivePlayers() > maxPlayersToContest) {
            return true;
        }

        int artifactValue = estimateArtifactValue(state.getArtifactsOnPath(), state.getArtifactsClaimed());
        int estimatedShare = state.getActivePlayers() == 0
                ? 0
                : state.getTempleTreasure() / state.getActivePlayers();
        int bankValue = artifactValue + state.getRoundTreasure() + estimatedShare;
        int riskScore = hazardRiskScore(state);
	    
	    return bankValue < minBankValue || riskScore < riskThreshold;
    }

    private int estimateArtifactValue(int artifactsOnPath, int artifactsClaimed) {
        int value = 0;
        for (int i = 0; i < artifactsOnPath; i++) {
            int claimIndex = artifactsClaimed + i;
            value += claimIndex < ARTIFACT_LOW_COUNT ? ARTIFACT_LOW_VALUE : ARTIFACT_HIGH_VALUE;
        }
        return value;
    }

    private int hazardRiskScore(RoundState state) {
        boolean hasCopiesInfo = !state.getHazardCopiesRemainingMap().isEmpty();
        int score = 0;
        for (Hazard hazard : Hazard.values()) {
            int seen = state.getHazardCount(hazard);
            if (seen > 0) {
                int totalCopies = hasCopiesInfo ? state.getHazardCopiesRemaining(hazard) : DEFAULT_HAZARD_COPIES;
                int remaining = Math.max(0, totalCopies - seen);
                score += remaining;
            }
        }
        return score;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "ArtifactValueRisk";
    }
}
