package algorithm;

import model.RoundState;

/**
 * Leaves immediately when an artifact is available and the player is alone.
 */
public class ArtifactSoloExitStrategy implements Strategy {
    private final Strategy fallbackStrategy;

    public ArtifactSoloExitStrategy() {
        this(new AlwaysContinueStrategy());
    }

    public ArtifactSoloExitStrategy(Strategy fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy == null ? new AlwaysContinueStrategy() : fallbackStrategy;
    }

    @Override
    public boolean shouldContinue(RoundState state) {
        if (!fallbackStrategy.shouldContinue(state)) {
            return false;
        }
        return !(state.getArtifactsOnPath() > 0 && state.getActivePlayers() == 1);
    }

    @Override
    public String toString() {
        return "ArtifactSoloExit";
    }
}
