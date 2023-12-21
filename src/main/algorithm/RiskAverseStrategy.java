package algorithm;

import model.Hazard;

import java.util.List;

public class RiskAverseStrategy implements Strategy {
    @Override
    public boolean shouldContinue(int currentTreasure, List<Hazard> revealedHazards) {
        // Simple logic: retreat if any hazard is revealed
        return revealedHazards.isEmpty();
    }
}
