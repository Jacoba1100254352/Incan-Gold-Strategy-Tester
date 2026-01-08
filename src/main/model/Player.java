package model;

import algorithm.Strategy;

/**
 * Represents a player and their accumulated treasure.
 */
public class Player {
    private final Strategy strategy;
    private int totalTreasure;
    private int roundTreasure;
    private int artifactsClaimed;

    /**
     * Creates a player that uses the provided strategy.
     *
     * @param strategy decision strategy
     */
    public Player(Strategy strategy) {
        this.strategy = strategy;
        this.totalTreasure = 0;
        this.roundTreasure = 0;
        this.artifactsClaimed = 0;
    }

    /**
     * Resets per-round treasure at the start of a new round.
     */
    public void startRound() {
        roundTreasure = 0;
    }

    /**
     * Asks the strategy whether to continue exploring.
     *
     * @param state current round state
     * @return true to continue, false to leave
     */
    public boolean makeDecision(RoundState state) {
        return strategy.shouldContinue(state);
    }

    /**
     * Adds collected treasure to the round total.
     *
     * @param amount treasure to collect
     */
    public void collect(int amount) {
        roundTreasure += amount;
    }

    /**
     * Leaves the round and banks the round treasure plus temple share.
     *
     * @param templeShare share of remaining temple treasure
     */
    public void leaveRound(int templeShare) {
        roundTreasure += templeShare;
        bankRoundTreasure();
    }

    /**
     * Banks the current round treasure into total treasure.
     */
    public void bankRoundTreasure() {
        totalTreasure += roundTreasure;
        roundTreasure = 0;
    }

    /**
     * Loses all unbanked treasure from the current round.
     */
    public void loseRoundTreasure() {
        roundTreasure = 0;
    }

    /**
     * Returns total treasure across all rounds.
     *
     * @return total treasure
     */
    public int getTotalTreasure() {
        return totalTreasure;
    }

    /**
     * Returns treasure collected in the current round.
     *
     * @return round treasure
     */
    public int getRoundTreasure() {
        return roundTreasure;
    }

    /**
     * Adds an artifact value to total treasure and increments artifact count.
     *
     * @param value artifact value
     */
    public void claimArtifact(int value) {
        totalTreasure += value;
        artifactsClaimed++;
    }

    /**
     * Returns how many artifacts this player has claimed.
     *
     * @return artifacts claimed
     */
    public int getArtifactsClaimed() {
        return artifactsClaimed;
    }
}
