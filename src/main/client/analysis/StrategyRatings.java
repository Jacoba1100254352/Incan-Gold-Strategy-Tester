package client.analysis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maintains a shared ratings file for strategy performance.
 */
public final class StrategyRatings {
    private static final Path DEFAULT_RATINGS_PATH = Paths.get("results", "strategy-ratings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final double MAX_RATING = 5.0;
    private static final double MIN_RATING = 0.0;
    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double INTERACTION_AVERAGE_WEIGHT = 0.5;
    private static final double INTERACTION_WIN_RATE_WEIGHT = 0.7;
    private static final double WIN_RATE_SCORE_WEIGHT = 0.6;
    private static final double MAX_WIN_RATE = 100.0;
    private static final double MIN_WIN_RATE = 0.0;
    private static final double TIE_EPSILON = 1e-9;
    private static Path ratingsPath = DEFAULT_RATINGS_PATH;

    private StrategyRatings() {
    }

    /**
     * Represents per-run strategy performance for rating updates.
     */
    public record StrategyPerformance(String name, double average, int wins, int runs) {
    }

    /**
     * Returns the active ratings file path.
     */
    public static Path getRatingsPath() {
        return ratingsPath;
    }

    /**
     * Overrides the ratings file path, primarily for tests and isolated runs.
     */
    public static void setRatingsPath(Path path) {
        ratingsPath = Objects.requireNonNull(path, "path");
    }

    /**
     * Updates the ratings JSON based on the provided averages.
     */
    public static void updateRatings(List<StrategyPerformance> performances, String sourceLabel) {
        updateRatings(performances, sourceLabel, null, false);
    }

    /**
     * Updates the ratings JSON based on sweep results and optional interaction data.
     */
    public static void updateRatings(List<StrategyPerformance> performances,
                                     String sourceLabel,
                                     Map<String, InteractionPerformance> interactionPerformances,
                                     boolean includeInteractions) {
        updateRatings(performances, sourceLabel, interactionPerformances, includeInteractions, true);
    }

    /**
     * Writes ratings with optional blending of the existing ratings file.
     *
     * @param blendHistory whether to blend this run with previously saved ratings
     */
    public static void updateRatings(List<StrategyPerformance> performances,
                                     String sourceLabel,
                                     Map<String, InteractionPerformance> interactionPerformances,
                                     boolean includeInteractions,
                                     boolean blendHistory) {
        if (performances == null || performances.isEmpty()) {
            return;
        }

        validatePerformances(performances);
        Map<String, ExistingEntry> previous = blendHistory ? loadRatings() : Map.of();
        List<EffectivePerformance> effectivePerformances = buildEffectivePerformances(
                performances,
                interactionPerformances,
                includeInteractions);
        applyAverageRatings(effectivePerformances);
        applyWinRateRatings(effectivePerformances);
        applyScoreRatings(effectivePerformances);

        Map<String, ScoreInfo> scoreInfo = buildScoreInfoMap(effectivePerformances);

        List<RatingEntry> entries = new ArrayList<>();
        for (EffectivePerformance effective : effectivePerformances) {
            StrategyPerformance performance = effective.performance;
            ScoreInfo info = scoreInfo.get(performance.name);
            ExistingEntry existing = previous.get(performance.name);
            double previousRating = existing == null ? info.scoreRating : existing.rating;
            double previousWinRate = existing == null ? info.winRate : existing.winRate;
            double previousSweepWinRate = existing == null ? info.sweepWinRate : existing.sweepWinRate;
            double previousInteractionWinRate = existing == null ? info.interactionWinRate : existing.interactionWinRate;
            double updatedRating = blendRating(previousRating, info.scoreRating);
            double updatedWinRate = blendWinRate(previousWinRate, info.winRate);
            double updatedSweepWinRate = blendWinRate(previousSweepWinRate, info.sweepWinRate);
            double updatedInteractionWinRate = blendWinRate(previousInteractionWinRate, info.interactionWinRate);
            entries.add(new RatingEntry(
                    performance.name,
                    updatedRating,
                    0,
                    info.scoreRank,
                    info.scoreRating,
                    info.average,
                    updatedWinRate,
                    info.winRate,
                    updatedSweepWinRate,
                    info.sweepWinRate,
                    updatedInteractionWinRate,
                    info.interactionWinRate,
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

        assignRatingRanks(entries);
        writeRatings(entries, sourceLabel, blendHistory);
    }
    /**
     * Applies average ratings.
     */
    private static void applyAverageRatings(List<EffectivePerformance> performances) {
        List<EffectivePerformance> sorted = new ArrayList<>(performances);
        sorted.sort((left, right) -> Double.compare(right.effectiveAverage, left.effectiveAverage));
        int total = sorted.size();
        int rank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && !approximatelyEqual(
                    sorted.get(i).effectiveAverage,
                    sorted.get(i - 1).effectiveAverage)) {
                rank = i + 1;
            }
            sorted.get(i).averageRating = ratingFromRank(rank, total);
        }
    }
    /**
     * Applies win rate ratings.
     */
    private static void applyWinRateRatings(List<EffectivePerformance> performances) {
        List<EffectivePerformance> sorted = new ArrayList<>(performances);
        sorted.sort((left, right) -> Double.compare(right.effectiveWinRate, left.effectiveWinRate));
        int total = sorted.size();
        int rank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && !approximatelyEqual(
                    sorted.get(i).effectiveWinRate,
                    sorted.get(i - 1).effectiveWinRate)) {
                rank = i + 1;
            }
            sorted.get(i).winRateRating = ratingFromRank(rank, total);
        }
    }
    /**
     * Applies score ratings.
     */
    private static void applyScoreRatings(List<EffectivePerformance> performances) {
        for (EffectivePerformance performance : performances) {
            performance.scoreRating = mixMetric(performance.averageRating,
                    performance.winRateRating,
                    WIN_RATE_SCORE_WEIGHT);
        }
        List<EffectivePerformance> sorted = new ArrayList<>(performances);
        sorted.sort((left, right) -> {
            int comparison = Double.compare(right.scoreRating, left.scoreRating);
            if (comparison != 0) {
                return comparison;
            }
            return left.performance.name.compareToIgnoreCase(right.performance.name);
        });
        int rank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && !approximatelyEqual(
                    sorted.get(i).scoreRating,
                    sorted.get(i - 1).scoreRating)) {
                rank = i + 1;
            }
            sorted.get(i).scoreRank = rank;
        }
    }
    /**
     * Builds score info map.
     */
    private static Map<String, ScoreInfo> buildScoreInfoMap(List<EffectivePerformance> sorted) {
        Map<String, ScoreInfo> scoreInfo = new HashMap<>();
        for (EffectivePerformance performance : sorted) {
            scoreInfo.put(performance.performance.name,
                    new ScoreInfo(performance.scoreRank,
                            performance.scoreRating,
                            performance.effectiveAverage,
                            performance.effectiveWinRate,
                            performance.sweepWinRate,
                            performance.interactionWinRate));
        }
        return scoreInfo;
    }
    /**
     * Builds effective performances.
     */
    private static List<EffectivePerformance> buildEffectivePerformances(
            List<StrategyPerformance> performances,
            Map<String, InteractionPerformance> interactionPerformances,
            boolean includeInteractions) {
        List<EffectivePerformance> effective = new ArrayList<>(performances.size());
        for (StrategyPerformance performance : performances) {
            double sweepAverage = performance.average;
            double sweepWinRate = toWinRate(performance.wins, performance.runs);
            InteractionPerformance interaction = interactionPerformances == null
                    ? null
                    : interactionPerformances.get(performance.name);
            double interactionAverage = interaction == null ? Double.NaN : interaction.average;
            double interactionWinRate = interaction == null ? Double.NaN : clampWinRate(interaction.winRate);
            double effectiveAverage = sweepAverage;
            double effectiveWinRate = sweepWinRate;
            if (includeInteractions && interaction != null) {
                effectiveAverage = mixMetric(sweepAverage, interactionAverage, INTERACTION_AVERAGE_WEIGHT);
                effectiveWinRate = clampWinRate(mixMetric(sweepWinRate, interactionWinRate, INTERACTION_WIN_RATE_WEIGHT));
            }
            effective.add(new EffectivePerformance(performance,
                    sweepAverage,
                    interactionAverage,
                    effectiveAverage,
                    sweepWinRate,
                    interactionWinRate,
                    effectiveWinRate));
        }
        return effective;
    }
    /**
     * Loads ratings.
     */
    private static Map<String, ExistingEntry> loadRatings() {
        Map<String, ExistingEntry> ratings = new HashMap<>();
        if (!Files.exists(ratingsPath)) {
            return ratings;
        }
        try {
            String content = Files.readString(ratingsPath, StandardCharsets.UTF_8);
            JsonElement rootElement = JsonParser.parseString(content);
            if (!rootElement.isJsonObject()) {
                return ratings;
            }
            JsonArray strategies = rootElement.getAsJsonObject().getAsJsonArray("strategies");
            if (strategies == null) {
                return ratings;
            }
            for (JsonElement element : strategies) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                String name = getString(entry, "name", null);
                double rating = getDouble(entry, "rating", Double.NaN);
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (Double.isNaN(rating)) {
                    continue;
                }
                double winRate = getDouble(entry, "winRate", Double.NaN);
                if (Double.isNaN(winRate)) {
                    winRate = getDouble(entry, "lastWinRate", Double.NaN);
                }
                double sweepWinRate = getDouble(entry, "sweepWinRate", Double.NaN);
                if (Double.isNaN(sweepWinRate)) {
                    sweepWinRate = getDouble(entry, "lastSweepWinRate", Double.NaN);
                }
                double interactionWinRate = getDouble(entry, "interactionWinRate", Double.NaN);
                if (Double.isNaN(interactionWinRate)) {
                    interactionWinRate = getDouble(entry, "lastInteractionWinRate", Double.NaN);
                }
                double clampedRating = clampRating(rating);
                double clampedWinRate = Double.isNaN(winRate) ? Double.NaN : clampWinRate(winRate);
                double clampedSweepWinRate = Double.isNaN(sweepWinRate) ? Double.NaN : clampWinRate(sweepWinRate);
                double clampedInteractionWinRate = Double.isNaN(interactionWinRate)
                        ? Double.NaN
                        : clampWinRate(interactionWinRate);
                ratings.put(name, new ExistingEntry(clampedRating, clampedWinRate,
                        clampedSweepWinRate, clampedInteractionWinRate));
            }
        } catch (IOException | ClassCastException | IllegalStateException | JsonParseException e) {
            System.err.println("Failed to read strategy ratings: " + e.getMessage());
        }
        return ratings;
    }
    /**
     * Writes ratings.
     */
    private static void writeRatings(List<RatingEntry> entries,
                                     String sourceLabel,
                                     boolean historyBlended) {
        try {
            Path parent = ratingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            RatingsDocument document = new RatingsDocument(
                    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    historyBlended ? DEFAULT_WEIGHT : 1.0,
                    historyBlended,
                    sourceLabel == null || sourceLabel.isBlank() ? null : sourceLabel,
                    entries);
            Files.writeString(ratingsPath, GSON.toJson(document) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Failed to write strategy ratings: " + e.getMessage());
        }
    }
    /**
     * Handles blend rating.
     */
    private static double blendRating(double previous, double current) {
        double blended = previous * (1.0 - DEFAULT_WEIGHT) + current * DEFAULT_WEIGHT;
        return clampRating(blended);
    }
    /**
     * Handles blend win rate.
     */
    private static double blendWinRate(double previous, double current) {
        if (Double.isNaN(current)) {
            if (Double.isNaN(previous)) {
                return 0.0;
            }
            return clampWinRate(previous);
        }
        if (Double.isNaN(previous)) {
            return clampWinRate(current);
        }
        double blended = previous * (1.0 - DEFAULT_WEIGHT) + current * DEFAULT_WEIGHT;
        return clampWinRate(blended);
    }
    /**
     * Handles mix metric.
     */
    private static double mixMetric(double primary, double secondary, double weight) {
        return primary * (1.0 - weight) + secondary * weight;
    }
    /**
     * Handles rating from rank.
     */
    private static double ratingFromRank(int rank, int total) {
        if (total <= 1) {
            return MAX_RATING;
        }
        double step = MAX_RATING / (total - 1);
        double rating = MAX_RATING - (rank - 1) * step;
        return clampRating(rating);
    }
    /**
     * Handles clamp rating.
     */
    private static double clampRating(double rating) {
        if (rating < MIN_RATING) {
            return MIN_RATING;
        }
        return Math.min(rating, MAX_RATING);
    }
    /**
     * Handles clamp win rate.
     */
    private static double clampWinRate(double winRate) {
        if (winRate < MIN_WIN_RATE) {
            return MIN_WIN_RATE;
        }
	    return Math.min(winRate, MAX_WIN_RATE);
    }
    /**
     * Handles to win rate.
     */
    private static double toWinRate(int wins, int runs) {
        if (runs <= 0) {
            return 0.0;
        }
        return (wins * 100.0) / runs;
    }

    private static void validatePerformances(List<StrategyPerformance> performances) {
        Set<String> names = new HashSet<>();
        for (StrategyPerformance performance : performances) {
            Objects.requireNonNull(performance, "performances cannot contain null");
            if (performance.name == null || performance.name.isBlank()) {
                throw new IllegalArgumentException("Strategy names cannot be blank");
            }
            if (!Double.isFinite(performance.average)) {
                throw new IllegalArgumentException("Strategy average must be finite: " + performance.name);
            }
            if (performance.runs < 0 || performance.wins < 0 || performance.wins > performance.runs) {
                throw new IllegalArgumentException("Invalid win/run counts for " + performance.name);
            }
            if (!names.add(performance.name)) {
                throw new IllegalArgumentException("Duplicate strategy name: " + performance.name);
            }
        }
    }

    private static void assignRatingRanks(List<RatingEntry> entries) {
        int rank = 1;
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0 && !approximatelyEqual(
                    entries.get(index).rating,
                    entries.get(index - 1).rating)) {
                rank = index + 1;
            }
            entries.get(index).ratingRank = rank;
        }
    }

    private static boolean approximatelyEqual(double left, double right) {
        return Math.abs(left - right) <= TIE_EPSILON;
    }

    /**
     * Reads a string from a JSON object with a fallback.
     */
    private static String getString(JsonObject object, String field, String fallback) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (ClassCastException | IllegalStateException e) {
            return fallback;
        }
    }

    /**
     * Reads a double from a JSON object with a fallback.
     */
    private static double getDouble(JsonObject object, String field, double fallback) {
        JsonElement element = object.get(field);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (ClassCastException | IllegalStateException | NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Normalizes non-finite values before JSON serialization.
     */
    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
    
    private record ScoreInfo(int scoreRank,
                             double scoreRating,
                             double average,
                             double winRate,
                             double sweepWinRate,
                             double interactionWinRate)
    {
    }
    
    
    private record ExistingEntry(double rating,
                                 double winRate,
                                 double sweepWinRate,
                                 double interactionWinRate)
    {
    }

    /**
     * Interaction data for combining sweep results with matchup performance.
     */
    public record InteractionPerformance(String name, double average, double winRate) {
    }

    private record RatingsDocument(String updatedAt,
                                   double ratingWeight,
                                   boolean historyBlended,
                                   String source,
                                   List<RatingEntry> strategies) {
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
        private final double sweepWinRate;
        private final double lastSweepWinRate;
        private final double interactionWinRate;
        private final double lastInteractionWinRate;
        private final int wins;
        private final int runs;
        /**
         * Creates a rating entry.
         */
        private RatingEntry(String name,
                            double rating,
                            int ratingRank,
                            int scoreRank,
                            double scoreRating,
                            double lastAverage,
                            double winRate,
                            double lastWinRate,
                            double sweepWinRate,
                            double lastSweepWinRate,
                            double interactionWinRate,
                            double lastInteractionWinRate,
                            int wins,
                            int runs) {
            this.name = name;
            this.rating = finiteOrZero(rating);
            this.ratingRank = ratingRank;
            this.scoreRank = scoreRank;
            this.scoreRating = finiteOrZero(scoreRating);
            this.lastAverage = finiteOrZero(lastAverage);
            this.winRate = finiteOrZero(winRate);
            this.lastWinRate = finiteOrZero(lastWinRate);
            this.sweepWinRate = finiteOrZero(sweepWinRate);
            this.lastSweepWinRate = finiteOrZero(lastSweepWinRate);
            this.interactionWinRate = finiteOrZero(interactionWinRate);
            this.lastInteractionWinRate = finiteOrZero(lastInteractionWinRate);
            this.wins = wins;
            this.runs = runs;
        }
    }

    private static class EffectivePerformance {
        private final StrategyPerformance performance;
        private final double sweepAverage;
        private final double interactionAverage;
        private final double effectiveAverage;
        private final double sweepWinRate;
        private final double interactionWinRate;
        private final double effectiveWinRate;
        private double averageRating;
        private double winRateRating;
        private double scoreRating;
        private int scoreRank;
        private EffectivePerformance(StrategyPerformance performance,
                                     double sweepAverage,
                                     double interactionAverage,
                                     double effectiveAverage,
                                     double sweepWinRate,
                                     double interactionWinRate,
                                     double effectiveWinRate) {
            this.performance = performance;
            this.sweepAverage = sweepAverage;
            this.interactionAverage = interactionAverage;
            this.effectiveAverage = effectiveAverage;
            this.sweepWinRate = sweepWinRate;
            this.interactionWinRate = interactionWinRate;
            this.effectiveWinRate = effectiveWinRate;
        }
    }
}
