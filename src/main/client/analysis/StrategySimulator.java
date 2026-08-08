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
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Utility methods for deterministic strategy and round-length simulations.
 */
public final class StrategySimulator {
    private static final double CONFIDENCE_95_Z = 1.96;

    private StrategySimulator() {
    }

    /**
     * Simulates average treasure per player using generated game seeds.
     */
    public static double simulateAverageTreasure(Supplier<Strategy> strategyFactory,
                                                 int simulations,
                                                 int playersPerGame) {
        return simulateAverageTreasure(strategyFactory, simulations, playersPerGame, new Random());
    }

    /**
     * Simulates average treasure per player using seeds drawn from the supplied RNG.
     */
    public static double simulateAverageTreasure(Supplier<Strategy> strategyFactory,
                                                 int simulations,
                                                 int playersPerGame,
                                                 Random random) {
        return simulateTreasureStats(
                strategyFactory,
                playersPerGame,
                createGameSeeds(simulations, random)
        ).averageTreasure();
    }

    /**
     * Simulates average treasure per player over an explicit set of game seeds.
     *
     * <p>Passing the same seed array to multiple strategies gives every strategy
     * the same random starting conditions, which reduces comparison noise and
     * makes results independent of catalog order.</p>
     */
    public static double simulateAverageTreasure(Supplier<Strategy> strategyFactory,
                                                 int playersPerGame,
                                                 long[] gameSeeds) {
        return simulateTreasureStats(strategyFactory, playersPerGame, gameSeeds).averageTreasure();
    }

    /**
     * Returns game-level mean and uncertainty statistics for a homogeneous strategy.
     */
    public static SimulationStats simulateTreasureStats(Supplier<Strategy> strategyFactory,
                                                        int playersPerGame,
                                                        long[] gameSeeds) {
        Objects.requireNonNull(strategyFactory, "strategyFactory");
        validatePlayersPerGame(playersPerGame);
        long[] seeds = validateGameSeeds(gameSeeds);

        RunningStats gameAverages = new RunningStats();
        for (long gameSeed : seeds) {
            List<Player> players = createPlayers(strategyFactory, playersPerGame);
            Game game = new Game(players, new Random(gameSeed));
            game.playGame();

            long gameTreasure = 0;
            for (Player player : players) {
                gameTreasure += player.getTotalTreasure();
            }
            gameAverages.add(gameTreasure / (double) playersPerGame);
        }
        return gameAverages.snapshot();
    }

    /**
     * Simulates a focus strategy against identical opponent strategies.
     */
    public static MatchupStats simulateMatchup(Supplier<Strategy> focusFactory,
                                               Supplier<Strategy> opponentFactory,
                                               int simulations,
                                               int playersPerGame,
                                               int focusPlayers) {
        return simulateMatchup(
                focusFactory,
                opponentFactory,
                playersPerGame,
                focusPlayers,
                createGameSeeds(simulations, new Random())
        );
    }

    /**
     * Simulates a focus strategy using seeds drawn from the supplied RNG.
     */
    public static MatchupStats simulateMatchup(Supplier<Strategy> focusFactory,
                                               Supplier<Strategy> opponentFactory,
                                               int simulations,
                                               int playersPerGame,
                                               int focusPlayers,
                                               Random random) {
        return simulateMatchup(
                focusFactory,
                opponentFactory,
                playersPerGame,
                focusPlayers,
                createGameSeeds(simulations, random)
        );
    }

