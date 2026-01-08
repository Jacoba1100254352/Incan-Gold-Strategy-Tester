package model;

import algorithm.Strategy;

public class Player {
    private final Strategy strategy;
    private int totalTreasure;
    private int roundTreasure;

    public Player(Strategy strategy) {
        this.strategy = strategy;
        this.totalTreasure = 0;
        this.roundTreasure = 0;
    }

    public void startRound() {
        roundTreasure = 0;
    }

    public boolean makeDecision(RoundState state) {
        return strategy.shouldContinue(state);
    }

    public void collect(int amount) {
        roundTreasure += amount;
    }

    public void leaveRound(int templeShare) {
        roundTreasure += templeShare;
        bankRoundTreasure();
    }

    public void bankRoundTreasure() {
        totalTreasure += roundTreasure;
        roundTreasure = 0;
    }

    public void loseRoundTreasure() {
        roundTreasure = 0;
    }

    public int getTotalTreasure() {
        return totalTreasure;
    }

    public int getRoundTreasure() {
        return roundTreasure;
    }
}
