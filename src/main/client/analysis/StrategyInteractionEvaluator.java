package client.analysis;

import algorithm.Strategy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Evaluates how strategies perform against other strategies and writes a report.
 */
public class StrategyInteractionEvaluator {
    private static final String OUTPUT_DIR = "results";
    private static final String OUTPUT_FILE = "strategy-interactions.json";
    private static final String NUMBER_FORMAT = "%.4f";
    private static final double UNAFFECTED_THRESHOLD = 0.25;

    /**
     * Runs strategy matchups and writes a JSON report to the results folder.
     *
     * @param strategies strategies to evaluate
     * @param simulations simulations per matchup
     * @param playersPerGame players per simulated game
     * @return per-strategy interaction performance summary
     */
    public static Map<String, StrategyRatings.InteractionPerformance> evaluateAndWrite(
            List<StrategyCatalog.StrategySpec> strategies,
            int simulations,
            int playersPerGame) {
        InteractionReport report = evaluate(strategies, simulations, playersPerGame);
        writeReport(report);
        return buildInteractionMap(report);
    }

    private static InteractionReport evaluate(List<StrategyCatalog.StrategySpec> strategies,
                                              int simulations,
                                              int playersPerGame) {
        List<StrategyResult> results = new ArrayList<>();
        List<StrategyCatalog.StrategySpec> sortedStrategies = new ArrayList<>(strategies);
        sortedStrategies.sort(Comparator.comparing(spec -> spec.name));
        Random random = new Random();

        for (StrategyCatalog.StrategySpec focus : sortedStrategies) {
            double mirrorAverage = StrategySimulator.simulateAverageTreasure(
                    focus.factory,
                    simulations,
                    playersPerGame);

            List<OpponentResult> matchups = new ArrayList<>();
            double maxAbsDelta = 0.0;
            double maxDelta = Double.NEGATIVE_INFINITY;
            double minDelta = Double.POSITIVE_INFINITY;
            OpponentResult mostAffectedBy = null;

            for (StrategyCatalog.StrategySpec opponent : sortedStrategies) {
                if (opponent == focus) {
                    continue;
                }
                StrategySimulator.MatchupStats stats = StrategySimulator.simulateMatchup(
                        focus.factory,
                        opponent.factory,
                        simulations,
                        playersPerGame,
                        1);
                double delta = stats.averageTreasure() - mirrorAverage;
                OpponentResult matchup = new OpponentResult(opponent.name, stats.averageTreasure(), delta,
                        stats.winRate());
                matchups.add(matchup);

                double absDelta = Math.abs(delta);
                if (absDelta > maxAbsDelta) {
                    maxAbsDelta = absDelta;
                    mostAffectedBy = matchup;
                }
                maxDelta = Math.max(maxDelta, delta);
                minDelta = Math.min(minDelta, delta);
            }
            matchups.sort((left, right) -> {
                int winRateCompare = Double.compare(left.winRate, right.winRate);
                if (winRateCompare != 0) {
                    return winRateCompare;
                }
                int deltaCompare = Double.compare(left.delta, right.delta);
                if (deltaCompare != 0) {
                    return deltaCompare;
                }
                return left.opponent.compareToIgnoreCase(right.opponent);
            });

            StrategySimulator.MatchupStats mixedStats = simulateAgainstField(
                    focus.name,
                    focus.factory,
                    sortedStrategies,
                    simulations,
                    playersPerGame,
                    random);
            double mixedDelta = mixedStats.averageTreasure() - mirrorAverage;
            if (mostAffectedBy == null) {
                maxDelta = 0.0;
                minDelta = 0.0;
            }
            boolean unaffected = maxAbsDelta <= UNAFFECTED_THRESHOLD;
            results.add(new StrategyResult(
                    focus.name,
                    mirrorAverage,
                    mixedStats.averageTreasure(),
                    mixedDelta,
                    mixedStats.winRate(),
                    maxAbsDelta,
                    maxDelta,
                    minDelta,
                    unaffected,
                    mostAffectedBy,
                    matchups
            ));
        }

        List<MostAffectedEntry> mostAffected = new ArrayList<>();
        for (StrategyResult result : results) {
            if (result.mostAffectedBy == null) {
                continue;
            }
            mostAffected.add(new MostAffectedEntry(
                    result.name,
                    result.maxAbsDelta,
                    result.mostAffectedBy.opponent,
                    result.mostAffectedBy.delta
            ));
        }
        mostAffected.sort(Comparator.comparingDouble((MostAffectedEntry entry) -> entry.maxAbsDelta).reversed());

        results.sort((left, right) -> {
            int averageCompare = Double.compare(right.mixedAverage, left.mixedAverage);
            if (averageCompare != 0) {
                return averageCompare;
            }
            int winRateCompare = Double.compare(right.mixedWinRate, left.mixedWinRate);
            if (winRateCompare != 0) {
                return winRateCompare;
            }
            return left.name.compareToIgnoreCase(right.name);
        });
        return new InteractionReport(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                simulations,
                playersPerGame,
                UNAFFECTED_THRESHOLD,
                results,
                mostAffected
        );
    }

    private static StrategySimulator.MatchupStats simulateAgainstField(
            String focusName,
            Supplier<Strategy> focusFactory,
            List<StrategyCatalog.StrategySpec> allStrategies,
            int simulations,
            int playersPerGame,
            Random random) {
        List<Supplier<Strategy>> opponentFactories = new ArrayList<>();
        for (StrategyCatalog.StrategySpec spec : allStrategies) {
            if (!spec.name.equals(focusName)) {
                opponentFactories.add(spec.factory);
            }
        }
        if (opponentFactories.isEmpty()) {
            return new StrategySimulator.MatchupStats(0.0, 0.0);
        }
        return StrategySimulator.simulateMatchupAgainstField(
                focusFactory,
                opponentFactories,
                simulations,
                playersPerGame,
                random
        );
    }

