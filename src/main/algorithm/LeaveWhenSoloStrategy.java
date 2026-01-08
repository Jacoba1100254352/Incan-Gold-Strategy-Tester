package algorithm;

import model.RoundState;

/**
 * Strategy that leaves when only one player remains in the temple.
 */
public class LeaveWhenSoloStrategy implements Strategy {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getActivePlayers() > 1;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveWhenSolo";
    }
}
