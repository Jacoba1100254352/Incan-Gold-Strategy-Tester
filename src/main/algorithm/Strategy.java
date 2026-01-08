package algorithm;

import model.RoundState;

public interface Strategy {
    /**
     * Returns true to continue exploring, false to leave the temple.
     *
     * @param state current round state snapshot
     * @return whether the player should continue
     */
    boolean shouldContinue(RoundState state);
}
