package client.ml;

/**
 * Labeled training example for continue/leave prediction.
 */
public record TrainingSample(double[] features, double label) {
}
