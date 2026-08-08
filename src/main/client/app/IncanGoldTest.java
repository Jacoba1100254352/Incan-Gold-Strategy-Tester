package client.app;

import client.analysis.StrategyCatalog;
import client.analysis.StrategyEvaluator;
import client.analysis.StrategyInteractionEvaluator;
import client.analysis.StrategyRatings;
import client.analysis.StrategySimulator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Runs reproducible strategy sweeps and summarizes average treasure outcomes.
 */
public final class IncanGoldTest {
    private static final int DEFAULT_REPEATS = 20;
    private static final int DEFAULT_SIMULATIONS = 10_000;
    private static final int DEFAULT_PLAYERS_PER_GAME = 4;
    private static final int DEFAULT_MATCHUP_SIMULATIONS = 1_000;
    private static final boolean DEFAULT_INCLUDE_INTERACTION_RATINGS = true;
    private static final boolean DEFAULT_RUN_PLAYER_COUNT_SWEEP = true;
    private static final boolean DEFAULT_RUN_INTERACTIONS = true;
    private static final boolean DEFAULT_BLEND_RATING_HISTORY = false;
    private static final boolean DEFAULT_EXPANDED_VALIDATION_CATALOG = false;
    private static final int PLAYER_SWEEP_MIN = 3;
    private static final int PLAYER_SWEEP_MAX = 8;
    private static final int TOP_STRATEGIES_TO_DISPLAY = 10;

    private static final int REPEATS_ARG_INDEX = 0;
    private static final int SIMULATIONS_ARG_INDEX = 1;
    private static final int PLAYERS_ARG_INDEX = 2;
    private static final int MATCHUP_SIMULATIONS_ARG_INDEX = 3;
    private static final int INTERACTION_RATINGS_ARG_INDEX = 4;
    private static final int SEED_ARG_INDEX = 5;
    private static final int PLAYER_SWEEP_ARG_INDEX = 6;
    private static final int RUN_INTERACTIONS_ARG_INDEX = 7;
    private static final int BLEND_HISTORY_ARG_INDEX = 8;
    private static final int VALIDATION_CATALOG_ARG_INDEX = 9;

    private IncanGoldTest() {
    }

    /**
     * Runs configured sweeps.
     *
     * @param args optional positional values:
     *             repeats, simulations, players, matchup simulations,
     *             include interaction ratings, seed, run player sweep,
     *             run interactions, blend historical ratings,
     *             use expanded validation catalog
     */
    public static void main(String[] args) {
        int repeats = positiveIntArg(args, REPEATS_ARG_INDEX, DEFAULT_REPEATS);
        int simulations = positiveIntArg(args, SIMULATIONS_ARG_INDEX, DEFAULT_SIMULATIONS);
        int playersPerGame = positiveIntArg(args, PLAYERS_ARG_INDEX, DEFAULT_PLAYERS_PER_GAME);
        int matchupSimulations = positiveIntArg(
                args,
                MATCHUP_SIMULATIONS_ARG_INDEX,
                Math.min(simulations, DEFAULT_MATCHUP_SIMULATIONS)
        );
        boolean includeInteractionRatings = booleanArg(
                args,
                INTERACTION_RATINGS_ARG_INDEX,
                DEFAULT_INCLUDE_INTERACTION_RATINGS
        );
        long seed = longArg(args, SEED_ARG_INDEX, new Random().nextLong());
        boolean runPlayerCountSweep = booleanArg(
                args,
                PLAYER_SWEEP_ARG_INDEX,
                DEFAULT_RUN_PLAYER_COUNT_SWEEP
        );
        boolean runInteractions = booleanArg(
                args,
                RUN_INTERACTIONS_ARG_INDEX,
                DEFAULT_RUN_INTERACTIONS
        );
        boolean blendRatingHistory = booleanArg(
                args,
                BLEND_HISTORY_ARG_INDEX,
                DEFAULT_BLEND_RATING_HISTORY
        );
        boolean expandedValidationCatalog = booleanArg(
                args,
                VALIDATION_CATALOG_ARG_INDEX,
                DEFAULT_EXPANDED_VALIDATION_CATALOG
        );

        Random seedGenerator = new Random(seed);
        System.out.printf("Simulation seed: %d%n", seed);

        double averageTurns = StrategySimulator.simulateAverageTurnsUntilDoubleHazard(
                simulations,
                new Random(seedGenerator.nextLong())
        );
        System.out.printf(
                "Stay-as-long-as-possible average cards revealed per expedition: %.2f%n",
                averageTurns
        );

        List<StrategyCatalog.StrategySpec> strategies = expandedValidationCatalog
                ? StrategyCatalog.buildValidationStrategies()
                : StrategyCatalog.buildDefaultStrategies();
        System.out.printf("Strategy catalog: %s (%d candidates)%n",
                expandedValidationCatalog ? "expanded validation" : "default",
                strategies.size());
        if (runPlayerCountSweep) {
            runPlayerCountSweep(strategies, repeats, simulations, seedGenerator);
        }

        List<StrategyEvaluator.StrategyScore> scores = StrategyEvaluator.evaluate(
                strategies,
                repeats,
                simulations,
                playersPerGame,
                new Random(seedGenerator.nextLong()),
                completed -> System.out.printf(
                        "Completed paired batch %d/%d%n",
                        completed,
                        repeats
                )
        );
        printSummary(scores, repeats, simulations, playersPerGame);

        Map<String, StrategyRatings.InteractionPerformance> interactionResults = Map.of();
        if (runInteractions) {
            interactionResults = StrategyInteractionEvaluator.evaluateAndWrite(
                    strategies,
                    matchupSimulations,
                    playersPerGame,
                    seedGenerator.nextLong()
            );
        } else if (includeInteractionRatings) {
            System.out.println("Interaction ratings disabled because matchup evaluation was skipped.");
            includeInteractionRatings = false;
        }

        StrategyRatings.updateRatings(
                buildRatingPerformances(scores),
                buildSourceLabel(seed, repeats, simulations, playersPerGame, expandedValidationCatalog),
                interactionResults,
                includeInteractionRatings,
                blendRatingHistory
        );
    }

