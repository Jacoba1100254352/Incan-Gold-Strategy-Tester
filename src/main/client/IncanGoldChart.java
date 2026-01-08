package client;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Displays a bar chart that groups strategies by integer treasure averages.
 */
public class IncanGoldChart extends Application {
    // Default number of sweep runs when no repeat count is provided.
    private static final int DEFAULT_REPEATS = 20;
    // Default number of simulations per strategy.
    private static final int DEFAULT_SIMULATIONS = 10000;
    // Default number of players per simulated game.
    private static final int DEFAULT_PLAYERS_PER_GAME = 4;
    // Minimum allowed repeat count.
    private static final int MIN_REPEATS = 1;
    // Minimum allowed simulations per strategy.
    private static final int MIN_SIMULATIONS = 1;
    // Minimum allowed players per game.
    private static final int MIN_PLAYERS_PER_GAME = 1;
    // Argument index for repeat count.
    private static final int REPEATS_ARG_INDEX = 0;
    // Argument index for simulations per strategy.
    private static final int SIMULATIONS_ARG_INDEX = 1;
    // Argument index for players per game.
    private static final int PLAYERS_ARG_INDEX = 2;
    // Argument index for max players per game.
    private static final int MAX_PLAYERS_ARG_INDEX = 3;
    // Default minimum players per game for chart sweeps.
    private static final int DEFAULT_PLAYER_SWEEP_MIN = 2;
    // Default maximum players per game for chart sweeps.
    private static final int DEFAULT_PLAYER_SWEEP_MAX = 8;
    // Chart window width.
    private static final int CHART_WIDTH = 960;
    // Chart window height.
    private static final int CHART_HEIGHT = 640;
    // Chart height per player-count section.
    private static final int CHART_SECTION_HEIGHT = 260;
    // Padding used around chart content.
    private static final int CHART_PADDING = 12;
    // Spacing between chart sections.
    private static final int SECTION_SPACING = 16;
    // Number format for averages.
    private static final String AVERAGE_FORMAT = "%.2f";

    /**
     * Launches the JavaFX chart.
     *
     * @param args optional args: [repeats] [simulations] [playersPerGame] or [minPlayers] [maxPlayers]
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Parameters parameters = getParameters();
        SimulationParams params = parseParams(parameters.getRaw());

        List<StrategyCatalog.StrategySpec> strategies = StrategyCatalog.buildDefaultStrategies();
        VBox content = new VBox(SECTION_SPACING);
        content.setPadding(new Insets(CHART_PADDING));

        for (int players = params.minPlayersPerGame; players <= params.maxPlayersPerGame; players++) {
            List<StrategyEvaluator.StrategyScore> scores = StrategyEvaluator.evaluate(
                    strategies,
                    params.repeats,
                    params.simulations,
                    players
            );
            content.getChildren().add(createBucketSection(scores, players));
        }

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);

        stage.setTitle("Incan Gold Strategy Buckets");
        stage.setScene(new Scene(scrollPane, CHART_WIDTH, CHART_HEIGHT));
        stage.show();
    }

    /**
     * Parses command line parameters with defaults and basic validation.
     */
    private static SimulationParams parseParams(List<String> rawArgs) {
        int repeats = !rawArgs.isEmpty()
                ? parsePositiveInt(rawArgs.get(REPEATS_ARG_INDEX), DEFAULT_REPEATS)
                : DEFAULT_REPEATS;
        int simulations = rawArgs.size() > SIMULATIONS_ARG_INDEX
                ? parsePositiveInt(rawArgs.get(SIMULATIONS_ARG_INDEX), DEFAULT_SIMULATIONS)
                : DEFAULT_SIMULATIONS;
        int minPlayersPerGame = DEFAULT_PLAYER_SWEEP_MIN;
        int maxPlayersPerGame = DEFAULT_PLAYER_SWEEP_MAX;
        if (rawArgs.size() > PLAYERS_ARG_INDEX) {
            int singlePlayerCount = parsePositiveInt(rawArgs.get(PLAYERS_ARG_INDEX), DEFAULT_PLAYERS_PER_GAME);
            minPlayersPerGame = singlePlayerCount;
            maxPlayersPerGame = singlePlayerCount;
        }
        if (rawArgs.size() > MAX_PLAYERS_ARG_INDEX) {
            minPlayersPerGame = parsePositiveInt(rawArgs.get(PLAYERS_ARG_INDEX), DEFAULT_PLAYER_SWEEP_MIN);
            maxPlayersPerGame = parsePositiveInt(rawArgs.get(MAX_PLAYERS_ARG_INDEX), DEFAULT_PLAYER_SWEEP_MAX);
        }

        repeats = Math.max(repeats, MIN_REPEATS);
        simulations = Math.max(simulations, MIN_SIMULATIONS);
        minPlayersPerGame = Math.max(minPlayersPerGame, MIN_PLAYERS_PER_GAME);
        maxPlayersPerGame = Math.max(maxPlayersPerGame, minPlayersPerGame);

        return new SimulationParams(repeats, simulations, minPlayersPerGame, maxPlayersPerGame);
    }

