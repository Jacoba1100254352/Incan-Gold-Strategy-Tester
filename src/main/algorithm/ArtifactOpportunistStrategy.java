package algorithm;

import model.RoundState;

/**
 * Leaves to pursue artifacts when few players remain and the risk or take is high.
 */
public class ArtifactOpportunistStrategy implements Strategy {
    private final Strategy fallbackStrategy;
    private final int minArtifacts;
    private final int maxPlayersToContest;
    private final int minTreasureToLeave;
    private final int hazardThreshold;

    /**
     * Creates an artifact opportunist strategy with an always-continue fallback.
     *
     * @param minArtifacts minimum artifacts needed to consider leaving
     * @param maxPlayersToContest maximum players allowed to contest artifacts
     * @param minTreasureToLeave minimum personal treasure to justify leaving
     * @param hazardThreshold hazard count that triggers leaving
     */
    public ArtifactOpportunistStrategy(int minArtifacts,
                                       int maxPlayersToContest,
                                       int minTreasureToLeave,
                                       int hazardThreshold) {
        this(minArtifacts, maxPlayersToContest, minTreasureToLeave, hazardThreshold, new AlwaysContinueStrategy());
    }

    /**
     * Creates an artifact opportunist strategy with a fallback baseline.
     *
     * @param minArtifacts minimum artifacts needed to consider leaving
     * @param maxPlayersToContest maximum players allowed to contest artifacts
     * @param minTreasureToLeave minimum personal treasure to justify leaving
     * @param hazardThreshold hazard count that triggers leaving
     * @param fallbackStrategy baseline strategy used before opportunistic checks
     */
    public ArtifactOpportunistStrategy(int minArtifacts,
                                       int maxPlayersToContest,
                                       int minTreasureToLeave,
                                       int hazardThreshold,
                                       Strategy fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy == null ? new AlwaysContinueStrategy() : fallbackStrategy;
        this.minArtifacts = minArtifacts;
        this.maxPlayersToContest = maxPlayersToContest;
        this.minTreasureToLeave = minTreasureToLeave;
        this.hazardThreshold = hazardThreshold;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        if (!fallbackStrategy.shouldContinue(state)) {
            return false;
        }
        if (state.getArtifactsOnPath() < minArtifacts) {
            return true;
        }
        if (state.getActivePlayers() > maxPlayersToContest) {
            return true;
        }
        int hazards = state.getTotalHazardsRevealed();
        int estimatedShare = state.getActivePlayers() == 0
                ? 0
                : state.getTempleTreasure() / state.getActivePlayers();
        int bankValue = state.getRoundTreasure() + estimatedShare;
	    return bankValue < minTreasureToLeave && hazards < hazardThreshold;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "ArtifactOpportunist";
    }
}