    private static void runPlayerCountSweep(List<StrategyCatalog.StrategySpec> strategies,
                                            int repeats,
                                            int simulations,
                                            Random seedGenerator) {
        System.out.printf("%nTop strategy by standard player count (%d-%d players):%n",
                PLAYER_SWEEP_MIN, PLAYER_SWEEP_MAX);
        for (int players = PLAYER_SWEEP_MIN; players <= PLAYER_SWEEP_MAX; players++) {
            List<StrategyEvaluator.StrategyScore> scores = StrategyEvaluator.evaluate(
                    strategies,
                    repeats,
                    simulations,
                    players,
                    new Random(seedGenerator.nextLong())
            );
            StrategyEvaluator.StrategyScore top = sortedByAverage(scores).getFirst();
            System.out.printf(
                    "%d players: %s (%.3f ± %.3f at 95%%)%n",
                    players,
                    top.name,
                    top.average,
                    top.confidence95Margin
            );
        }
        System.out.println();
    }

    private static void printSummary(List<StrategyEvaluator.StrategyScore> scores,
                                     int repeats,
                                     int simulations,
                                     int playersPerGame) {
        List<StrategyEvaluator.StrategyScore> ranked = sortedByAverage(scores);
        int displayCount = Math.min(TOP_STRATEGIES_TO_DISPLAY, ranked.size());

        System.out.printf(
                "%nTop %d strategies for %d players over %d paired batches of %,d games:%n",
                displayCount,
                playersPerGame,
                repeats,
                simulations
        );
        for (int index = 0; index < displayCount; index++) {
            StrategyEvaluator.StrategyScore score = ranked.get(index);
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

        List<StrategyEvaluator.StrategyScore> wins = new ArrayList<>(scores);
        wins.sort(Comparator
                .comparingInt((StrategyEvaluator.StrategyScore score) -> score.wins)
                .reversed()
                .thenComparing(Comparator
                        .comparingDouble((StrategyEvaluator.StrategyScore score) -> score.average)
                        .reversed())
                .thenComparing(score -> score.name));

        StrategyEvaluator.StrategyScore mostConsistent = wins.getFirst();
        System.out.printf(
                "Most paired-batch wins: %s (%d/%d)%n%n",
                mostConsistent.name,
                mostConsistent.wins,
                repeats
        );
    }

    private static List<StrategyEvaluator.StrategyScore> sortedByAverage(
            List<StrategyEvaluator.StrategyScore> scores) {
        List<StrategyEvaluator.StrategyScore> sorted = new ArrayList<>(scores);
        sorted.sort(Comparator
                .comparingDouble((StrategyEvaluator.StrategyScore score) -> score.average)
                .reversed()
                .thenComparing(score -> score.name));
        return sorted;
    }

    private static List<StrategyRatings.StrategyPerformance> buildRatingPerformances(
            List<StrategyEvaluator.StrategyScore> scores) {
        List<StrategyRatings.StrategyPerformance> performances = new ArrayList<>(scores.size());
        for (StrategyEvaluator.StrategyScore score : scores) {
            performances.add(new StrategyRatings.StrategyPerformance(
                    score.name,
                    score.average,
                    score.wins,
                    score.runs
            ));
        }
        return performances;
    }

    private static String buildSourceLabel(long seed,
                                           int repeats,
                                           int simulations,
                                           int playersPerGame,
                                           boolean expandedValidationCatalog) {
        return String.format(
                "strategy-test seed=%d repeats=%d simulations=%d players=%d paired=true catalog=%s",
                seed,
                repeats,
                simulations,
                playersPerGame,
                expandedValidationCatalog ? "validation" : "default"
        );
    }

    private static int positiveIntArg(String[] args, int index, int fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(args[index]);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

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

    private static boolean booleanArg(String[] args, int index, boolean fallback) {
        if (args.length <= index) {
            return fallback;
        }
        String value = args[index].trim();
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }
}
