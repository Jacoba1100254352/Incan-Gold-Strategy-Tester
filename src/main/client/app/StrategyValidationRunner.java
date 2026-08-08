package client.app;

import client.analysis.StrategyCatalog;
import client.analysis.StrategyEvaluator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Runs a broad strategy screen followed by a higher-sample finalist comparison.
 */
public final class StrategyValidationRunner {
    private static final int DEFAULT_SCREEN_REPEATS = 5;
    private static final int DEFAULT_SCREEN_SIMULATIONS = 2_000;
    private static final int DEFAULT_FINALISTS = 10;
    private static final int DEFAULT_FINAL_REPEATS = 20;
    private static final int DEFAULT_FINAL_SIMULATIONS = 10_000;
    private static final int DEFAULT_PLAYERS = 4;

    private StrategyValidationRunner() {
    }

    /**
     * Runs the two-stage validation workflow.
     *
     * @param args optional values: screen repeats, screen simulations, finalists,
     *             final repeats, final simulations, players, seed
     */
    public static void main(String[] args) {
        int screenRepeats = positiveIntArg(args, 0, DEFAULT_SCREEN_REPEATS);
        int screenSimulations = positiveIntArg(args, 1, DEFAULT_SCREEN_SIMULATIONS);
        int finalistCount = positiveIntArg(args, 2, DEFAULT_FINALISTS);
        int finalRepeats = positiveIntArg(args, 3, DEFAULT_FINAL_REPEATS);
        int finalSimulations = positiveIntArg(args, 4, DEFAULT_FINAL_SIMULATIONS);
        int players = positiveIntArg(args, 5, DEFAULT_PLAYERS);
        long seed = longArg(args, 6, new Random().nextLong());
        Random seedGenerator = new Random(seed);

        List<StrategyCatalog.StrategySpec> candidates = StrategyCatalog.buildValidationStrategies();
        System.out.printf(
                "Validation seed: %d%nStage 1: %d candidates, %d batches of %,d games%n",
                seed,
                candidates.size(),
                screenRepeats,
                screenSimulations
        );
        List<StrategyEvaluator.StrategyScore> screeningScores = StrategyEvaluator.evaluate(
                candidates,
                screenRepeats,
                screenSimulations,
                players,
                new Random(seedGenerator.nextLong()),
                completed -> System.out.printf(
                        "Completed screening batch %d/%d%n",
                        completed,
                        screenRepeats
                )
        );

        List<StrategyEvaluator.StrategyScore> rankedScreening = sorted(screeningScores);
        int selectedCount = Math.min(finalistCount, rankedScreening.size());
        printScores("Screening finalists", rankedScreening.subList(0, selectedCount));

        List<StrategyCatalog.StrategySpec> finalists = new ArrayList<>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            StrategyEvaluator.StrategyScore score = rankedScreening.get(index);
            finalists.add(new StrategyCatalog.StrategySpec(score.name, score.factory));
        }

        System.out.printf(
                "%nStage 2: %d finalists, %d batches of %,d games%n",
                finalists.size(),
                finalRepeats,
                finalSimulations
        );
        List<StrategyEvaluator.StrategyScore> finalScores = StrategyEvaluator.evaluate(
                finalists,
                finalRepeats,
                finalSimulations,
                players,
                new Random(seedGenerator.nextLong()),
                completed -> System.out.printf(
                        "Completed finalist batch %d/%d%n",
                        completed,
                        finalRepeats
                )
        );
        List<StrategyEvaluator.StrategyScore> finalRanking = sorted(finalScores);
        printScores("Final ranking", finalRanking);
        printPairedComparisons(finalRanking);
    }

    /**
     * Returns scores ordered by average treasure and then by name.
     */
    private static List<StrategyEvaluator.StrategyScore> sorted(
            List<StrategyEvaluator.StrategyScore> scores) {
        List<StrategyEvaluator.StrategyScore> sorted = new ArrayList<>(scores);
        sorted.sort(Comparator
                .comparingDouble((StrategyEvaluator.StrategyScore score) -> score.average)
                .reversed()
                .thenComparing(score -> score.name));
        return sorted;
    }

    /**
     * Prints one ranked validation stage with uncertainty and batch wins.
     */
    private static void printScores(String heading,
                                    List<StrategyEvaluator.StrategyScore> scores) {
        System.out.printf("%n%s:%n", heading);
        for (int index = 0; index < scores.size(); index++) {
            StrategyEvaluator.StrategyScore score = scores.get(index);
            System.out.printf(
                    "%d) %s — %.3f ± %.3f (95%% CI), batch wins %d/%d%n",
                    index + 1,
                    score.name,
                    score.average,
                    score.confidence95Margin,
                    score.wins,
                    score.runs
            );
        }
    }

    /**
     * Prints paired batch differences between the winner and every other finalist.
     */
    private static void printPairedComparisons(List<StrategyEvaluator.StrategyScore> scores) {
        if (scores.size() < 2 || scores.getFirst().runs < 2) {
            return;
        }

        StrategyEvaluator.StrategyScore winner = scores.getFirst();
        System.out.printf("%nPaired advantages for %s:%n", winner.name);
        for (int index = 1; index < scores.size(); index++) {
            StrategyEvaluator.StrategyScore comparison = scores.get(index);
            StrategyEvaluator.PairedDifferenceStats difference =
                    winner.pairedDifferenceFrom(comparison);
            System.out.printf(
                    "over %s — %.3f ± %.3f (paired-batch 95%% CI)%n",
                    comparison.name,
                    difference.meanDifference(),
                    difference.confidence95Margin()
            );
        }
    }

    /**
     * Parses a positive integer argument or returns its fallback.
     */
    private static int positiveIntArg(String[] args, int index, int fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(args[index]);
            return value > 0 ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses a long argument or returns its fallback.
     */
    private static long longArg(String[] args, int index, long fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
