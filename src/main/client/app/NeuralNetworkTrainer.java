package client.app;

import algorithm.Strategy;
import client.ai.AIDifficulty;
import client.ai.StrategyAdvisor;
import client.analysis.StrategyCatalog;
import client.ml.NeuralNetworkModel;
import client.ml.RoundStateVectorizer;
import client.ml.TrainingSample;
import model.Game;
import model.Player;
import model.RoundState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates training data from strategy advisors and trains a neural strategy model.
 */
public class NeuralNetworkTrainer {
    // Default number of self-play games to generate training data.
    private static final int DEFAULT_GAMES = 2000;
    // Default number of players per simulated training game.
    private static final int DEFAULT_PLAYERS = 4;
    // Default maximum number of labeled samples to collect.
    private static final int DEFAULT_SAMPLE_TARGET = 100000;
    // Default number of training epochs.
    private static final int DEFAULT_EPOCHS = 12;
    // Default mini-batch size for SGD.
    private static final int DEFAULT_BATCH_SIZE = 128;
    // Default hidden layer size for the network.
    private static final int DEFAULT_HIDDEN_SIZE = 32;
    // Default learning rate for gradient updates.
    private static final double DEFAULT_LEARNING_RATE = 0.01;
    // Default probability of following the advisor's decision during training.
    private static final double DEFAULT_ADVISOR_FOLLOW_RATE = 0.4;
    // Default output path for the trained model.
    private static final String DEFAULT_MODEL_PATH = "results/models/strategy-net.json";

    /**
     * Entry point for training a neural network strategy.
     *
     * @param args optional args: [games] [players] [samples] [epochs] [batch] [hidden] [lr] [followRate] [difficulty] [outputPath]
     */
    public static void main(String[] args) {
        int games = args.length > 0 ? parsePositiveInt(args[0], DEFAULT_GAMES) : DEFAULT_GAMES;
        int players = args.length > 1 ? parsePositiveInt(args[1], DEFAULT_PLAYERS) : DEFAULT_PLAYERS;
        int sampleTarget = args.length > 2 ? parsePositiveInt(args[2], DEFAULT_SAMPLE_TARGET) : DEFAULT_SAMPLE_TARGET;
        int epochs = args.length > 3 ? parsePositiveInt(args[3], DEFAULT_EPOCHS) : DEFAULT_EPOCHS;
        int batchSize = args.length > 4 ? parsePositiveInt(args[4], DEFAULT_BATCH_SIZE) : DEFAULT_BATCH_SIZE;
        int hiddenSize = args.length > 5 ? parsePositiveInt(args[5], DEFAULT_HIDDEN_SIZE) : DEFAULT_HIDDEN_SIZE;
        double learningRate = args.length > 6 ? parsePositiveDouble(args[6], DEFAULT_LEARNING_RATE) : DEFAULT_LEARNING_RATE;
        double followRate = args.length > 7 ? parsePositiveDouble(args[7], DEFAULT_ADVISOR_FOLLOW_RATE)
                : DEFAULT_ADVISOR_FOLLOW_RATE;
        AIDifficulty difficulty = args.length > 8 ? AIDifficulty.fromInput(args[8]) : AIDifficulty.HARD;
        Path outputPath = args.length > 9 ? Path.of(args[9]) : Path.of(DEFAULT_MODEL_PATH);

        System.out.printf("Training samples: target=%d games=%d players=%d%n", sampleTarget, games, players);
        System.out.printf("Trainer: epochs=%d batch=%d hidden=%d lr=%.4f followRate=%.2f%n",
                epochs, batchSize, hiddenSize, learningRate, followRate);
        System.out.printf("Advisor difficulty: %s%n", difficulty);

        List<StrategyCatalog.StrategySpec> strategies = StrategyCatalog.buildDefaultStrategies();
        StrategyAdvisor advisor = StrategyAdvisor.buildDefault(difficulty, players);
        List<TrainingSample> samples = collectSamples(strategies, advisor, games, players, sampleTarget,
                followRate, new Random());
        System.out.printf("Collected %d samples%n", samples.size());

        NeuralNetworkModel model = NeuralNetworkModel.initialize(RoundStateVectorizer.featureCount(),
                hiddenSize, new Random());
        model.train(samples, epochs, batchSize, learningRate, new Random());

        try {
            model.save(outputPath);
            System.out.printf("Saved model to %s%n", outputPath.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to save model: " + e.getMessage());
        }
    }

    private static List<TrainingSample> collectSamples(List<StrategyCatalog.StrategySpec> strategies,
                                                       StrategyAdvisor advisor,
                                                       int games,
                                                       int playersPerGame,
                                                       int sampleTarget,
                                                       double followRate,
                                                       Random random) {
        List<TrainingSample> samples = new ArrayList<>(sampleTarget);
        int gameIndex = 0;
        while (gameIndex < games && samples.size() < sampleTarget) {
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < playersPerGame; i++) {
                Strategy baseStrategy = strategies.get(random.nextInt(strategies.size())).factory.get();
                Strategy trainingStrategy = new TrainingStrategy(baseStrategy, advisor, samples, sampleTarget,
                        followRate, random);
                players.add(new Player(trainingStrategy));
            }
            Game game = new Game(players);
            game.playGame();
            gameIndex++;
        }
        return samples;
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parsePositiveDouble(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0.0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static class TrainingStrategy implements Strategy {
        private final Strategy baseStrategy;
        private final StrategyAdvisor advisor;
        private final List<TrainingSample> samples;
        private final int sampleTarget;
        private final double followRate;
        private final Random random;

        private TrainingStrategy(Strategy baseStrategy,
                                 StrategyAdvisor advisor,
                                 List<TrainingSample> samples,
                                 int sampleTarget,
                                 double followRate,
                                 Random random) {
            this.baseStrategy = baseStrategy;
            this.advisor = advisor;
            this.samples = samples;
            this.sampleTarget = sampleTarget;
            this.followRate = followRate;
            this.random = random;
        }

        @Override
        public boolean shouldContinue(RoundState state) {
            StrategyAdvisor.Decision decision = advisor.decide(state);
            if (samples.size() < sampleTarget) {
                double label = decision.shouldContinue ? 1.0 : 0.0;
                samples.add(new TrainingSample(RoundStateVectorizer.toFeatures(state), label));
            }
            if (random.nextDouble() < followRate) {
                return decision.shouldContinue;
            }
            return baseStrategy.shouldContinue(state);
        }
    }
}
