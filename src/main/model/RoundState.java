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
    private final int artifactsRemainingInDeck;

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
                0,
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
     * @param hazardCopiesRemaining copies of each hazard still in the game
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
        this(turnNumber,
                activePlayers,
                templeTreasure,
                roundTreasure,
                hazardCounts,
                hazardCopiesRemaining,
                artifactsOnPath,
                artifactsClaimed,
                0);
    }

    /**
     * Creates a round state snapshot with full artifact deck context.
     *
     * @param turnNumber current turn number
     * @param activePlayers players still in the temple
     * @param templeTreasure treasure remaining on the path
     * @param roundTreasure treasure held by the current player
     * @param hazardCounts hazard counts revealed so far
     * @param hazardCopiesRemaining copies of each hazard still in the game
     * @param artifactsOnPath artifacts currently on the path
     * @param artifactsClaimed artifacts already claimed across the game
     * @param artifactsRemainingInDeck artifacts currently unrevealed in the expedition deck
     */
    public RoundState(int turnNumber,
                      int activePlayers,
                      int templeTreasure,
                      int roundTreasure,
                      Map<Hazard, Integer> hazardCounts,
                      Map<Hazard, Integer> hazardCopiesRemaining,
                      int artifactsOnPath,
                      int artifactsClaimed,
                      int artifactsRemainingInDeck) {
        requireNonNegative(turnNumber, "turnNumber");
        requireNonNegative(activePlayers, "activePlayers");
        requireNonNegative(templeTreasure, "templeTreasure");
        requireNonNegative(roundTreasure, "roundTreasure");
        requireNonNegative(artifactsOnPath, "artifactsOnPath");
        requireNonNegative(artifactsClaimed, "artifactsClaimed");
        requireNonNegative(artifactsRemainingInDeck, "artifactsRemainingInDeck");
        this.turnNumber = turnNumber;
        this.activePlayers = activePlayers;
        this.templeTreasure = templeTreasure;
        this.roundTreasure = roundTreasure;
        this.hazardCounts = immutableHazardMap(hazardCounts);
        this.hazardCopiesRemaining = immutableHazardMap(hazardCopiesRemaining);
        this.artifactsOnPath = artifactsOnPath;
        this.artifactsClaimed = artifactsClaimed;
        this.artifactsRemainingInDeck = artifactsRemainingInDeck;
    }

    /**
     * Returns an immutable hazard map copy that tolerates null and empty inputs.
     */
    private static Map<Hazard, Integer> immutableHazardMap(Map<Hazard, Integer> source) {
        EnumMap<Hazard, Integer> copy = new EnumMap<>(Hazard.class);
        if (source != null) {
            for (Map.Entry<Hazard, Integer> entry : source.entrySet()) {
                Hazard hazard = entry.getKey();
                Integer value = entry.getValue();
                if (hazard == null) {
                    throw new IllegalArgumentException("Hazard maps cannot contain null keys");
                }
                if (value == null || value < 0) {
                    throw new IllegalArgumentException("Hazard counts cannot be negative or null for " + hazard);
                }
                copy.put(hazard, value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Validates nonnegative integer state.
     */
    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative: " + value);
        }
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
     * Returns copies of a hazard still in the game.
     *
     * <p>This includes copies already visible on the current path. Strategies
     * can subtract {@link #getHazardCount(Hazard)} to obtain the unrevealed
     * matching-copy count.</p>
     *
     * @param hazard hazard type to query
     * @return remaining copies
     */
    public int getHazardCopiesRemaining(Hazard hazard) {
        return hazardCopiesRemaining.getOrDefault(hazard, 0);
    }

    /**
     * Returns an immutable view of hazard copies still in the game.
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

    /**
     * Returns how many artifact cards remain unrevealed in the expedition deck.
     *
     * @return artifact cards remaining in deck
     */
    public int getArtifactsRemainingInDeck() {
        return artifactsRemainingInDeck;
    }
}