    /**
     * Simulates a focus strategy over an explicit set of game seeds.
     */
    public static MatchupStats simulateMatchup(Supplier<Strategy> focusFactory,
                                               Supplier<Strategy> opponentFactory,
                                               int playersPerGame,
                                               int focusPlayers,
                                               long[] gameSeeds) {
        Objects.requireNonNull(focusFactory, "focusFactory");
        Objects.requireNonNull(opponentFactory, "opponentFactory");
        validatePlayersPerGame(playersPerGame);
        if (focusPlayers < 1 || focusPlayers > playersPerGame) {
            throw new IllegalArgumentException(
                    "focusPlayers must be between 1 and playersPerGame: " + focusPlayers);
        }
        long[] seeds = validateGameSeeds(gameSeeds);

        long focusTreasure = 0;
        int focusTopFinishes = 0;
        for (long gameSeed : seeds) {
            List<Player> players = new ArrayList<>(playersPerGame);
            players.addAll(createPlayers(focusFactory, focusPlayers));
            players.addAll(createPlayers(opponentFactory, playersPerGame - focusPlayers));

            Game game = new Game(players, new Random(gameSeed));
            game.playGame();

            Player bestOverall = players.getFirst();
            Player bestFocus = players.getFirst();
            for (int index = 0; index < players.size(); index++) {
                Player player = players.get(index);
                if (player.compareFinalStanding(bestOverall) > 0) {
                    bestOverall = player;
                }
                if (index < focusPlayers) {
                    focusTreasure += player.getTotalTreasure();
                    if (player.compareFinalStanding(bestFocus) > 0) {
                        bestFocus = player;
                    }
                }
            }
            if (bestFocus.compareFinalStanding(bestOverall) == 0) {
                focusTopFinishes++;
            }
        }

        double averageTreasure = focusTreasure / (double) (seeds.length * focusPlayers);
        double topFinishRate = focusTopFinishes * 100.0 / seeds.length;
        return new MatchupStats(averageTreasure, topFinishRate);
    }

    /**
     * Simulates one focus player against a random field of opponent strategies.
     */
    public static MatchupStats simulateMatchupAgainstField(Supplier<Strategy> focusFactory,
                                                           List<Supplier<Strategy>> opponentFactories,
                                                           int simulations,
                                                           int playersPerGame,
                                                           Random random) {
        Objects.requireNonNull(random, "random");
        long[] gameSeeds = createGameSeeds(simulations, random);
        return simulateMatchupAgainstField(
                focusFactory,
                opponentFactories,
                playersPerGame,
                gameSeeds,
                new Random(random.nextLong())
        );
    }

    /**
     * Simulates one focus player against a random field with independent deck and field RNGs.
     */
    public static MatchupStats simulateMatchupAgainstField(Supplier<Strategy> focusFactory,
                                                           List<Supplier<Strategy>> opponentFactories,
                                                           int playersPerGame,
                                                           long[] gameSeeds,
                                                           Random opponentRandom) {
        Objects.requireNonNull(focusFactory, "focusFactory");
        Objects.requireNonNull(opponentFactories, "opponentFactories");
        Objects.requireNonNull(opponentRandom, "opponentRandom");
        validatePlayersPerGame(playersPerGame);
        if (opponentFactories.isEmpty()) {
            throw new IllegalArgumentException("opponentFactories cannot be empty");
        }
        for (Supplier<Strategy> factory : opponentFactories) {
            Objects.requireNonNull(factory, "opponentFactories cannot contain null");
        }
        long[] seeds = validateGameSeeds(gameSeeds);

        long focusTreasure = 0;
        int focusTopFinishes = 0;
        for (long gameSeed : seeds) {
            List<Player> players = new ArrayList<>(playersPerGame);
            players.add(new Player(requireStrategy(focusFactory)));
            for (int index = 1; index < playersPerGame; index++) {
                Supplier<Strategy> factory =
                        opponentFactories.get(opponentRandom.nextInt(opponentFactories.size()));
                players.add(new Player(requireStrategy(factory)));
            }

            Game game = new Game(players, new Random(gameSeed));
            game.playGame();

            Player focusPlayer = players.getFirst();
            Player bestOverall = focusPlayer;
            for (Player player : players) {
                if (player.compareFinalStanding(bestOverall) > 0) {
                    bestOverall = player;
                }
            }
            focusTreasure += focusPlayer.getTotalTreasure();
            if (focusPlayer.compareFinalStanding(bestOverall) == 0) {
                focusTopFinishes++;
            }
        }

        double averageTreasure = focusTreasure / (double) seeds.length;
        double topFinishRate = focusTopFinishes * 100.0 / seeds.length;
        return new MatchupStats(averageTreasure, topFinishRate);
    }

