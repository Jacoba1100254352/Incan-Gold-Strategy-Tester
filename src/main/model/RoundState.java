package model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class RoundState {
    private final int turnNumber;
    private final int activePlayers;
    private final int templeTreasure;
    private final int roundTreasure;
    private final Map<Hazard, Integer> hazardCounts;
    private final Map<Hazard, Integer> hazardCopiesRemaining;
    private final int artifactsOnPath;
    private final int artifactsClaimed;

    public RoundState(int turnNumber,
                      int activePlayers,
                      int templeTreasure,
                      int roundTreasure,
                      Map<Hazard, Integer> hazardCounts,
                      int artifactsOnPath) {
        this(turnNumber,
                activePlayers,
                templeTreasure,
                roundTreasure,
                hazardCounts,
                Collections.emptyMap(),
                artifactsOnPath,
                0);
    }

    public RoundState(int turnNumber,
                      int activePlayers,
                      int templeTreasure,
                      int roundTreasure,
                      Map<Hazard, Integer> hazardCounts,
                      Map<Hazard, Integer> hazardCopiesRemaining,
                      int artifactsOnPath,
                      int artifactsClaimed) {
        this.turnNumber = turnNumber;
        this.activePlayers = activePlayers;
        this.templeTreasure = templeTreasure;
        this.roundTreasure = roundTreasure;
        this.hazardCounts = Collections.unmodifiableMap(new EnumMap<>(hazardCounts));
        Map<Hazard, Integer> remaining = hazardCopiesRemaining == null
                ? Collections.emptyMap()
                : hazardCopiesRemaining;
        EnumMap<Hazard, Integer> remainingCopy = remaining.isEmpty()
                ? new EnumMap<>(Hazard.class)
                : new EnumMap<>(remaining);
        this.hazardCopiesRemaining = Collections.unmodifiableMap(remainingCopy);
        this.artifactsOnPath = artifactsOnPath;
        this.artifactsClaimed = artifactsClaimed;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public int getActivePlayers() {
        return activePlayers;
    }

    public int getTempleTreasure() {
        return templeTreasure;
    }

    public int getRoundTreasure() {
        return roundTreasure;
    }

    public int getHazardCount(Hazard hazard) {
        return hazardCounts.getOrDefault(hazard, 0);
    }

    public int getTotalHazardsRevealed() {
        int total = 0;
        for (int count : hazardCounts.values()) {
            total += count;
        }
        return total;
    }

    public Map<Hazard, Integer> getHazardCounts() {
        return hazardCounts;
    }

    public int getHazardCopiesRemaining(Hazard hazard) {
        return hazardCopiesRemaining.getOrDefault(hazard, 0);
    }

    public Map<Hazard, Integer> getHazardCopiesRemainingMap() {
        return hazardCopiesRemaining;
    }

    public int getArtifactsOnPath() {
        return artifactsOnPath;
    }

    public int getArtifactsClaimed() {
        return artifactsClaimed;
    }
}
