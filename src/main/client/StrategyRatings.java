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
    private static final double MAX_WIN_RATE = 100.0;
    private static final double MIN_WIN_RATE = 0.0;
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"(.*?)\"(.*?)\\n\\s*\\}",
            Pattern.DOTALL);

    /**
     * Represents a strategy average for ranking.
     */
    public record StrategyPerformance(String name, double average, int wins, int runs) {
    }

    /**
     * Updates the ratings JSON based on the provided averages.
     */
    public static void updateRatings(List<StrategyPerformance> performances, String sourceLabel) {
        if (performances == null || performances.isEmpty()) {
            return;
        }

        Map<String, ExistingEntry> previous = loadRatings();
        List<StrategyPerformance> sorted = new ArrayList<>(performances);
        sorted.sort((left, right) -> Double.compare(right.average, left.average));

        int totalStrategies = sorted.size();
        Map<String, ScoreInfo> scoreInfo = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            StrategyPerformance performance = sorted.get(i);
            int scoreRank = i + 1;
            double scoreRating = ratingFromRank(scoreRank, totalStrategies);
            double winRate = toWinRate(performance.wins, performance.runs);
            scoreInfo.put(performance.name, new ScoreInfo(scoreRank, scoreRating, performance.average, winRate));
        }

        List<RatingEntry> entries = new ArrayList<>();
        for (StrategyPerformance performance : sorted) {
            ScoreInfo info = scoreInfo.get(performance.name);
            ExistingEntry existing = previous.get(performance.name);
            double previousRating = existing == null ? info.scoreRating : existing.rating;
            double previousWinRate = existing == null ? info.winRate : existing.winRate;
            double updatedRating = blendRating(previousRating, info.scoreRating);
            double updatedWinRate = blendWinRate(previousWinRate, info.winRate);
            entries.add(new RatingEntry(
                    performance.name,
                    updatedRating,
                    0,
                    info.scoreRank,
                    info.scoreRating,
                    info.average,
                    updatedWinRate,
                    info.winRate,
                    performance.wins,
                    performance.runs
            ));
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

    private static Map<String, ExistingEntry> loadRatings() {
        Map<String, ExistingEntry> ratings = new HashMap<>();
        if (!Files.exists(RATINGS_PATH)) {
            return ratings;
        }
        try {
            String content = Files.readString(RATINGS_PATH, StandardCharsets.UTF_8);
            Matcher matcher = ENTRY_PATTERN.matcher(content);
            while (matcher.find()) {
                String name = unescapeJson(matcher.group(1));
                String entryBody = matcher.group(2);
                double rating = parseDoubleField(entryBody, "rating", Double.NaN);
                if (Double.isNaN(rating)) {
                    continue;
                }
                double winRate = parseDoubleField(entryBody, "winRate", Double.NaN);
                if (Double.isNaN(winRate)) {
                    winRate = parseDoubleField(entryBody, "lastWinRate", Double.NaN);
                }
                double clampedRating = clampRating(rating);
                double clampedWinRate = Double.isNaN(winRate) ? Double.NaN : clampWinRate(winRate);
                ratings.put(name, new ExistingEntry(clampedRating, clampedWinRate));
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
            builder.append("      \"lastAverage\": ").append(formatNumber(entry.lastAverage)).append(",\n");
            builder.append("      \"winRate\": ").append(formatNumber(entry.winRate)).append(",\n");
            builder.append("      \"lastWinRate\": ").append(formatNumber(entry.lastWinRate)).append(",\n");
            builder.append("      \"wins\": ").append(entry.wins).append(",\n");
            builder.append("      \"runs\": ").append(entry.runs).append("\n");
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

    private static double blendWinRate(double previous, double current) {
        if (Double.isNaN(previous)) {
            return clampWinRate(current);
        }
        double blended = previous * (1.0 - DEFAULT_WEIGHT) + current * DEFAULT_WEIGHT;
        return clampWinRate(blended);
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

    private static double clampWinRate(double winRate) {
        if (winRate < MIN_WIN_RATE) {
            return MIN_WIN_RATE;
        }
        if (winRate > MAX_WIN_RATE) {
            return MAX_WIN_RATE;
        }
        return winRate;
    }

    private static double toWinRate(int wins, int runs) {
        if (runs <= 0) {
            return 0.0;
        }
        return (wins * 100.0) / runs;
    }

    private static double parseDoubleField(String entryBody, String field, double fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([0-9.]+)")
                .matcher(entryBody);
        if (!matcher.find()) {
            return fallback;
        }
        return Double.parseDouble(matcher.group(1));
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
        private final double winRate;

        private ScoreInfo(int scoreRank, double scoreRating, double average, double winRate) {
            this.scoreRank = scoreRank;
            this.scoreRating = scoreRating;
            this.average = average;
            this.winRate = winRate;
        }
    }

    private static class ExistingEntry {
        private final double rating;
        private final double winRate;

        private ExistingEntry(double rating, double winRate) {
            this.rating = rating;
            this.winRate = winRate;
        }
    }

    private static class RatingEntry {
        private final String name;
        private final double rating;
        private int ratingRank;
        private final int scoreRank;
        private final double scoreRating;
        private final double lastAverage;
        private final double winRate;
        private final double lastWinRate;
        private final int wins;
        private final int runs;

        private RatingEntry(String name,
                            double rating,
                            int ratingRank,
                            int scoreRank,
                            double scoreRating,
                            double lastAverage,
                            double winRate,
                            double lastWinRate,
                            int wins,
                            int runs) {
            this.name = name;
            this.rating = rating;
            this.ratingRank = ratingRank;
            this.scoreRank = scoreRank;
            this.scoreRating = scoreRating;
            this.lastAverage = lastAverage;
            this.winRate = winRate;
            this.lastWinRate = lastWinRate;
            this.wins = wins;
            this.runs = runs;
        }
    }
}
