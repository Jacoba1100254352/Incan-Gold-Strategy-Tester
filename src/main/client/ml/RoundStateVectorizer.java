package client.ml;

import model.Hazard;
import model.RoundState;

/**
 * Converts round states into normalized feature vectors for ML models.
 */
public class RoundStateVectorizer {
    private static final int MAX_TURN = 31;
    private static final int MAX_PLAYERS = 8;
    private static final int MAX_TREASURE = 50;
    private static final int MAX_ARTIFACTS = 5;
    private static final int MAX_HAZARD_COUNT = 2;
    private static final int MAX_HAZARD_COPIES = 3;
    private static final int MAX_TOTAL_HAZARDS = Hazard.values().length;
    /**
     * Creates a round state vectorizer.
     */
    private RoundStateVectorizer() {
    }

    /**
     * Returns the number of features emitted by the vectorizer.
     */
    public static int featureCount() {
        return 7 + Hazard.values().length * 2;
    }

    /**
     * Builds a normalized feature vector for the given state.
     */
    public static double[] toFeatures(RoundState state) {
        double[] features = new double[featureCount()];
        int index = 0;
        features[index++] = normalize(state.getTurnNumber(), MAX_TURN);
        features[index++] = normalize(state.getActivePlayers(), MAX_PLAYERS);
        features[index++] = normalize(state.getTempleTreasure(), MAX_TREASURE);
        features[index++] = normalize(state.getRoundTreasure(), MAX_TREASURE);
        features[index++] = normalize(state.getArtifactsOnPath(), MAX_ARTIFACTS);
        features[index++] = normalize(state.getArtifactsClaimed(), MAX_ARTIFACTS);
        features[index++] = normalize(state.getTotalHazardsRevealed(), MAX_TOTAL_HAZARDS);
        for (Hazard hazard : Hazard.values()) {
            features[index++] = normalize(state.getHazardCount(hazard), MAX_HAZARD_COUNT);
        }
        for (Hazard hazard : Hazard.values()) {
            features[index++] = normalize(state.getHazardCopiesRemaining(hazard), MAX_HAZARD_COPIES);
        }
        return features;
    }
    /**
     * Handles normalize.
     */
    private static double normalize(int value, int max) {
        if (max <= 0) {
            return 0.0;
        }
        double clamped = Math.max(0, Math.min(value, max));
        return clamped / max;
    }
}
