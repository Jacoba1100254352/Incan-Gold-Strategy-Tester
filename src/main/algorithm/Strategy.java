package algorithm;

import model.Hazard;

import java.util.List;

public interface Strategy {
    boolean shouldContinue(int currentTreasure, List<Hazard> revealedHazards);
}
