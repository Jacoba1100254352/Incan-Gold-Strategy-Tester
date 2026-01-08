package client;

import algorithm.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Runs strategy sweeps and summarizes average treasure outcomes.
 */
public class IncanGoldTest {
    // Default number of sweep runs when no repeat count is provided.
    private static final int DEFAULT_REPEATS = 20;
    // Default number of simulations per strategy.
    private static final int SIMULATIONS = 10000;
    // Default number of players per simulated game.
    private static final int DEFAULT_PLAYERS_PER_GAME = 4;
    // Argument index for repeat count.
    private static final int REPEATS_ARG_INDEX = 0;
    // Argument index for simulations per strategy.
    private static final int SIMULATIONS_ARG_INDEX = 1;
    // Minimum allowed repeat count.
    private static final int MIN_REPEATS = 1;
    // Minimum allowed simulations per strategy.
    private static final int MIN_SIMULATIONS = 1;
    // Minimum players per game for player-count sweeps.
    private static final int PLAYER_SWEEP_MIN = 2;
    // Maximum players per game for player-count sweeps.
    private static final int PLAYER_SWEEP_MAX = 8;
    // Toggle to run the player-count sweep after the main sweep.
    private static final boolean ENABLE_PLAYER_COUNT_SWEEP = true;
    // Number of top strategies to show per player count in the sweep.
    private static final int PLAYER_SWEEP_TOP_COUNT = 1;
    // Minimum allowed players per game.
    private static final int MIN_PLAYERS_PER_GAME = 1;
    // Argument index for players per game.
    private static final int PLAYERS_ARG_INDEX = 2;
    // Number of top strategies to list in the summary.
    private static final int TOP_STRATEGIES_TO_DISPLAY = 10;
    // Tolerance for treating averages as ties.
    private static final double TIE_EPSILON = 1e-9;
    // Number format for win rate percentages.
    private static final String WIN_RATE_FORMAT = "%.2f";

    /**
     * Entry point for running sweep simulations.
     *
     * @param args optional args: [repeats] [simulations] [playersPerGame]
     */
    public static void main(String[] args) {
        int repeats = args.length > REPEATS_ARG_INDEX
                ? Integer.parseInt(args[REPEATS_ARG_INDEX])
                : DEFAULT_REPEATS;
        int simulations = args.length > SIMULATIONS_ARG_INDEX
                ? Integer.parseInt(args[SIMULATIONS_ARG_INDEX])
                : SIMULATIONS;
        int playersPerGame = args.length > PLAYERS_ARG_INDEX
                ? Integer.parseInt(args[PLAYERS_ARG_INDEX])
                : DEFAULT_PLAYERS_PER_GAME;

        if (repeats < MIN_REPEATS) {
            repeats = DEFAULT_REPEATS;
        }
        if (simulations < MIN_SIMULATIONS) {
            simulations = SIMULATIONS;
        }
        if (playersPerGame < MIN_PLAYERS_PER_GAME) {
            playersPerGame = DEFAULT_PLAYERS_PER_GAME;
        }

        double averageTurns = StrategySimulator.simulateAverageTurnsUntilDoubleHazard(simulations);
        System.out.printf("Stay as long as possible average turns survived per round: %.2f%n", averageTurns);

        List<StrategyCatalog.StrategySpec> strategies = StrategyCatalog.buildDefaultStrategies();
        if (ENABLE_PLAYER_COUNT_SWEEP) {
            runPlayerCountSweep(strategies, repeats, simulations);
        }
        runSweeps(strategies, repeats, simulations, playersPerGame);
    }

    /**
     * Executes the sweeps and prints per-run winners and a summary.
     */
    private static void runSweeps(List<StrategyCatalog.StrategySpec> strategies,
                                  int repeats,
                                  int simulations,
                                  int playersPerGame) {
        List<StrategyStats> stats = evaluateStrategies(strategies, repeats, simulations, playersPerGame, true);
        printSummary(stats, repeats);
        StrategyRatings.updateRatings(buildRatingPerformances(stats), "strategy-test");
    }

    /**
     * Sweeps player counts and prints the top strategy for each size.
     */
    private static void runPlayerCountSweep(List<StrategyCatalog.StrategySpec> strategies,
                                            int repeats,
                                            int simulations) {
        System.out.printf("%nTop strategy by player count (%d-%d players):%n",
                PLAYER_SWEEP_MIN, PLAYER_SWEEP_MAX);
        for (int playersPerGame = PLAYER_SWEEP_MIN; playersPerGame <= PLAYER_SWEEP_MAX; playersPerGame++) {
            List<StrategyStats> stats = evaluateStrategies(strategies, repeats, simulations, playersPerGame, false);
            StrategyStats top = getTopStrategies(stats, PLAYER_SWEEP_TOP_COUNT).getFirst();
            System.out.printf("%d players: %s (%.2f)%n",
                    playersPerGame, top.name, top.getAverage());
        }
        System.out.println();
    }

