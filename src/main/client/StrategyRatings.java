package client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maintains a shared ratings file for strategy performance.
 */
public class StrategyRatings {
    private static final Path RATINGS_PATH = Paths.get("strategy-ratings.json");
    private static final double MAX_RATING = 5.0;
    private static final double MIN_RATING = 0.0;
    private static final double DEFAULT_WEIGHT = 0.5;
    private static final String NUMBER_FORMAT = "%.4f";
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"(.*?)\"\\s*,\\s*\"rating\"\\s*:\\s*([0-9.]+)",
            Pattern.DOTALL);

    /**
     * Represents a strategy average for ranking.
     */
    public record StrategyAverage(String name, double average) {
    }

    /**
     * Updates the ratings JSON based on the provided averages.
     */
    public static void updateRatings(List<StrategyAverage> averages, String sourceLabel) {
        if (averages == null || averages.isEmpty()) {
            return;
        }

        Map<String, Double> previousRatings = loadRatings();
        List<StrategyAverage> sorted = new ArrayList<>(averages);
        sorted.sort((left, right) -> Double.compare(right.average, left.average));

        int totalStrategies = sorted.size();
        Map<String, ScoreInfo> scoreInfo = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            StrategyAverage average = sorted.get(i);
            int scoreRank = i + 1;
            double scoreRating = ratingFromRank(scoreRank, totalStrategies);
            scoreInfo.put(average.name, new ScoreInfo(scoreRank, scoreRating, average.average));
        }

        List<RatingEntry> entries = new ArrayList<>();
        for (StrategyAverage average : sorted) {
            ScoreInfo info = scoreInfo.get(average.name);
            double previous = previousRatings.getOrDefault(average.name, info.scoreRating);
            double updated = blendRating(previous, info.scoreRating);
            entries.add(new RatingEntry(average.name, updated, 0, info.scoreRank, info.scoreRating, info.average));
        }

        entries.sort((left, right) -> {
            int comparison = Double.compare(right.rating, left.rating);
            if (comparison != 0) {
                return comparison;
            }
            return left.name.compareToIgnoreCase(right.name);
        });

        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).ratingRank = i + 1;
        }

        writeRatings(entries, sourceLabel);
    }

    private static Map<String, Double> loadRatings() {
        Map<String, Double> ratings = new HashMap<>();
        if (!Files.exists(RATINGS_PATH)) {
            return ratings;
        }
        try {
            String content = Files.readString(RATINGS_PATH, StandardCharsets.UTF_8);
            Matcher matcher = ENTRY_PATTERN.matcher(content);
            while (matcher.find()) {
                String name = unescapeJson(matcher.group(1));
                double rating = Double.parseDouble(matcher.group(2));
                ratings.put(name, clampRating(rating));
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Failed to read strategy ratings: " + e.getMessage());
        }
        return ratings;
    }

    private static void writeRatings(List<RatingEntry> entries, String sourceLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"updatedAt\": \"")
                .append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .append("\",\n");
        builder.append("  \"ratingWeight\": ").append(formatNumber(DEFAULT_WEIGHT)).append(",\n");
        if (sourceLabel != null && !sourceLabel.isBlank()) {
            builder.append("  \"source\": \"").append(escapeJson(sourceLabel)).append("\",\n");
        }
        builder.append("  \"strategies\": [\n");

        for (int i = 0; i < entries.size(); i++) {
            RatingEntry entry = entries.get(i);
            builder.append("    {\n");
            builder.append("      \"name\": \"").append(escapeJson(entry.name)).append("\",\n");
            builder.append("      \"rating\": ").append(formatNumber(entry.rating)).append(",\n");
            builder.append("      \"ratingRank\": ").append(entry.ratingRank).append(",\n");
            builder.append("      \"scoreRank\": ").append(entry.scoreRank).append(",\n");
            builder.append("      \"scoreRating\": ").append(formatNumber(entry.scoreRating)).append(",\n");
            builder.append("      \"lastAverage\": ").append(formatNumber(entry.lastAverage)).append("\n");
            builder.append("    }");
            if (i < entries.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }

        builder.append("  ]\n");
        builder.append("}\n");

        try {
            Files.writeString(RATINGS_PATH, builder.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to write strategy ratings: " + e.getMessage());
        }
    }

    private static double blendRating(double previous, double current) {
        double blended = previous * (1.0 - DEFAULT_WEIGHT) + current * DEFAULT_WEIGHT;
        return clampRating(blended);
    }

    private static double ratingFromRank(int rank, int total) {
        if (total <= 1) {
            return MAX_RATING;
        }
        double step = MAX_RATING / (total - 1);
        double rating = MAX_RATING - (rank - 1) * step;
        return clampRating(rating);
    }

    private static double clampRating(double rating) {
        if (rating < MIN_RATING) {
            return MIN_RATING;
        }
        if (rating > MAX_RATING) {
            return MAX_RATING;
        }
        return rating;
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

    private static String unescapeJson(String value) {
        StringBuilder unescaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                unescaped.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case '\\' -> unescaped.append('\\');
                case '"' -> unescaped.append('"');
                case 'n' -> unescaped.append('\n');
                case 'r' -> unescaped.append('\r');
                case 't' -> unescaped.append('\t');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        String hex = value.substring(i + 1, i + 5);
                        try {
                            unescaped.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            unescaped.append("\\u").append(hex);
                            i += 4;
                        }
                    } else {
                        unescaped.append("\\u");
                    }
                }
                default -> unescaped.append(next);
            }
        }
        return unescaped.toString();
    }

    private static class ScoreInfo {
        private final int scoreRank;
        private final double scoreRating;
        private final double average;

        private ScoreInfo(int scoreRank, double scoreRating, double average) {
            this.scoreRank = scoreRank;
            this.scoreRating = scoreRating;
            this.average = average;
        }
    }

    private static class RatingEntry {
        private final String name;
        private final double rating;
        private int ratingRank;
        private final int scoreRank;
        private final double scoreRating;
        private final double lastAverage;

        private RatingEntry(String name,
                            double rating,
                            int ratingRank,
                            int scoreRank,
                            double scoreRating,
                            double lastAverage) {
            this.name = name;
            this.rating = rating;
            this.ratingRank = ratingRank;
            this.scoreRank = scoreRank;
            this.scoreRating = scoreRating;
            this.lastAverage = lastAverage;
        }
    }
}
