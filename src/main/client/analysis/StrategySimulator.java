package client.analysis;

import algorithm.Strategy;
import model.Card;
import model.Game;
import model.Hazard;
import model.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
     * Simulates a focus strategy against identical opponent strategies.
     *
     * @param focusFactory focus strategy factory
     * @param opponentFactory opponent strategy factory
     * @param simulations number of simulated games
     * @param playersPerGame players per game
     * @param focusPlayers number of players using the focus strategy
     * @return matchup stats for the focus strategy
     */
    public static MatchupStats simulateMatchup(Supplier<Strategy> focusFactory,
                                               Supplier<Strategy> opponentFactory,
                                               int simulations,
                                               int playersPerGame,
                                               int focusPlayers) {
        int focusCount = Math.max(1, Math.min(focusPlayers, playersPerGame));
        int opponentCount = Math.max(0, playersPerGame - focusCount);

        long focusTreasure = 0;
        int focusWins = 0;
        for (int i = 0; i < simulations; i++) {
            List<Player> players = new ArrayList<>();
            for (int p = 0; p < focusCount; p++) {
                players.add(new Player(focusFactory.get()));
            }
            for (int p = 0; p < opponentCount; p++) {
                players.add(new Player(opponentFactory.get()));
            }
            Game game = new Game(players);
            game.playGame();

            int maxTreasure = Integer.MIN_VALUE;
            int focusMax = Integer.MIN_VALUE;
            for (int p = 0; p < players.size(); p++) {
                int treasure = players.get(p).getTotalTreasure();
                maxTreasure = Math.max(maxTreasure, treasure);
                if (p < focusCount) {
                    focusTreasure += treasure;
                    focusMax = Math.max(focusMax, treasure);
                }
            }
            if (focusMax == maxTreasure) {
                focusWins++;
            }
        }

        double averageTreasure = simulations == 0
                ? 0.0
                : focusTreasure / (double) (simulations * focusCount);
        double winRate = simulations == 0 ? 0.0 : (focusWins * 100.0) / simulations;
        return new MatchupStats(averageTreasure, winRate);
    }

    /**
     * Simulates a focus strategy against a random mix of opponent strategies.
     *
     * @param focusFactory focus strategy factory
     * @param opponentFactories pool of opponent strategy factories
     * @param simulations number of simulated games
     * @param playersPerGame players per game
     * @param random random generator for selecting opponents
     * @return matchup stats for the focus strategy
     */
    public static MatchupStats simulateMatchupAgainstField(Supplier<Strategy> focusFactory,
                                                           List<Supplier<Strategy>> opponentFactories,
                                                           int simulations,
                                                           int playersPerGame,
                                                           Random random) {
        if (opponentFactories.isEmpty() || playersPerGame < 1) {
            return new MatchupStats(0.0, 0.0);
        }
        long focusTreasure = 0;
        int focusWins = 0;

        for (int i = 0; i < simulations; i++) {
            List<Player> players = new ArrayList<>();
            players.add(new Player(focusFactory.get()));
            for (int p = 1; p < playersPerGame; p++) {
                Supplier<Strategy> factory = opponentFactories.get(random.nextInt(opponentFactories.size()));
                players.add(new Player(factory.get()));
            }
            Game game = new Game(players);
            game.playGame();

            int maxTreasure = Integer.MIN_VALUE;
            int focusTreasureValue = players.get(0).getTotalTreasure();
            for (Player player : players) {
                maxTreasure = Math.max(maxTreasure, player.getTotalTreasure());
            }
            focusTreasure += focusTreasureValue;
            if (focusTreasureValue == maxTreasure) {
                focusWins++;
            }
        }

        double averageTreasure = simulations == 0
                ? 0.0
                : focusTreasure / (double) simulations;
        double winRate = simulations == 0 ? 0.0 : (focusWins * 100.0) / simulations;
        return new MatchupStats(averageTreasure, winRate);
    }

    /**
     * Aggregated stats for a matchup simulation.
     *
     * @param averageTreasure average treasure for the focus strategy
     * @param winRate win rate percentage for the focus strategy
     */
    public record MatchupStats(double averageTreasure, double winRate) {
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
