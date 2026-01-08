package algorithm;

import model.RoundState;

public class LeaveWhenSoloStrategy implements Strategy {
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getActivePlayers() > 1;
    }

    @Override
    public String toString() {
        return "LeaveWhenSolo";
    }
}
