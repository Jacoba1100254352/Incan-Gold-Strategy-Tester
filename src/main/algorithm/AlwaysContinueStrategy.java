package algorithm;

import model.RoundState;

/**
 * Strategy that never leaves the temple.
 */
public class AlwaysContinueStrategy implements Strategy {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return true;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "AlwaysContinue";
    }
}
