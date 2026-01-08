package algorithm;

import model.RoundState;

/**
 * Stays longer to secure artifacts, extending limits when artifacts are in play.
 */
public class ArtifactChaserStrategy implements Strategy {
    private final int baseTurnLimit;
    private final int baseHazardLimit;
    private final int bonusTurns;
    private final int bonusHazards;
    private final int maxPlayersToChase;

    public ArtifactChaserStrategy(int baseTurnLimit,
                                  int baseHazardLimit,
                                  int bonusTurns,
                                  int bonusHazards,
                                  int maxPlayersToChase) {
        this.baseTurnLimit = baseTurnLimit;
        this.baseHazardLimit = baseHazardLimit;
        this.bonusTurns = bonusTurns;
        this.bonusHazards = bonusHazards;
        this.maxPlayersToChase = maxPlayersToChase;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        if (state.getArtifactsOnPath() > 0 && state.getActivePlayers() == 1) {
            return false;
        }

        int turnLimit = baseTurnLimit;
        int hazardLimit = baseHazardLimit;

        if (state.getArtifactsOnPath() > 0 && state.getActivePlayers() <= maxPlayersToChase) {
            turnLimit += bonusTurns;
            hazardLimit += bonusHazards;
        }

        return state.getTurnNumber() < turnLimit
                && state.getTotalHazardsRevealed() < hazardLimit;
    }

    @Override
    public String toString() {
        return "ArtifactChaser";
    }
}