    /**
     * Simulates average cards revealed before a repeated hazard ends a round.
     */
    public static double simulateAverageTurnsUntilDoubleHazard(int simulations) {
        return simulateAverageTurnsUntilDoubleHazard(simulations, new Random());
    }

    /**
     * Simulates average cards revealed using seeds drawn from the supplied RNG.
     */
    public static double simulateAverageTurnsUntilDoubleHazard(int simulations, Random random) {
        long[] gameSeeds = createGameSeeds(simulations, random);
        long totalTurns = 0;
        for (long gameSeed : gameSeeds) {
            RoundLengthGame game = new RoundLengthGame(new Random(gameSeed));
            totalTurns += countTurnsUntilDoubleHazard(game.createRoundDeckForSimulation());
        }
        return totalTurns / (double) gameSeeds.length;
    }

    /**
     * Creates an explicit seed schedule for fair cross-strategy comparisons.
     */
    public static long[] createGameSeeds(int simulations, Random random) {
        Objects.requireNonNull(random, "random");
        if (simulations <= 0) {
            throw new IllegalArgumentException("simulations must be positive: " + simulations);
        }
        long[] seeds = new long[simulations];
        for (int index = 0; index < simulations; index++) {
            seeds[index] = random.nextLong();
        }
        return seeds;
    }

    private static List<Player> createPlayers(Supplier<Strategy> strategyFactory, int count) {
        List<Player> players = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            players.add(new Player(requireStrategy(strategyFactory)));
        }
        return players;
    }

    private static Strategy requireStrategy(Supplier<Strategy> strategyFactory) {
        return Objects.requireNonNull(strategyFactory.get(), "strategyFactory returned null");
    }

    private static long[] validateGameSeeds(long[] gameSeeds) {
        Objects.requireNonNull(gameSeeds, "gameSeeds");
        if (gameSeeds.length == 0) {
            throw new IllegalArgumentException("gameSeeds cannot be empty");
        }
        return gameSeeds.clone();
    }

    private static void validatePlayersPerGame(int playersPerGame) {
        if (playersPerGame <= 0) {
            throw new IllegalArgumentException("playersPerGame must be positive: " + playersPerGame);
        }
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

    /**
     * Aggregate matchup performance.
     *
     * @param averageTreasure average focus-player treasure
     * @param winRate percentage of games where a focus player held or tied the best official standing
     */
    public record MatchupStats(double averageTreasure, double winRate) {
    }

    /**
     * Game-level sampling statistics for an average-treasure estimate.
     */
    public record SimulationStats(double averageTreasure,
                                  double standardDeviation,
                                  double standardError,
                                  int simulations) {
        /**
         * Returns the normal-approximation 95% confidence-interval margin.
         */
        public double confidence95Margin() {
            return CONFIDENCE_95_Z * standardError;
        }
    }

    private static final class RunningStats {
        private int count;
        private double mean;
        private double squaredDeviationSum;

        private void add(double value) {
            count++;
            double delta = value - mean;
            mean += delta / count;
            double deltaAfterMeanUpdate = value - mean;
            squaredDeviationSum += delta * deltaAfterMeanUpdate;
        }

        private SimulationStats snapshot() {
            double variance = count > 1 ? squaredDeviationSum / (count - 1) : 0.0;
            double standardDeviation = Math.sqrt(Math.max(0.0, variance));
            double standardError = standardDeviation / Math.sqrt(count);
            return new SimulationStats(mean, standardDeviation, standardError, count);
        }
    }

    private static final class RoundLengthGame extends Game {
        private RoundLengthGame(Random random) {
            super(new ArrayList<>(), random);
        }

        private List<Card> createRoundDeckForSimulation() {
            return createRoundDeck();
        }
    }
}
