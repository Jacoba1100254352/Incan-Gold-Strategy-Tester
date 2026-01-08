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

    public ArtifactOpportunistStrategy(int minArtifacts,
                                       int maxPlayersToContest,
                                       int minTreasureToLeave,
                                       int hazardThreshold) {
        this(minArtifacts, maxPlayersToContest, minTreasureToLeave, hazardThreshold, new AlwaysContinueStrategy());
    }

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
        if (bankValue >= minTreasureToLeave || hazards >= hazardThreshold) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ArtifactOpportunist";
    }
}
