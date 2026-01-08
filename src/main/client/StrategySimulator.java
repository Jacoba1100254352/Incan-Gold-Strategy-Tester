package client;

import algorithm.Strategy;
import model.Card;
import model.Game;
import model.Hazard;
import model.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Utility methods for simulating strategies and round lengths.
 */
public class StrategySimulator {
    /**
     * Simulates average treasure per player for the given strategy.
     */
    public static double simulateAverageTreasure(Supplier<Strategy> strategyFactory,
                                                 int simulations,
                                                 int playersPerGame) {
        long totalTreasure = 0;
        for (int i = 0; i < simulations; i++) {
            List<Player> players = new ArrayList<>();
            for (int p = 0; p < playersPerGame; p++) {
                players.add(new Player(strategyFactory.get()));
            }
            Game game = new Game(players);
            game.playGame();
            for (Player player : players) {
                totalTreasure += player.getTotalTreasure();
            }
        }
        return totalTreasure / (double) (simulations * playersPerGame);
    }

    /**
     * Simulates average turns survived before the second hazard ends a round.
     */
    public static double simulateAverageTurnsUntilDoubleHazard(int simulations) {
        long totalTurns = 0;

        for (int i = 0; i < simulations; i++) {
            RoundLengthGame game = new RoundLengthGame();
            List<Card> deck = game.createRoundDeckForSimulation();
            totalTurns += countTurnsUntilDoubleHazard(deck);
        }

        return totalTurns / (double) simulations;
    }

    private static int countTurnsUntilDoubleHazard(List<Card> deck) {
        Map<Hazard, Integer> hazardCounts = new EnumMap<>(Hazard.class);
        int turns = 0;

        for (Card card : deck) {
            turns++;
            if (card.getType() == Card.Type.HAZARD) {
                Hazard hazard = card.getHazard();
                int count = hazardCounts.getOrDefault(hazard, 0) + 1;
                hazardCounts.put(hazard, count);
                if (count >= 2) {
                    break;
                }
            }
        }

        return turns;
    }

    private static class RoundLengthGame extends Game {
        private RoundLengthGame() {
            super(new ArrayList<>());
        }

        private List<Card> createRoundDeckForSimulation() {
            return createRoundDeck();
        }
    }
}