    private static void writeReport(InteractionReport report) {
        Path outputPath = Paths.get(OUTPUT_DIR, OUTPUT_FILE);
        try {
            Files.createDirectories(outputPath.getParent());
            String json = buildJson(report);
            Files.writeString(outputPath, json, StandardCharsets.UTF_8);
            System.out.printf("Saved strategy interaction report to %s%n", outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write strategy interaction report: " + e.getMessage());
        }
    }

    private static Map<String, StrategyRatings.InteractionPerformance> buildInteractionMap(
            InteractionReport report) {
        Map<String, StrategyRatings.InteractionPerformance> interactionMap = new HashMap<>();
        for (StrategyResult result : report.results) {
            interactionMap.put(result.name,
                    new StrategyRatings.InteractionPerformance(
                            result.name,
                            result.mixedAverage,
                            result.mixedWinRate));
        }
        return interactionMap;
    }

    private static String buildJson(InteractionReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"generatedAt\": \"").append(report.generatedAt).append("\",\n");
        builder.append("  \"simulations\": ").append(report.simulations).append(",\n");
        builder.append("  \"playersPerGame\": ").append(report.playersPerGame).append(",\n");
        builder.append("  \"unaffectedThreshold\": ").append(formatNumber(report.unaffectedThreshold)).append(",\n");
        builder.append("  \"strategies\": [\n");

        for (int i = 0; i < report.results.size(); i++) {
            StrategyResult result = report.results.get(i);
            builder.append("    {\n");
            builder.append("      \"name\": \"").append(escapeJson(result.name)).append("\",\n");
            builder.append("      \"mirrorAverage\": ").append(formatNumber(result.mirrorAverage)).append(",\n");
            builder.append("      \"mixedOpponentsAverage\": ").append(formatNumber(result.mixedAverage)).append(",\n");
            builder.append("      \"mixedOpponentsDelta\": ").append(formatNumber(result.mixedDelta)).append(",\n");
            builder.append("      \"mixedOpponentsWinRate\": ").append(formatNumber(result.mixedWinRate)).append(",\n");
            builder.append("      \"maxAbsDelta\": ").append(formatNumber(result.maxAbsDelta)).append(",\n");
            builder.append("      \"maxDelta\": ").append(formatNumber(result.maxDelta)).append(",\n");
            builder.append("      \"minDelta\": ").append(formatNumber(result.minDelta)).append(",\n");
            builder.append("      \"unaffected\": ").append(result.unaffected).append(",\n");
            if (result.mostAffectedBy != null) {
                builder.append("      \"mostAffectedBy\": {\n");
                builder.append("        \"strategy\": \"").append(escapeJson(result.mostAffectedBy.opponent)).append("\",\n");
                builder.append("        \"delta\": ").append(formatNumber(result.mostAffectedBy.delta)).append("\n");
                builder.append("      },\n");
            }
            builder.append("      \"matchups\": [\n");
            for (int j = 0; j < result.matchups.size(); j++) {
                OpponentResult matchup = result.matchups.get(j);
                builder.append("        {\n");
                builder.append("          \"opponent\": \"").append(escapeJson(matchup.opponent)).append("\",\n");
                builder.append("          \"average\": ").append(formatNumber(matchup.average)).append(",\n");
                builder.append("          \"delta\": ").append(formatNumber(matchup.delta)).append(",\n");
                builder.append("          \"winRate\": ").append(formatNumber(matchup.winRate)).append("\n");
                builder.append("        }");
                if (j < result.matchups.size() - 1) {
                    builder.append(",");
                }
                builder.append("\n");
            }
            builder.append("      ]\n");
            builder.append("    }");
            if (i < report.results.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("  ],\n");
        builder.append("  \"mostAffected\": [\n");
        for (int i = 0; i < report.mostAffected.size(); i++) {
            MostAffectedEntry entry = report.mostAffected.get(i);
            builder.append("    {\n");
            builder.append("      \"name\": \"").append(escapeJson(entry.name)).append("\",\n");
            builder.append("      \"maxAbsDelta\": ").append(formatNumber(entry.maxAbsDelta)).append(",\n");
            builder.append("      \"opponent\": \"").append(escapeJson(entry.opponent)).append("\",\n");
            builder.append("      \"delta\": ").append(formatNumber(entry.delta)).append("\n");
            builder.append("    }");
            if (i < report.mostAffected.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, NUMBER_FORMAT, value);
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format(Locale.US, "\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private record InteractionReport(String generatedAt,
                                     int simulations,
                                     int playersPerGame,
                                     double unaffectedThreshold,
                                     List<StrategyResult> results,
                                     List<MostAffectedEntry> mostAffected) {
    }

    private record StrategyResult(String name,
                                  double mirrorAverage,
                                  double mixedAverage,
                                  double mixedDelta,
                                  double mixedWinRate,
                                  double maxAbsDelta,
                                  double maxDelta,
                                  double minDelta,
                                  boolean unaffected,
                                  OpponentResult mostAffectedBy,
                                  List<OpponentResult> matchups) {
    }

    private record OpponentResult(String opponent,
                                  double average,
                                  double delta,
                                  double winRate) {
    }

    private record MostAffectedEntry(String name,
                                     double maxAbsDelta,
                                     String opponent,
                                     double delta) {
    }
}
