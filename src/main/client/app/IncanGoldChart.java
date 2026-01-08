package client.app;

import client.analysis.StrategyCatalog;
import client.analysis.StrategyEvaluator;
import client.analysis.StrategyRatings;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    // Number format for metadata averages.
    private static final String METADATA_AVERAGE_FORMAT = "%.4f";
    // Output directory for chart exports.
    private static final String OUTPUT_DIR_NAME = "results";
    // Base name for chart exports.
    private static final String OUTPUT_BASENAME = "incan-gold-chart";
    // Timestamp format for output file names.
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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
        List<BucketSection> sections = new ArrayList<>();
        Map<String, PerformanceAccumulator> ratingTotals = new LinkedHashMap<>();

        for (int players = params.minPlayersPerGame; players <= params.maxPlayersPerGame; players++) {
            List<StrategyEvaluator.StrategyScore> scores = StrategyEvaluator.evaluate(
                    strategies,
                    params.repeats,
                    params.simulations,
                    players
            );
            for (StrategyEvaluator.StrategyScore score : scores) {
                ratingTotals
                        .computeIfAbsent(score.name, key -> new PerformanceAccumulator())
                        .add(score);
            }
            BucketResult bucketResult = bucketScores(scores);
            StrategyEvaluator.StrategyScore bestScore = findBestScore(scores);
            String commonFactor = summarizeCommonFactor(bucketResult.topBucketScores());
            BucketSection section = new BucketSection(players, bucketResult, bestScore, commonFactor);
            sections.add(section);
            content.getChildren().add(createBucketSection(section));
        }
        StrategyRatings.updateRatings(buildRatingPerformances(ratingTotals), "chart");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);

        stage.setTitle("Incan Gold Strategy Buckets");
        stage.setScene(new Scene(scrollPane, CHART_WIDTH, CHART_HEIGHT));
        stage.show();
        Platform.runLater(() -> saveSnapshotAndMetadata(content, sections, params));
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
        return new BucketResult(buckets, topBucketScores, topBucket);
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
    private static VBox createBucketSection(BucketSection section) {
        Label subtitle = new Label(String.format(
                "%d players | Top strategy: %s (%s). Most common factor in top bucket: %s.",
                section.playersPerGame,
                section.bestScore.name,
                String.format(AVERAGE_FORMAT, section.bestScore.average),
                section.commonFactor
        ));

        BarChart<String, Number> chart = createChart(section.bucketResult);
        chart.setPrefHeight(CHART_SECTION_HEIGHT);

        VBox sectionBox = new VBox(CHART_PADDING, subtitle, chart);
        sectionBox.setPadding(new Insets(CHART_PADDING, 0, 0, 0));
        return sectionBox;
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
     * Saves a snapshot of the chart content and a JSON metadata file.
     */
    private static void saveSnapshotAndMetadata(VBox content,
                                                List<BucketSection> sections,
                                                SimulationParams params) {
        OffsetDateTime generatedAt = OffsetDateTime.now();
        String timestamp = generatedAt.format(FILE_TIMESTAMP_FORMAT);
        Path outputDir = Paths.get(OUTPUT_DIR_NAME);
        String baseName = OUTPUT_BASENAME + "-" + timestamp;
        Path imagePath = outputDir.resolve(baseName + ".png");
        Path metadataPath = outputDir.resolve(baseName + ".json");

        try {
            Files.createDirectories(outputDir);
            content.applyCss();
            content.layout();

            WritableImage image = content.snapshot(new SnapshotParameters(), null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", imagePath.toFile());

            String metadata = buildMetadataJson(sections, params, image, imagePath.getFileName().toString(), generatedAt);
            Files.writeString(metadataPath, metadata, StandardCharsets.UTF_8);

            System.out.printf("Saved chart image to %s%n", imagePath.toAbsolutePath());
            System.out.printf("Saved chart metadata to %s%n", metadataPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save chart output: " + e.getMessage());
        }
    }

    /**
     * Builds JSON metadata for the exported chart.
     */
    private static String buildMetadataJson(List<BucketSection> sections,
                                            SimulationParams params,
                                            WritableImage image,
                                            String imageFileName,
                                            OffsetDateTime generatedAt) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"generatedAt\": \"")
                .append(generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append("\",\n");
        builder.append("  \"repeats\": ").append(params.repeats).append(",\n");
        builder.append("  \"simulations\": ").append(params.simulations).append(",\n");
        builder.append("  \"minPlayersPerGame\": ").append(params.minPlayersPerGame).append(",\n");
        builder.append("  \"maxPlayersPerGame\": ").append(params.maxPlayersPerGame).append(",\n");
        builder.append("  \"image\": {\n");
        builder.append("    \"file\": \"").append(escapeJson(imageFileName)).append("\",\n");
        builder.append("    \"width\": ").append((int) Math.round(image.getWidth())).append(",\n");
        builder.append("    \"height\": ").append((int) Math.round(image.getHeight())).append("\n");
        builder.append("  },\n");
        builder.append("  \"sections\": [\n");

        for (int i = 0; i < sections.size(); i++) {
            BucketSection section = sections.get(i);
            builder.append("    {\n");
            builder.append("      \"playersPerGame\": ").append(section.playersPerGame).append(",\n");
            builder.append("      \"topStrategy\": {\n");
            builder.append("        \"name\": \"").append(escapeJson(section.bestScore.name)).append("\",\n");
            builder.append("        \"average\": ").append(formatAverage(section.bestScore.average)).append("\n");
            builder.append("      },\n");
            builder.append("      \"commonTopBucketFactor\": \"")
                    .append(escapeJson(section.commonFactor))
                    .append("\",\n");
            builder.append("      \"topBucket\": ").append(section.bucketResult.topBucket).append(",\n");
            builder.append("      \"buckets\": [\n");

            int bucketIndex = 0;
            int bucketCount = section.bucketResult.buckets.size();
            for (Map.Entry<Integer, List<StrategyEvaluator.StrategyScore>> entry :
                    section.bucketResult.buckets.entrySet()) {
                builder.append("        {\n");
                builder.append("          \"bucket\": ").append(entry.getKey()).append(",\n");
                builder.append("          \"strategies\": [\n");

                List<StrategyEvaluator.StrategyScore> bucketScores = entry.getValue();
                for (int scoreIndex = 0; scoreIndex < bucketScores.size(); scoreIndex++) {
                    StrategyEvaluator.StrategyScore score = bucketScores.get(scoreIndex);
                    builder.append("            {\n");
                    builder.append("              \"name\": \"")
                            .append(escapeJson(score.name))
                            .append("\",\n");
                    builder.append("              \"average\": ")
                            .append(formatAverage(score.average))
                            .append("\n");
                    builder.append("            }");
                    if (scoreIndex < bucketScores.size() - 1) {
                        builder.append(",");
                    }
                    builder.append("\n");
                }

                builder.append("          ]\n");
                builder.append("        }");
                if (bucketIndex < bucketCount - 1) {
                    builder.append(",");
                }
                builder.append("\n");
                bucketIndex++;
            }

            builder.append("      ]\n");
            builder.append("    }");
            if (i < sections.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }

        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    /**
     * Formats an average value for metadata output.
     */
    private static String formatAverage(double average) {
        return String.format(Locale.US, METADATA_AVERAGE_FORMAT, average);
    }

    /**
     * Escapes a string for JSON output.
     */
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

    private static List<StrategyRatings.StrategyPerformance> buildRatingPerformances(
            Map<String, PerformanceAccumulator> totals) {
        List<StrategyRatings.StrategyPerformance> performances = new ArrayList<>();
        for (Map.Entry<String, PerformanceAccumulator> entry : totals.entrySet()) {
            PerformanceAccumulator accumulator = entry.getValue();
            performances.add(new StrategyRatings.StrategyPerformance(
                    entry.getKey(),
                    accumulator.average(),
                    accumulator.wins,
                    accumulator.runs
            ));
        }
        return performances;
    }

    private static class PerformanceAccumulator {
        private double total;
        private int count;
        private int wins;
        private int runs;

        private void add(StrategyEvaluator.StrategyScore score) {
            total += score.average;
            count++;
            wins += score.wins;
            runs += score.runs;
        }

        private double average() {
            return count == 0 ? 0.0 : total / count;
        }
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
     * Prepared chart section data.
     */
    private record BucketSection(int playersPerGame,
                                 BucketResult bucketResult,
                                 StrategyEvaluator.StrategyScore bestScore,
                                 String commonFactor)
    {
    }


    /**
     * Bucketed score results for rendering.
     */
    private record BucketResult(Map<Integer, List<StrategyEvaluator.StrategyScore>> buckets,
                                List<StrategyEvaluator.StrategyScore> topBucketScores,
                                int topBucket)
    {
    }
}
