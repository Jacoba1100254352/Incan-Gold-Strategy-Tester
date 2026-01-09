package client.app;

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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders charts for strategy ratings and interaction win rates from JSON files.
 */
public class StrategyResultsChart extends Application {
    // Default maximum number of strategies to include in each chart.
    private static final int DEFAULT_MAX_STRATEGIES = 20;
    // Default ratings JSON path.
    private static final String DEFAULT_RATINGS_PATH = "results/strategy-ratings.json";
    // Default interactions JSON path.
    private static final String DEFAULT_INTERACTIONS_PATH = "results/strategy-interactions.json";
    // Default base directory for chart exports.
    private static final Path DEFAULT_OUTPUT_BASE_DIR = Paths.get("results", "charts");
    // Default output directory for ratings chart exports.
    private static final Path DEFAULT_RATINGS_OUTPUT_DIR = DEFAULT_OUTPUT_BASE_DIR.resolve("ratings");
    // Default output directory for interaction chart exports.
    private static final Path DEFAULT_INTERACTIONS_OUTPUT_DIR = DEFAULT_OUTPUT_BASE_DIR.resolve("interactions");
    // Default output directory for combined chart exports.
    private static final Path DEFAULT_COMBINED_OUTPUT_DIR = DEFAULT_OUTPUT_BASE_DIR.resolve("combined");
    // Base file name for rating chart exports.
    private static final String RATINGS_BASENAME = "strategy-ratings-chart";
    // Base file name for interaction chart exports.
    private static final String INTERACTIONS_BASENAME = "strategy-interactions-chart";
    // Base file name for combined chart exports.
    private static final String COMBINED_BASENAME = "strategy-combined-chart";
    // Timestamp format for output file names.
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    // Chart window width.
    private static final int CHART_WIDTH = 1200;
    // Chart window height.
    private static final int CHART_HEIGHT = 720;
    // Padding around chart content.
    private static final int CHART_PADDING = 12;

    /**
     * Entry point for rendering strategy result charts.
     *
     * @param args optional args: [maxStrategies] [ratingsPath] [interactionsPath]
     *             [outputBaseDir] [ratingsOutputDir] [interactionsOutputDir] [combinedOutputDir]
     */
    public static void main(String[] args) {
        launch(args);
    }
    /**
     * Handles start.
     */

