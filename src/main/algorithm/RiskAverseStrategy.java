package algorithm;

import model.RoundState;

/**
 * Strategy that leaves after the first hazard is revealed.
 */
public class RiskAverseStrategy implements Strategy {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        return state.getTotalHazardsRevealed() == 0;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "RiskAverse (leave after 1 hazard)";
    }
}