    /**
     * Parses a positive integer from a string, returning a fallback on error.
     */
    private static int parsePositiveInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Groups strategy scores into integer treasure buckets.
     */
    private static BucketResult bucketScores(List<StrategyEvaluator.StrategyScore> scores) {
        Map<Integer, List<StrategyEvaluator.StrategyScore>> buckets = new TreeMap<>(Collections.reverseOrder());
        for (StrategyEvaluator.StrategyScore score : scores) {
            int bucket = (int) Math.floor(score.average);
            buckets.computeIfAbsent(bucket, key -> new ArrayList<>()).add(score);
        }

        int topBucket = buckets.isEmpty() ? 0 : buckets.keySet().iterator().next();
        List<StrategyEvaluator.StrategyScore> topBucketScores =
                buckets.getOrDefault(topBucket, Collections.emptyList());
        return new BucketResult(buckets, topBucketScores);
    }

    /**
     * Finds the best strategy by average treasure.
     */
    private static StrategyEvaluator.StrategyScore findBestScore(List<StrategyEvaluator.StrategyScore> scores) {
        StrategyEvaluator.StrategyScore best = scores.getFirst();
        for (StrategyEvaluator.StrategyScore score : scores) {
            if (score.average > best.average) {
                best = score;
            }
        }
        return best;
    }

    /**
     * Creates a labeled section containing a bucket chart for a player count.
     */
    private static VBox createBucketSection(List<StrategyEvaluator.StrategyScore> scores, int playersPerGame) {
        BucketResult buckets = bucketScores(scores);
        StrategyEvaluator.StrategyScore bestScore = findBestScore(scores);
        String commonFactor = summarizeCommonFactor(buckets.topBucketScores);

        Label subtitle = new Label(String.format(
                "%d players | Top strategy: %s (%s). Most common factor in top bucket: %s.",
                playersPerGame,
                bestScore.name,
                String.format(AVERAGE_FORMAT, bestScore.average),
                commonFactor
        ));

        BarChart<String, Number> chart = createChart(buckets);
        chart.setPrefHeight(CHART_SECTION_HEIGHT);

        VBox section = new VBox(CHART_PADDING, subtitle, chart);
        section.setPadding(new Insets(CHART_PADDING, 0, 0, 0));
        return section;
    }

    /**
     * Builds the bar chart with tooltips listing strategies per bucket.
     */
    private static BarChart<String, Number> createChart(BucketResult result) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Average treasure bucket");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Strategies in bucket");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<Integer, List<StrategyEvaluator.StrategyScore>> entry : result.buckets.entrySet()) {
            String bucketLabel = String.valueOf(entry.getKey());
            List<StrategyEvaluator.StrategyScore> bucketScores = entry.getValue();
            XYChart.Data<String, Number> data = new XYChart.Data<>(bucketLabel, bucketScores.size());
            series.getData().add(data);

            String tooltipText = buildTooltipText(bucketScores);
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip.install(newNode, new Tooltip(tooltipText));
                }
            });
        }

        chart.getData().add(series);
        return chart;
    }

    /**
     * Builds tooltip text listing strategies and averages for a bucket.
     */
    private static String buildTooltipText(List<StrategyEvaluator.StrategyScore> scores) {
        StringBuilder builder = new StringBuilder();
        for (StrategyEvaluator.StrategyScore score : scores) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(score.name)
                    .append(" (")
                    .append(String.format(AVERAGE_FORMAT, score.average))
                    .append(')');
        }
        return builder.toString();
    }

    /**
     * Summarizes the most common keyword among the top bucket strategies.
     */
    private static String summarizeCommonFactor(List<StrategyEvaluator.StrategyScore> scores) {
        if (scores.isEmpty()) {
            return "n/a";
        }

        Map<String, Integer> keywordCounts = new LinkedHashMap<>();
        keywordCounts.put("hazards", 0);
        keywordCounts.put("turns", 0);
        keywordCounts.put("treasure", 0);
        keywordCounts.put("temple", 0);
        keywordCounts.put("switch", 0);

        for (StrategyEvaluator.StrategyScore score : scores) {
            String name = score.name.toLowerCase();
            for (Map.Entry<String, Integer> entry : keywordCounts.entrySet()) {
                if (name.contains(entry.getKey())) {
                    keywordCounts.put(entry.getKey(), entry.getValue() + 1);
                }
            }
        }

        int maxCount = 0;
        List<String> topKeywords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : keywordCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                topKeywords.clear();
                topKeywords.add(entry.getKey());
            } else if (entry.getValue() == maxCount && entry.getValue() > 0) {
                topKeywords.add(entry.getKey());
            }
        }

        if (maxCount == 0) {
            return "n/a";
        }
        if (topKeywords.size() == 1) {
            return topKeywords.getFirst();
        }
        return "tie: " + String.join(", ", topKeywords);
    }
    
    /**
     * Parsed simulation parameters.
     */
    private record SimulationParams(int repeats,
                                    int simulations,
                                    int minPlayersPerGame,
                                    int maxPlayersPerGame)
    {
    }
    
    
    /**
     * Bucketed score results for rendering.
     */
    private record BucketResult(Map<Integer, List<StrategyEvaluator.StrategyScore>> buckets,
                                    List<StrategyEvaluator.StrategyScore> topBucketScores)
    {
    }
}
