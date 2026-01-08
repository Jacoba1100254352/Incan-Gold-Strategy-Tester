package client.ai;

import algorithm.Strategy;
import client.ml.NeuralNetworkModel;
import client.ml.RoundStateVectorizer;
import model.RoundState;

import java.nio.file.Path;

/**
 * Strategy that uses a trained neural network to decide whether to continue.
 */
public class NeuralNetworkStrategy implements Strategy {
    private final String name;
    private final NeuralNetworkModel model;
    private final double threshold;
    private final Strategy fallback;

    /**
     * Loads a neural network model from disk.
     *
     * @param name display name for logs
     * @param modelPath path to the saved model JSON
     * @param threshold probability threshold for continuing
     * @param fallback strategy used if the model fails to load
     */
    public NeuralNetworkStrategy(String name, Path modelPath, double threshold, Strategy fallback) {
        this.name = name;
        this.threshold = threshold;
        this.fallback = fallback;
        NeuralNetworkModel loaded = null;
        try {
            loaded = NeuralNetworkModel.load(modelPath);
        } catch (Exception e) {
            System.err.printf("Failed to load model for %s: %s%n", name, e.getMessage());
        }
        this.model = loaded;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        if (model == null) {
            return fallback != null && fallback.shouldContinue(state);
        }
        double probability = model.predict(RoundStateVectorizer.toFeatures(state));
        boolean decision = probability >= threshold;
        return decision;
    }
}
