package model;

import algorithm.Strategy;

import java.util.List;

public class Player {
    private final Strategy strategy;
    private int treasure;

    public Player(Strategy strategy) {
        this.strategy = strategy;
        this.treasure = 0;
    }

    public boolean makeDecision(int currentTreasure, List<Hazard> revealedHazards) {
        return strategy.shouldContinue(currentTreasure, revealedHazards);
    }

    public void addTreasure(int amount) {
        treasure += amount;
    }

    public int getTreasure() {
        return treasure;
    }
}
