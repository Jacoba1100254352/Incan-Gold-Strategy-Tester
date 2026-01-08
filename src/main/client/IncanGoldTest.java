package client;

import algorithm.AlwaysContinueStrategy;
import algorithm.LeaveAfterHazardsOrTurnsStrategy;
import algorithm.LeaveAfterHazardsStrategy;
import algorithm.LeaveAfterTempleTreasureStrategy;
import algorithm.LeaveAfterTreasureOrHazardsStrategy;
import algorithm.LeaveAfterTreasureStrategy;
import algorithm.LeaveAfterTurnsStrategy;
import algorithm.LeaveWhenSoloStrategy;
import algorithm.RiskAverseStrategy;
import algorithm.Strategy;
import algorithm.SwitchAfterHazardsStrategy;
import model.Game;
import model.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class IncanGoldTest {
    private static final int SIMULATIONS = 10000;
    private static final int PLAYERS_PER_GAME = 4;
    private static final double TIE_EPSILON = 1e-9;

    public static void main(String[] args) {
        int repeats = args.length > 0 ? Integer.parseInt(args[0]) : 1;
        int simulations = args.length > 1 ? Integer.parseInt(args[1]) : SIMULATIONS;

        if (repeats < 1) {
            repeats = 1;
        }
        if (simulations < 1) {
            simulations = SIMULATIONS;
        }

        List<StrategySpec> strategies = buildStrategies();
        runSweeps(strategies, repeats, simulations);
    }

    private static List<StrategySpec> buildStrategies() {
        List<StrategySpec> strategies = new ArrayList<>();
        strategies.add(new StrategySpec("Stay as long as possible", AlwaysContinueStrategy::new));
        strategies.add(new StrategySpec("Leave after 1 hazard", RiskAverseStrategy::new));
        strategies.add(new StrategySpec("Leave when solo", LeaveWhenSoloStrategy::new));

        addHazardSweep(strategies, 2, 4);
        addTurnSweep(strategies, 2, 15);
        addTreasureSweep(strategies, 2, 10, 1);
        addTempleTreasureSweep(strategies, 2, 10, 1);
        addHazardsOrTurnsSweep(strategies, 1, 3, 2, 8, 2);
        addTreasureOrHazardsSweep(strategies, 5, 20, 5, 1, 3);
        addSwitchAfterHazardsSweep(strategies, 1, 3, 2, 6, 1);

        return strategies;
    }

    private static void runSweeps(List<StrategySpec> strategies, int repeats, int simulations) {
        List<StrategyStats> stats = new ArrayList<>();
        for (StrategySpec spec : strategies) {
            stats.add(new StrategyStats(spec.name, spec.factory));
        }

        for (int run = 1; run <= repeats; run++) {
            double bestAverage = Double.NEGATIVE_INFINITY;
            List<StrategyStats> runWinners = new ArrayList<>();

            if (repeats > 1) {
                System.out.printf("Sweep %d/%d (simulations per strategy: %d)%n", run, repeats, simulations);
            }

            for (StrategyStats stat : stats) {
                double average = runSimulation(stat.factory, simulations);
                stat.recordRun(average);

                if (repeats == 1) {
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

            System.out.printf("Run %d winner%s: %s (%.2f)%n", run, runWinners.size() > 1 ? "s" : "",
                    joinNames(runWinners), bestAverage);
        }

        printSummary(stats, repeats);
    }

    private static void printSummary(List<StrategyStats> stats, int repeats) {
        double bestAverage = Double.NEGATIVE_INFINITY;
        List<StrategyStats> bestAverageStrategies = new ArrayList<>();
        int mostWins = -1;
        List<StrategyStats> mostWinStrategies = new ArrayList<>();

        for (StrategyStats stat : stats) {
            double average = stat.getAverage();
            if (average > bestAverage + TIE_EPSILON) {
                bestAverage = average;
                bestAverageStrategies.clear();
                bestAverageStrategies.add(stat);
            } else if (Math.abs(average - bestAverage) <= TIE_EPSILON) {
                bestAverageStrategies.add(stat);
            }

            if (stat.wins > mostWins) {
                mostWins = stat.wins;
                mostWinStrategies.clear();
                mostWinStrategies.add(stat);
            } else if (stat.wins == mostWins) {
                mostWinStrategies.add(stat);
            }
        }

        System.out.printf("%nBest average over %d run%s: %s (%.2f)%n",
                repeats, repeats == 1 ? "" : "s", joinNames(bestAverageStrategies), bestAverage);
        System.out.printf("Most run wins: %s (%d/%d)%n",
                joinNames(mostWinStrategies), mostWins, repeats);
    }

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

    private static double runSimulation(Supplier<Strategy> strategyFactory, int simulations) {
        long totalTreasure = 0;
        for (int i = 0; i < simulations; i++) {
            List<Player> players = new ArrayList<>();
            for (int p = 0; p < PLAYERS_PER_GAME; p++) {
                players.add(new Player(strategyFactory.get()));
            }
            Game game = new Game(players);
            game.playGame();
            for (Player player : players) {
                totalTreasure += player.getTotalTreasure();
            }
        }
        return totalTreasure / (double) (simulations * PLAYERS_PER_GAME);
    }

    private static class StrategySpec {
        private final String name;
        private final Supplier<Strategy> factory;

        private StrategySpec(String name, Supplier<Strategy> factory) {
            this.name = name;
            this.factory = factory;
        }
    }

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

    private static void addHazardSweep(List<StrategySpec> strategies, int min, int max) {
        for (int hazards = min; hazards <= max; hazards++) {
            final int threshold = hazards;
            strategies.add(new StrategySpec("Leave after " + hazards + " hazards",
                    () -> new LeaveAfterHazardsStrategy(threshold)));
        }
    }

    private static void addTempleTreasureSweep(List<StrategySpec> strategies, int min, int max, int step) {
        for (int treasure = min; treasure <= max; treasure += step) {
            final int threshold = treasure;
            strategies.add(new StrategySpec("Leave after temple treasure " + treasure,
                    () -> new LeaveAfterTempleTreasureStrategy(threshold)));
        }
    }

    private static void addTurnSweep(List<StrategySpec> strategies, int min, int max) {
        for (int turns = min; turns <= max; turns++) {
            final int threshold = turns;
            strategies.add(new StrategySpec("Leave after " + turns + " turns",
                    () -> new LeaveAfterTurnsStrategy(threshold)));
        }
    }

    private static void addTreasureSweep(List<StrategySpec> strategies, int min, int max, int step) {
        for (int treasure = min; treasure <= max; treasure += step) {
            final int threshold = treasure;
            strategies.add(new StrategySpec("Leave after " + treasure + " treasure",
                    () -> new LeaveAfterTreasureStrategy(threshold)));
        }
    }

    private static void addHazardsOrTurnsSweep(List<StrategySpec> strategies, int hazardMin, int hazardMax,
                                               int turnMin, int turnMax, int turnStep) {
        for (int hazards = hazardMin; hazards <= hazardMax; hazards++) {
            for (int turns = turnMin; turns <= turnMax; turns += turnStep) {
                final int hazardThreshold = hazards;
                final int turnThreshold = turns;
                strategies.add(new StrategySpec("Leave after " + hazards + " hazards or " + turns + " turns",
                        () -> new LeaveAfterHazardsOrTurnsStrategy(hazardThreshold, turnThreshold)));
            }
        }
    }

    private static void addTreasureOrHazardsSweep(List<StrategySpec> strategies, int treasureMin, int treasureMax,
                                                  int treasureStep, int hazardMin, int hazardMax) {
        for (int treasure = treasureMin; treasure <= treasureMax; treasure += treasureStep) {
            for (int hazards = hazardMin; hazards <= hazardMax; hazards++) {
                final int treasureThreshold = treasure;
                final int hazardThreshold = hazards;
                strategies.add(new StrategySpec("Leave after " + treasure + " treasure or " + hazards + " hazards",
                        () -> new LeaveAfterTreasureOrHazardsStrategy(treasureThreshold, hazardThreshold)));
            }
        }
    }

    private static void addSwitchAfterHazardsSweep(List<StrategySpec> strategies, int hazardMin, int hazardMax,
                                                   int turnMin, int turnMax, int turnStep) {
        for (int hazards = hazardMin; hazards <= hazardMax; hazards++) {
            for (int turns = turnMin; turns <= turnMax; turns += turnStep) {
                final int hazardThreshold = hazards;
                final int turnThreshold = turns;
                strategies.add(new StrategySpec("Switch after " + hazards + " hazards (stay->leave after " + turns + " turns)",
                        () -> new SwitchAfterHazardsStrategy(
                                hazardThreshold,
                                new AlwaysContinueStrategy(),
                                new LeaveAfterTurnsStrategy(turnThreshold))));
            }
        }
    }
}
