package algorithm;

import model.RoundState;

public class AlwaysContinueStrategy implements Strategy {
    @Override
    public boolean shouldContinue(RoundState state) {
        return true;
    }

    @Override
    public String toString() {
        return "AlwaysContinue";
    }
}
