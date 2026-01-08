package algorithm;

import model.RoundState;

/**
 * Leaves immediately when an artifact is available and the player is alone.
 */
public class ArtifactSoloExitStrategy implements Strategy {
    private final Strategy fallbackStrategy;

    /**
     * Creates a strategy that always continues until solo artifact conditions apply.
     */
    public ArtifactSoloExitStrategy() {
        this(new AlwaysContinueStrategy());
    }

    /**
     * Creates a strategy that defers to a fallback policy unless solo artifact applies.
     *
     * @param fallbackStrategy baseline strategy used before solo artifact exit
     */
    public ArtifactSoloExitStrategy(Strategy fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy == null ? new AlwaysContinueStrategy() : fallbackStrategy;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        if (!fallbackStrategy.shouldContinue(state)) {
            return false;
        }
        return !(state.getArtifactsOnPath() > 0 && state.getActivePlayers() == 1);
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "ArtifactSoloExit";
    }
}
