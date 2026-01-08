package algorithm;

import model.RoundState;

/**
 * Strategy that leaves once a turn threshold is reached.
 */
public class LeaveAfterTurnsStrategy implements Strategy {
    private final int maxTurns;

    /**
     * Creates a strategy with the maximum turn count to tolerate.
     *
     * @param maxTurns turn limit before leaving
     */
    public LeaveAfterTurnsStrategy(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTurnNumber() < maxTurns;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "LeaveAfterTurns(" + maxTurns + ")";
    }
}
