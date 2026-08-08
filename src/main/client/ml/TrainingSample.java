package client.ml;

import java.util.Objects;

/**
 * Labeled training example for continue/leave prediction.
 */
public record TrainingSample(double[] features, double label) {
    public TrainingSample {
        Objects.requireNonNull(features, "features");
        if (!Double.isFinite(label) || label < 0.0 || label > 1.0) {
            throw new IllegalArgumentException("label must be in [0, 1]: " + label);
        }
        features = features.clone();
    }

    @Override
    public double[] features() {
        return features.clone();
    }
}
