package client.ml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple feedforward neural network with one hidden layer.
 */
public class NeuralNetworkModel {
    private static final String NUMBER_FORMAT = "%.6f";

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
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"inputSize\": ").append(inputSize).append(",\n");
        builder.append("  \"hiddenSize\": ").append(hiddenSize).append(",\n");
        builder.append("  \"weights1\": [\n");
        for (int i = 0; i < hiddenSize; i++) {
            builder.append("    [");
            for (int j = 0; j < inputSize; j++) {
                builder.append(format(weights1[i][j]));
                if (j < inputSize - 1) {
                    builder.append(", ");
                }
            }
            builder.append("]");
            if (i < hiddenSize - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("  ],\n");
        builder.append("  \"bias1\": [");
        for (int i = 0; i < hiddenSize; i++) {
            builder.append(format(bias1[i]));
            if (i < hiddenSize - 1) {
                builder.append(", ");
            }
        }
        builder.append("],\n");
        builder.append("  \"weights2\": [");
        for (int i = 0; i < hiddenSize; i++) {
            builder.append(format(weights2[i]));
            if (i < hiddenSize - 1) {
                builder.append(", ");
            }
        }
        builder.append("],\n");
        builder.append("  \"bias2\": ").append(format(bias2)).append("\n");
        builder.append("}\n");

        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Loads a model from a JSON file.
     */
    public static NeuralNetworkModel load(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        int inputSize = (int) extractNumber(json, "inputSize");
        int hiddenSize = (int) extractNumber(json, "hiddenSize");
        NeuralNetworkModel model = new NeuralNetworkModel(inputSize, hiddenSize);
        double[][] weights1 = extractMatrix(json, "weights1", hiddenSize, inputSize);
        double[] bias1 = extractArray(json, "bias1", hiddenSize);
        double[] weights2 = extractArray(json, "weights2", hiddenSize);
        double bias2 = extractNumber(json, "bias2");

        for (int i = 0; i < hiddenSize; i++) {
            System.arraycopy(weights1[i], 0, model.weights1[i], 0, inputSize);
            model.bias1[i] = bias1[i];
            model.weights2[i] = weights2[i];
        }
        model.bias2 = bias2;
        return model;
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
     * Handles extract number.
     */
    private static double extractNumber(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([-0-9.eE]+)")
                .matcher(json);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return Double.parseDouble(matcher.group(1));
    }
    /**
     * Handles extract array.
     */
    private static double[] extractArray(String json, String field, int expected) {
        String content = extractArrayContent(json, field);
        List<Double> values = extractNumbers(content);
        if (values.size() != expected) {
            throw new IllegalArgumentException("Expected " + expected + " values for " + field
                    + " but found " + values.size());
        }
        double[] result = new double[expected];
        for (int i = 0; i < expected; i++) {
            result[i] = values.get(i);
        }
        return result;
    }
    /**
     * Handles extract matrix.
     */
    private static double[][] extractMatrix(String json, String field, int rows, int cols) {
        String content = extractArrayContent(json, field);
        List<Double> values = extractNumbers(content);
        if (values.size() != rows * cols) {
            throw new IllegalArgumentException("Expected " + (rows * cols) + " values for " + field
                    + " but found " + values.size());
        }
        double[][] result = new double[rows][cols];
        int index = 0;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result[i][j] = values.get(index++);
            }
        }
        return result;
    }
    /**
     * Handles extract array content.
     */
    private static String extractArrayContent(String json, String field) {
        int fieldIndex = json.indexOf("\"" + field + "\"");
        if (fieldIndex < 0) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        int start = json.indexOf('[', fieldIndex);
        if (start < 0) {
            throw new IllegalArgumentException("Missing array start for field: " + field);
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unterminated array for field: " + field);
    }
    /**
     * Handles extract numbers.
     */
    private static List<Double> extractNumbers(String input) {
        List<Double> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("[-0-9.eE]+").matcher(input);
        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group()));
        }
        return values;
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
    /**
     * Handles format.
     */
    private static String format(double value) {
        return String.format(Locale.US, NUMBER_FORMAT, value);
    }
}
