package algorithm;

import model.RoundState;

public interface Strategy {
    boolean shouldContinue(RoundState state);
}
