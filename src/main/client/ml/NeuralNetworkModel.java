package client.ml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * Simple feedforward neural network with one hidden layer.
 */
public class NeuralNetworkModel {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final int inputSize;
    private final int hiddenSize;
    private final double[][] weights1;
    private final double[] bias1;
    private final double[] weights2;
    private double bias2;
    /**
     * Creates a neural network model.
     */
    private NeuralNetworkModel(int inputSize, int hiddenSize) {
        if (inputSize <= 0) {
            throw new IllegalArgumentException("inputSize must be positive: " + inputSize);
        }
        if (hiddenSize <= 0) {
            throw new IllegalArgumentException("hiddenSize must be positive: " + hiddenSize);
        }
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.weights1 = new double[hiddenSize][inputSize];
        this.bias1 = new double[hiddenSize];
        this.weights2 = new double[hiddenSize];
        this.bias2 = 0.0;
    }

    /**
     * Creates a randomly initialized network.
     */
    public static NeuralNetworkModel initialize(int inputSize, int hiddenSize, Random random) {
        Objects.requireNonNull(random, "random");
        NeuralNetworkModel model = new NeuralNetworkModel(inputSize, hiddenSize);
        double scale = 1.0 / Math.sqrt(inputSize);
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                model.weights1[i][j] = (random.nextDouble() * 2.0 - 1.0) * scale;
            }
            model.bias1[i] = 0.0;
            model.weights2[i] = (random.nextDouble() * 2.0 - 1.0) * scale;
        }
        model.bias2 = 0.0;
        return model;
    }

    /**
     * Runs a forward pass and returns the continue probability.
     */
    public double predict(double[] input) {
        Objects.requireNonNull(input, "input");
        if (input.length != inputSize) {
            throw new IllegalArgumentException("Expected " + inputSize + " inputs but got " + input.length);
        }
        double[] hidden = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = bias1[i];
            for (int j = 0; j < inputSize; j++) {
                sum += weights1[i][j] * input[j];
            }
            hidden[i] = relu(sum);
        }
        double output = bias2;
        for (int i = 0; i < hiddenSize; i++) {
            output += weights2[i] * hidden[i];
        }
        return sigmoid(output);
    }

    /**
     * Trains the network using mini-batch SGD.
     */
    public void train(List<TrainingSample> samples,
                      int epochs,
                      int batchSize,
                      double learningRate,
                      Random random) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(random, "random");
        if (epochs <= 0) {
            throw new IllegalArgumentException("epochs must be positive: " + epochs);
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive: " + batchSize);
        }
        if (!Double.isFinite(learningRate) || learningRate <= 0.0) {
            throw new IllegalArgumentException("learningRate must be positive: " + learningRate);
        }
        if (samples.isEmpty()) {
            return;
        }
        List<TrainingSample> shuffled = new ArrayList<>(samples);

        for (int epoch = 0; epoch < epochs; epoch++) {
            java.util.Collections.shuffle(shuffled, random);
            double[][] gradW1 = new double[hiddenSize][inputSize];
            double[] gradB1 = new double[hiddenSize];
            double[] gradW2 = new double[hiddenSize];
            double gradB2 = 0.0;
            int batchCount = 0;
            double totalLoss = 0.0;

            for (TrainingSample sample : shuffled) {
                double[] input = sample.features();
                double label = sample.label();

                double[] z1 = new double[hiddenSize];
                double[] a1 = new double[hiddenSize];
                for (int i = 0; i < hiddenSize; i++) {
                    double sum = bias1[i];
                    for (int j = 0; j < inputSize; j++) {
                        sum += weights1[i][j] * input[j];
                    }
                    z1[i] = sum;
                    a1[i] = relu(sum);
                }

                double z2 = bias2;
                for (int i = 0; i < hiddenSize; i++) {
                    z2 += weights2[i] * a1[i];
                }
                double output = sigmoid(z2);
                totalLoss += crossEntropy(output, label);

                double dZ2 = output - label;
                for (int i = 0; i < hiddenSize; i++) {
                    gradW2[i] += dZ2 * a1[i];
                }
                gradB2 += dZ2;

                for (int i = 0; i < hiddenSize; i++) {
                    double dA1 = dZ2 * weights2[i];
                    double dZ1 = dA1 * reluDerivative(z1[i]);
                    gradB1[i] += dZ1;
                    for (int j = 0; j < inputSize; j++) {
                        gradW1[i][j] += dZ1 * input[j];
                    }
                }

                batchCount++;
                if (batchCount >= batchSize) {
                    applyGradients(gradW1, gradB1, gradW2, gradB2, learningRate, batchCount);
                    batchCount = 0;
                    gradB2 = 0.0;
                    reset(gradW1, gradB1, gradW2);
                }
            }

            if (batchCount > 0) {
                applyGradients(gradW1, gradB1, gradW2, gradB2, learningRate, batchCount);
            }

            double avgLoss = totalLoss / shuffled.size();
            System.out.printf(Locale.US, "Epoch %d/%d - loss %.6f%n", epoch + 1, epochs, avgLoss);
        }
    }

    /**
     * Saves the model weights to a JSON file.
     */
    public void save(Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        ModelData data = new ModelData(inputSize, hiddenSize, weights1, bias1, weights2, bias2);
        Files.writeString(path, GSON.toJson(data) + "\n", StandardCharsets.UTF_8);
    }

    /**
     * Loads a model from a JSON file.
     */
    public static NeuralNetworkModel load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        ModelData data;
        try {
            data = GSON.fromJson(json, ModelData.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid model JSON: " + e.getMessage(), e);
        }
        validateModelData(data);

        NeuralNetworkModel model = new NeuralNetworkModel(data.inputSize, data.hiddenSize);
        for (int i = 0; i < data.hiddenSize; i++) {
            System.arraycopy(data.weights1[i], 0, model.weights1[i], 0, data.inputSize);
            model.bias1[i] = data.bias1[i];
            model.weights2[i] = data.weights2[i];
        }
        model.bias2 = data.bias2;
        return model;
    }

    /**
     * Validates model shape before copying arrays into the runtime model.
     */
    private static void validateModelData(ModelData data) {
        if (data == null) {
            throw new IllegalArgumentException("Missing model data");
        }
        if (data.inputSize <= 0 || data.hiddenSize <= 0) {
            throw new IllegalArgumentException("Model dimensions must be positive");
        }
        if (data.weights1 == null || data.weights1.length != data.hiddenSize) {
            throw new IllegalArgumentException("weights1 row count does not match hiddenSize");
        }
        for (double[] row : data.weights1) {
            if (row == null || row.length != data.inputSize) {
                throw new IllegalArgumentException("weights1 column count does not match inputSize");
            }
        }
        if (data.bias1 == null || data.bias1.length != data.hiddenSize) {
            throw new IllegalArgumentException("bias1 length does not match hiddenSize");
        }
        if (data.weights2 == null || data.weights2.length != data.hiddenSize) {
            throw new IllegalArgumentException("weights2 length does not match hiddenSize");
        }
    }
    /**
     * Handles reset.
     */
    private void reset(double[][] gradW1, double[] gradB1, double[] gradW2) {
        for (int i = 0; i < gradW1.length; i++) {
            for (int j = 0; j < gradW1[i].length; j++) {
                gradW1[i][j] = 0.0;
            }
            gradB1[i] = 0.0;
            gradW2[i] = 0.0;
        }
    }
    /**
     * Applies gradients.
     */
    private void applyGradients(double[][] gradW1,
                                double[] gradB1,
                                double[] gradW2,
                                double gradB2,
                                double learningRate,
                                int batchCount) {
        double scale = learningRate / batchCount;
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                weights1[i][j] -= gradW1[i][j] * scale;
            }
            bias1[i] -= gradB1[i] * scale;
            weights2[i] -= gradW2[i] * scale;
        }
        bias2 -= gradB2 * scale;
    }
    /**
     * Handles relu.
     */
    private static double relu(double value) {
        return Math.max(0.0, value);
    }
    /**
     * Handles relu derivative.
     */
    private static double reluDerivative(double value) {
        return value > 0.0 ? 1.0 : 0.0;
    }
    /**
     * Handles sigmoid.
     */
    private static double sigmoid(double value) {
        return 1.0 / (1.0 + Math.exp(-value));
    }
    /**
     * Handles cross entropy.
     */
    private static double crossEntropy(double prediction, double label) {
        double epsilon = 1e-9;
        double p = Math.min(1.0 - epsilon, Math.max(epsilon, prediction));
        return -(label * Math.log(p) + (1.0 - label) * Math.log(1.0 - p));
    }

    private record ModelData(int inputSize,
                             int hiddenSize,
                             double[][] weights1,
                             double[] bias1,
                             double[] weights2,
                             double bias2) {
    }
}
