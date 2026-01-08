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

    /**
     * Creates a strategy that extends turn/hazard limits when artifacts are present.
     *
     * @param baseTurnLimit base turn limit before leaving
     * @param baseHazardLimit base hazard limit before leaving
     * @param bonusTurns extra turns allowed when chasing artifacts
     * @param bonusHazards extra hazards allowed when chasing artifacts
     * @param maxPlayersToChase maximum players allowed to chase artifacts
     */
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

    /**
     * {@inheritDoc}
     */
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

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "ArtifactChaser";
    }
}
