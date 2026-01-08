package model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable snapshot of the current round state used by strategies.
 */
public class RoundState {
    private final int turnNumber;
    private final int activePlayers;
    private final int templeTreasure;
    private final int roundTreasure;
    private final Map<Hazard, Integer> hazardCounts;
    private final Map<Hazard, Integer> hazardCopiesRemaining;
    private final int artifactsOnPath;
    private final int artifactsClaimed;

    /**
     * Creates a round state snapshot without hazard copy or artifact claim context.
     *
     * @param turnNumber current turn number
     * @param activePlayers players still in the temple
     * @param templeTreasure treasure remaining on the path
     * @param roundTreasure treasure held by the current player
     * @param hazardCounts hazard counts revealed so far
     * @param artifactsOnPath artifacts currently on the path
     */
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

    /**
     * Creates a round state snapshot with hazard copy and artifact claim context.
     *
     * @param turnNumber current turn number
     * @param activePlayers players still in the temple
     * @param templeTreasure treasure remaining on the path
     * @param roundTreasure treasure held by the current player
     * @param hazardCounts hazard counts revealed so far
     * @param hazardCopiesRemaining remaining hazard copies in the deck
     * @param artifactsOnPath artifacts currently on the path
     * @param artifactsClaimed artifacts already claimed across the game
     */
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

    /**
     * Returns the current turn number.
     *
     * @return turn number
     */
    public int getTurnNumber() {
        return turnNumber;
    }

    /**
     * Returns the number of players still in the temple.
     *
     * @return active player count
     */
    public int getActivePlayers() {
        return activePlayers;
    }

    /**
     * Returns treasure remaining on the path.
     *
     * @return temple treasure
     */
    public int getTempleTreasure() {
        return templeTreasure;
    }

    /**
     * Returns treasure held by the current player this round.
     *
     * @return round treasure
     */
    public int getRoundTreasure() {
        return roundTreasure;
    }

    /**
     * Returns the count of a specific hazard revealed so far.
     *
     * @param hazard hazard type to query
     * @return hazard count
     */
    public int getHazardCount(Hazard hazard) {
        return hazardCounts.getOrDefault(hazard, 0);
    }

    /**
     * Returns the total hazards revealed so far in the round.
     *
     * @return total hazard count
     */
    public int getTotalHazardsRevealed() {
        int total = 0;
        for (int count : hazardCounts.values()) {
            total += count;
        }
        return total;
    }

    /**
     * Returns an immutable view of hazard counts.
     *
     * @return hazard counts map
     */
    public Map<Hazard, Integer> getHazardCounts() {
        return hazardCounts;
    }

    /**
     * Returns remaining copies of a hazard in the deck.
     *
     * @param hazard hazard type to query
     * @return remaining copies
     */
    public int getHazardCopiesRemaining(Hazard hazard) {
        return hazardCopiesRemaining.getOrDefault(hazard, 0);
    }

    /**
     * Returns an immutable view of remaining hazard copies.
     *
     * @return hazard copies map
     */
    public Map<Hazard, Integer> getHazardCopiesRemainingMap() {
        return hazardCopiesRemaining;
    }

    /**
     * Returns how many artifacts are currently on the path.
     *
     * @return artifacts on path
     */
    public int getArtifactsOnPath() {
        return artifactsOnPath;
    }

    /**
     * Returns how many artifacts have been claimed so far in the game.
     *
     * @return artifacts claimed
     */
    public int getArtifactsClaimed() {
        return artifactsClaimed;
    }
}