    @Override
    public void start(Stage stage) {
        Parameters parameters = getParameters();
        List<String> args = parameters.getRaw();
        int maxStrategies = !args.isEmpty()
                ? parsePositiveInt(args.get(0), DEFAULT_MAX_STRATEGIES)
                : DEFAULT_MAX_STRATEGIES;
        Path ratingsPath = args.size() > 1 ? Paths.get(args.get(1)) : Paths.get(DEFAULT_RATINGS_PATH);
        Path interactionsPath = args.size() > 2 ? Paths.get(args.get(2)) : Paths.get(DEFAULT_INTERACTIONS_PATH);
        Path baseOutputDir = args.size() > 3 ? Paths.get(args.get(3)) : DEFAULT_OUTPUT_BASE_DIR;
        Path ratingsOutputDir = args.size() > 4 ? Paths.get(args.get(4)) : baseOutputDir.resolve("ratings");
        Path interactionsOutputDir = args.size() > 5
                ? Paths.get(args.get(5))
                : baseOutputDir.resolve("interactions");
        Path combinedOutputDir = args.size() > 6 ? Paths.get(args.get(6)) : baseOutputDir.resolve("combined");

        List<StrategyMetric> ratingMetrics = loadRatingMetrics(ratingsPath);
        ratingMetrics = limitMetrics(ratingMetrics, maxStrategies);

        List<StrategyMetric> interactionMetrics = Files.exists(interactionsPath)
                ? loadInteractionMetrics(interactionsPath)
                : Collections.emptyList();
        interactionMetrics = limitMetrics(interactionMetrics, maxStrategies);
        List<StrategyMetric> combinedMetrics = limitMetrics(
                combineMetrics(ratingMetrics, interactionMetrics),
                maxStrategies);

        VBox root = new VBox(16);
        root.setPadding(new Insets(CHART_PADDING));

        BarChart<Number, String> ratingChart = buildChart(
                "Strategy Ratings (0-5)",
                "Rating",
                "Strategy",
                ratingMetrics,
                0.0,
                5.0
        );
        root.getChildren().addAll(new Label("Ratings"), ratingChart);

        BarChart<Number, String> interactionChart = null;
        if (!interactionMetrics.isEmpty()) {
            interactionChart = buildChart(
                    "Interaction Win Rate",
                    "Win Rate (%)",
                    "Strategy",
                    interactionMetrics,
                    0.0,
                    100.0
            );
            root.getChildren().addAll(new Label("Interactions"), interactionChart);
        }

        BarChart<Number, String> combinedChart = null;
        if (!combinedMetrics.isEmpty()) {
            combinedChart = buildChart(
                    "Combined Rating + Interaction Score",
                    "Combined Score (0-100)",
                    "Strategy",
                    combinedMetrics,
                    0.0,
                    100.0
            );
            root.getChildren().addAll(new Label("Combined"), combinedChart);
        }

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        stage.setTitle("Incan Gold Strategy Results");
        stage.setScene(new Scene(scrollPane, CHART_WIDTH, CHART_HEIGHT));
        stage.show();

        BarChart<Number, String> finalInteractionChart = interactionChart;
        BarChart<Number, String> finalCombinedChart = combinedChart;
        List<StrategyMetric> finalRatingMetrics = ratingMetrics;
        List<StrategyMetric> finalInteractionMetrics = interactionMetrics;
        List<StrategyMetric> finalCombinedMetrics = combinedMetrics;
        Platform.runLater(() -> {
            saveChart(ratingChart, finalRatingMetrics, RATINGS_BASENAME, "rating",
                    ratingsPath, ratingsOutputDir);
            if (finalInteractionChart != null) {
                saveChart(finalInteractionChart, finalInteractionMetrics, INTERACTIONS_BASENAME,
                        "interactionWinRate", interactionsPath, interactionsOutputDir);
            }
            if (finalCombinedChart != null) {
                saveChart(finalCombinedChart, finalCombinedMetrics, COMBINED_BASENAME,
                        "combinedScore", ratingsPath, combinedOutputDir);
            }
        });
    }
    /**
     * Builds chart.
     */
    private static BarChart<Number, String> buildChart(String title,
                                                       String xLabel,
                                                       String yLabel,
                                                       List<StrategyMetric> metrics,
                                                       double minX,
                                                       double maxX) {
        NumberAxis xAxis = new NumberAxis(minX, maxX, Math.max(1.0, (maxX - minX) / 5.0));
        xAxis.setLabel(xLabel);
        xAxis.setAutoRanging(false);
        CategoryAxis yAxis = new CategoryAxis();
        yAxis.setLabel(yLabel);
        yAxis.setTickLabelRotation(0);

        BarChart<Number, String> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(8);
        chart.setBarGap(2);
        chart.setPrefHeight(Math.max(320, metrics.size() * 28 + 80));

        XYChart.Series<Number, String> series = new XYChart.Series<>();
        for (StrategyMetric metric : metrics) {
            series.getData().add(new XYChart.Data<>(metric.value, metric.name));
        }
        chart.getData().add(series);
        return chart;
    }
    /**
     * Saves chart.
     */
    private static void saveChart(BarChart<?, ?> chart,
                                  List<StrategyMetric> metrics,
                                  String baseName,
                                  String metricLabel,
                                  Path sourcePath,
                                  Path outputDir) {
        OffsetDateTime generatedAt = OffsetDateTime.now();
        String timestamp = generatedAt.format(FILE_TIMESTAMP_FORMAT);
        Path imagePath = outputDir.resolve(baseName + "-" + timestamp + ".png");
        Path metadataPath = outputDir.resolve(baseName + "-" + timestamp + ".json");

        try {
            Files.createDirectories(outputDir);
            chart.applyCss();
            chart.layout();

            ImageIO.write(SwingFXUtils.fromFXImage(chart.snapshot(new SnapshotParameters(), null), null),
                    "png", imagePath.toFile());
            Files.writeString(metadataPath,
                    buildMetadataJson(metrics, metricLabel, sourcePath, imagePath.getFileName().toString(),
                            generatedAt),
                    StandardCharsets.UTF_8);
            System.out.printf("Saved chart image to %s%n", imagePath.toAbsolutePath());
            System.out.printf("Saved chart metadata to %s%n", metadataPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save chart output: " + e.getMessage());
        }
    }
    /**
     * Builds metadata json.
     */
    private static String buildMetadataJson(List<StrategyMetric> metrics,
                                            String metricLabel,
                                            Path sourcePath,
                                            String imageFileName,
                                            OffsetDateTime generatedAt) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"generatedAt\": \"")
                .append(generatedAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append("\",\n");
        builder.append("  \"metric\": \"").append(metricLabel).append("\",\n");
        if (sourcePath != null) {
            builder.append("  \"source\": \"").append(escapeJson(sourcePath.toString())).append("\",\n");
        }
        builder.append("  \"image\": \"").append(escapeJson(imageFileName)).append("\",\n");
        builder.append("  \"strategies\": [\n");
        for (int i = 0; i < metrics.size(); i++) {
            StrategyMetric metric = metrics.get(i);
            builder.append("    {\n");
            builder.append("      \"name\": \"").append(escapeJson(metric.name)).append("\",\n");
            builder.append("      \"value\": ").append(formatNumber(metric.value)).append("\n");
            builder.append("    }");
            if (i < metrics.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }
    /**
     * Loads rating metrics.
     */
    private static List<StrategyMetric> loadRatingMetrics(Path path) {
        String content = readFile(path);
        if (content == null) {
            return Collections.emptyList();
        }
        List<String> entries = extractStrategyObjects(content);
        List<StrategyMetric> metrics = new ArrayList<>();
        for (String entry : entries) {
            String name = extractStringField(entry, "name");
            double rating = extractNumberField(entry, "rating", Double.NaN);
            if (name != null && !Double.isNaN(rating)) {
                metrics.add(new StrategyMetric(name, rating));
            }
        }
        metrics.sort(Comparator.comparingDouble(StrategyMetric::value).reversed()
                .thenComparing(metric -> metric.name.toLowerCase(Locale.US)));
        return metrics;
    }
    /**
     * Loads interaction metrics.
     */
    private static List<StrategyMetric> loadInteractionMetrics(Path path) {
        String content = readFile(path);
        if (content == null) {
            return Collections.emptyList();
        }
        List<String> entries = extractStrategyObjects(content);
        List<StrategyMetric> metrics = new ArrayList<>();
        for (String entry : entries) {
            String name = extractStringField(entry, "name");
            double winRate = extractNumberField(entry, "mixedOpponentsWinRate", Double.NaN);
            if (name != null && !Double.isNaN(winRate)) {
                metrics.add(new StrategyMetric(name, winRate));
            }
        }
        metrics.sort(Comparator.comparingDouble(StrategyMetric::value).reversed()
                .thenComparing(metric -> metric.name.toLowerCase(Locale.US)));
        return metrics;
    }
    /**
     * Handles combine metrics.
     */
    private static List<StrategyMetric> combineMetrics(List<StrategyMetric> ratingMetrics,
                                                       List<StrategyMetric> interactionMetrics) {
        if (ratingMetrics.isEmpty() || interactionMetrics.isEmpty()) {
            return Collections.emptyList();
        }
        List<StrategyMetric> combined = new ArrayList<>();
        java.util.Map<String, Double> ratingsByName = new java.util.HashMap<>();
        for (StrategyMetric metric : ratingMetrics) {
            ratingsByName.put(metric.name, metric.value);
        }
        for (StrategyMetric interaction : interactionMetrics) {
            Double rating = ratingsByName.get(interaction.name);
            if (rating == null) {
                continue;
            }
            double normalizedRating = clamp(rating / 5.0, 0.0, 1.0) * 100.0;
            double normalizedWinRate = clamp(interaction.value, 0.0, 100.0);
            double combinedScore = (normalizedRating + normalizedWinRate) / 2.0;
            combined.add(new StrategyMetric(interaction.name, combinedScore));
        }
        combined.sort(Comparator.comparingDouble(StrategyMetric::value).reversed()
                .thenComparing(metric -> metric.name.toLowerCase(Locale.US)));
        return combined;
    }
    /**
     * Handles limit metrics.
     */
    private static List<StrategyMetric> limitMetrics(List<StrategyMetric> metrics, int max) {
        if (metrics.size() <= max) {
            return metrics;
        }
        return new ArrayList<>(metrics.subList(0, max));
    }
    /**
     * Handles extract strategy objects.
     */
    private static List<String> extractStrategyObjects(String content) {
        int index = content.indexOf("\"strategies\"");
        if (index < 0) {
            return Collections.emptyList();
        }
        int arrayStart = content.indexOf('[', index);
        if (arrayStart < 0) {
            return Collections.emptyList();
        }
        int arrayEnd = findMatchingBracket(content, arrayStart, '[', ']');
        if (arrayEnd < 0) {
            return Collections.emptyList();
        }

        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        int objectStart = -1;
        for (int i = arrayStart + 1; i < arrayEnd; i++) {
            char c = content.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(content.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }
        return objects;
    }
    /**
     * Handles find matching bracket.
     */
    private static int findMatchingBracket(String content, int start, char open, char close) {
        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
    /**
     * Handles extract string field.
     */
    private static String extractStringField(String entry, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(.*?)\"",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(entry);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }
    /**
     * Handles extract number field.
     */
    private static double extractNumberField(String entry, String field, double fallback) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([-0-9.]+)");
        Matcher matcher = pattern.matcher(entry);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
    /**
     * Reads file.
     */
    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read file: " + path + " (" + e.getMessage() + ")");
            return null;
        }
    }
    /**
     * Parses positive int.
     */
    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
    /**
     * Formats number.
     */
    private static String formatNumber(double value) {
        return String.format(Locale.US, "%.4f", value);
    }
    /**
     * Handles clamp.
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }
    /**
     * Handles escape json.
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
    /**
     * Handles unescape json.
     */
    private static String unescapeJson(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private record StrategyMetric(String name, double value) {
    }
}