    /**
     * Runs simulations and accumulates per-strategy averages and win counts.
     */
    private static List<StrategyStats> evaluateStrategies(List<StrategyCatalog.StrategySpec> strategies,
                                                          int repeats,
                                                          int simulations,
                                                          int playersPerGame,
                                                          boolean verbose) {
        List<StrategyStats> stats = new ArrayList<>();
        for (StrategyCatalog.StrategySpec spec : strategies) {
            stats.add(new StrategyStats(spec.name, spec.factory));
        }

        for (int run = 1; run <= repeats; run++) {
            double bestAverage = Double.NEGATIVE_INFINITY;
            List<StrategyStats> runWinners = new ArrayList<>();

            if (verbose && repeats > DEFAULT_REPEATS) {
                System.out.printf("Sweep %d/%d (simulations per strategy: %d)%n", run, repeats, simulations);
            }

            for (StrategyStats stat : stats) {
                double average = StrategySimulator.simulateAverageTreasure(stat.factory, simulations, playersPerGame);
                stat.recordRun(average);

                if (verbose && repeats == DEFAULT_REPEATS) {
                    System.out.printf("%s average treasure: %.2f%n", stat.name, average);
                }

                if (average > bestAverage + TIE_EPSILON) {
                    bestAverage = average;
                    runWinners.clear();
                    runWinners.add(stat);
                } else if (Math.abs(average - bestAverage) <= TIE_EPSILON) {
                    runWinners.add(stat);
                }
            }

            for (StrategyStats winner : runWinners) {
                winner.recordWin();
            }

            if (verbose) {
                System.out.printf("Run %d winner%s: %s (%.2f)%n", run, runWinners.size() > 1 ? "s" : "",
                        joinNames(runWinners), bestAverage);
            }
        }

        return stats;
    }

    /**
     * Prints the ranked summary and win counts across sweep runs.
     */
    private static void printSummary(List<StrategyStats> stats, int repeats) {
        int mostWins = -1;
        List<StrategyStats> mostWinStrategies = new ArrayList<>();

        for (StrategyStats stat : stats) {
            if (stat.wins > mostWins) {
                mostWins = stat.wins;
                mostWinStrategies.clear();
                mostWinStrategies.add(stat);
            } else if (stat.wins == mostWins) {
                mostWinStrategies.add(stat);
            }
        }

        List<StrategyStats> topStrategies = getTopStrategies(stats, TOP_STRATEGIES_TO_DISPLAY);
        int displayCount = topStrategies.size();

        System.out.printf("%nTop %d average%s over %d run%s:%n",
                displayCount,
                displayCount == DEFAULT_REPEATS ? "" : "s",
                repeats,
                repeats == DEFAULT_REPEATS ? "" : "s");

        for (int i = 0; i < topStrategies.size(); i++) {
            StrategyStats stat = topStrategies.get(i);
            System.out.printf("%d) %s (%.2f)%n", i + 1, stat.name, stat.getAverage());
        }
        System.out.printf("Most run wins: %s (%d/%d)%n",
                joinNames(mostWinStrategies), mostWins, repeats);
        printWinRates(stats, repeats);
    }

    /**
     * Prints win rate percentages by strategy.
     */
    private static void printWinRates(List<StrategyStats> stats, int repeats) {
        List<StrategyStats> sorted = new ArrayList<>(stats);
        sorted.sort((left, right) -> {
            int winCompare = Integer.compare(right.wins, left.wins);
            if (winCompare != 0) {
                return winCompare;
            }
            return Double.compare(right.getAverage(), left.getAverage());
        });

        System.out.printf("%nWin rate by strategy (%d run%s):%n",
                repeats, repeats == 1 ? "" : "s");
        for (StrategyStats stat : sorted) {
            double percent = repeats == 0 ? 0.0 : (stat.wins * 100.0) / repeats;
            System.out.printf("%s: %d/%d (%s%%)%n",
                    stat.name,
                    stat.wins,
                    repeats,
                    String.format(WIN_RATE_FORMAT, percent));
        }
        System.out.println();
    }

    /**
     * Returns the top strategies by average treasure.
     */
    private static List<StrategyStats> getTopStrategies(List<StrategyStats> stats, int count) {
        List<StrategyStats> sortedByAverage = new ArrayList<>(stats);
        sortedByAverage.sort((left, right) -> Double.compare(right.getAverage(), left.getAverage()));
        int displayCount = Math.min(count, sortedByAverage.size());
        return new ArrayList<>(sortedByAverage.subList(0, displayCount));
    }

    /**
     * Joins strategy names for compact output.
     */
    private static String joinNames(List<StrategyStats> stats) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stats.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(stats.get(i).name);
        }
        return builder.toString();
    }

    private static List<StrategyRatings.StrategyPerformance> buildRatingPerformances(List<StrategyStats> stats) {
        List<StrategyRatings.StrategyPerformance> performances = new ArrayList<>();
        for (StrategyStats stat : stats) {
            performances.add(new StrategyRatings.StrategyPerformance(
                    stat.name,
                    stat.getAverage(),
                    stat.wins,
                    stat.runs
            ));
        }
        return performances;
    }

    /**
     * Accumulates averages and win counts for a strategy.
     */
    private static class StrategyStats {
        private final String name;
        private final Supplier<Strategy> factory;
        private double totalAverage;
        private int runs;
        private int wins;

        private StrategyStats(String name, Supplier<Strategy> factory) {
            this.name = name;
            this.factory = factory;
        }

        private void recordRun(double average) {
            totalAverage += average;
            runs++;
        }

        private void recordWin() {
            wins++;
        }

        private double getAverage() {
            return totalAverage / runs;
        }
    }
}
