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
    // Base file name for rating chart exports.
    private static final String RATINGS_BASENAME = "strategy-ratings-chart";
    // Base file name for interaction chart exports.
    private static final String INTERACTIONS_BASENAME = "strategy-interactions-chart";
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
     *             [outputBaseDir] [ratingsOutputDir] [interactionsOutputDir]
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Parameters parameters = getParameters();
        List<String> args = parameters.getRaw();
        int maxStrategies = args.size() > 0
                ? parsePositiveInt(args.get(0), DEFAULT_MAX_STRATEGIES)
                : DEFAULT_MAX_STRATEGIES;
        Path ratingsPath = args.size() > 1 ? Paths.get(args.get(1)) : Paths.get(DEFAULT_RATINGS_PATH);
        Path interactionsPath = args.size() > 2 ? Paths.get(args.get(2)) : Paths.get(DEFAULT_INTERACTIONS_PATH);
        Path baseOutputDir = args.size() > 3 ? Paths.get(args.get(3)) : DEFAULT_OUTPUT_BASE_DIR;
        Path ratingsOutputDir = args.size() > 4 ? Paths.get(args.get(4)) : baseOutputDir.resolve("ratings");
        Path interactionsOutputDir = args.size() > 5
                ? Paths.get(args.get(5))
                : baseOutputDir.resolve("interactions");

        List<StrategyMetric> ratingMetrics = loadRatingMetrics(ratingsPath);
        ratingMetrics = limitMetrics(ratingMetrics, maxStrategies);

        List<StrategyMetric> interactionMetrics = Files.exists(interactionsPath)
                ? loadInteractionMetrics(interactionsPath)
                : Collections.emptyList();
        interactionMetrics = limitMetrics(interactionMetrics, maxStrategies);

        VBox root = new VBox(16);
        root.setPadding(new Insets(CHART_PADDING));

        BarChart<String, Number> ratingChart = buildChart(
                "Strategy Ratings (0-5)",
                "Strategy",
                "Rating",
                ratingMetrics,
                0.0,
                5.0
        );
        root.getChildren().addAll(new Label("Ratings"), ratingChart);

        BarChart<String, Number> interactionChart = null;
        if (!interactionMetrics.isEmpty()) {
            interactionChart = buildChart(
                    "Interaction Win Rate",
                    "Strategy",
                    "Win Rate (%)",
                    interactionMetrics,
                    0.0,
                    100.0
            );
            root.getChildren().addAll(new Label("Interactions"), interactionChart);
        }

        stage.setTitle("Incan Gold Strategy Results");
        stage.setScene(new Scene(root, CHART_WIDTH, CHART_HEIGHT));
        stage.show();

        BarChart<String, Number> finalInteractionChart = interactionChart;
        List<StrategyMetric> finalRatingMetrics = ratingMetrics;
        List<StrategyMetric> finalInteractionMetrics = interactionMetrics;
        Platform.runLater(() -> {
            saveChart(ratingChart, finalRatingMetrics, RATINGS_BASENAME, "rating",
                    ratingsPath, ratingsOutputDir);
            if (finalInteractionChart != null) {
                saveChart(finalInteractionChart, finalInteractionMetrics, INTERACTIONS_BASENAME,
                        "interactionWinRate", interactionsPath, interactionsOutputDir);
            }
        });
    }

    private static BarChart<String, Number> buildChart(String title,
                                                       String xLabel,
                                                       String yLabel,
                                                       List<StrategyMetric> metrics,
                                                       double minY,
                                                       double maxY) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(xLabel);
        xAxis.setTickLabelRotation(-45);
        NumberAxis yAxis = new NumberAxis(minY, maxY, Math.max(1.0, (maxY - minY) / 5.0));
        yAxis.setLabel(yLabel);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (StrategyMetric metric : metrics) {
            series.getData().add(new XYChart.Data<>(metric.name, metric.value));
        }
        chart.getData().add(series);
        return chart;
    }

    private static void saveChart(BarChart<String, Number> chart,
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

    private static List<StrategyMetric> limitMetrics(List<StrategyMetric> metrics, int max) {
        if (metrics.size() <= max) {
            return metrics;
        }
        return new ArrayList<>(metrics.subList(0, max));
    }

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

    private static String extractStringField(String entry, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"(.*?)\"",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(entry);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

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

    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to read file: " + path + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String formatNumber(double value) {
        return String.format(Locale.US, "%.4f", value);
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
