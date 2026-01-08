package client.app;

import algorithm.Strategy;
import client.ai.AIDifficulty;
import client.ai.StrategyAdvisor;
import client.analysis.StrategyCatalog;
import client.ml.NeuralNetworkModel;
import client.ml.RoundStateVectorizer;
import client.ml.TrainingSample;
import model.Card;
import model.Game;
import model.Hazard;
import model.Player;
import model.RoundState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    // Default toggle to use Monte Carlo rollouts for labeling.
    private static final boolean DEFAULT_USE_MONTE_CARLO_LABELS = true;
    // Default number of rollouts per decision when Monte Carlo labeling is enabled.
    private static final int DEFAULT_MONTE_CARLO_ROLLOUTS = 30;
    // Default output path for the trained model.
    private static final String DEFAULT_MODEL_PATH = "results/models/strategy-net.json";
    // Default number of copies per hazard when copy info is missing.
    private static final int DEFAULT_HAZARD_COPIES = 3;
    // Total artifacts in the full game.
    private static final int TOTAL_ARTIFACTS = 5;
    // Value for the first few artifacts.
    private static final int ARTIFACT_LOW_VALUE = 5;
    // Value for later artifacts.
    private static final int ARTIFACT_HIGH_VALUE = 10;
    // Number of artifacts scored at the lower value.
    private static final int ARTIFACT_LOW_COUNT = 3;
    // Treasure values included in the deck.
    private static final List<Integer> TREASURE_VALUES = List.of(
            1, 2, 3, 4, 5,
            6, 7, 8, 9, 10,
            11, 12, 13, 14, 15
    );

    /**
     * Entry point for training a neural network strategy.
     *
     * @param args optional args: [games] [players] [samples] [epochs] [batch] [hidden] [lr] [followRate] [difficulty]
     *             [outputPath] [useMonteCarloLabels] [rollouts]
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
        boolean useMonteCarloLabels = args.length > 10
                ? Boolean.parseBoolean(args[10])
                : DEFAULT_USE_MONTE_CARLO_LABELS;
        int rollouts = args.length > 11
                ? parsePositiveInt(args[11], DEFAULT_MONTE_CARLO_ROLLOUTS)
                : DEFAULT_MONTE_CARLO_ROLLOUTS;

        System.out.printf("Training samples: target=%d games=%d players=%d%n", sampleTarget, games, players);
        System.out.printf("Trainer: epochs=%d batch=%d hidden=%d lr=%.4f followRate=%.2f%n",
                epochs, batchSize, hiddenSize, learningRate, followRate);
        System.out.printf("Advisor difficulty: %s%n", difficulty);
        System.out.printf("Monte Carlo labels: %s (rollouts=%d)%n",
                useMonteCarloLabels ? "enabled" : "disabled",
                rollouts);

        List<StrategyCatalog.StrategySpec> strategies = StrategyCatalog.buildDefaultStrategies();
        StrategyAdvisor advisor = StrategyAdvisor.buildDefault(difficulty, players);
        List<TrainingSample> samples = collectSamples(strategies, advisor, games, players, sampleTarget,
                followRate, useMonteCarloLabels, rollouts, new Random());
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
                                                       boolean useMonteCarloLabels,
                                                       int rollouts,
                                                       Random random) {
        List<TrainingSample> samples = new ArrayList<>(sampleTarget);
        int gameIndex = 0;
        while (gameIndex < games && samples.size() < sampleTarget) {
            List<Player> players = new ArrayList<>();
            for (int i = 0; i < playersPerGame; i++) {
                Strategy baseStrategy = strategies.get(random.nextInt(strategies.size())).factory.get();
                Strategy trainingStrategy = new TrainingStrategy(baseStrategy, advisor, samples, sampleTarget,
                        followRate, useMonteCarloLabels, rollouts, random);
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
        private final boolean useMonteCarloLabels;
        private final int rollouts;
        private final Random random;

        private TrainingStrategy(Strategy baseStrategy,
                                 StrategyAdvisor advisor,
                                 List<TrainingSample> samples,
                                 int sampleTarget,
                                 double followRate,
                                 boolean useMonteCarloLabels,
                                 int rollouts,
                                 Random random) {
            this.baseStrategy = baseStrategy;
            this.advisor = advisor;
            this.samples = samples;
            this.sampleTarget = sampleTarget;
            this.followRate = followRate;
            this.useMonteCarloLabels = useMonteCarloLabels;
            this.rollouts = rollouts;
            this.random = random;
        }

        @Override
        public boolean shouldContinue(RoundState state) {
            StrategyAdvisor.Decision decision = advisor.decide(state);
            if (samples.size() < sampleTarget) {
                double label = useMonteCarloLabels
                        ? computeMonteCarloLabel(state, advisor, rollouts, random)
                        : (decision.shouldContinue ? 1.0 : 0.0);
                samples.add(new TrainingSample(RoundStateVectorizer.toFeatures(state), label));
            }
            if (random.nextDouble() < followRate) {
                return decision.shouldContinue;
            }
            return baseStrategy.shouldContinue(state);
        }
    }

    private static double computeMonteCarloLabel(RoundState state,
                                                 StrategyAdvisor advisor,
                                                 int rollouts,
                                                 Random random) {
        int simulations = Math.max(1, rollouts);
        double continueValue = estimateAverageOutcome(state, advisor, true, simulations, random);
        double leaveValue = estimateAverageOutcome(state, advisor, false, simulations, random);
        if (continueValue >= leaveValue) {
            return 1.0;
        }
        return 0.0;
    }

    private static double estimateAverageOutcome(RoundState state,
                                                 StrategyAdvisor advisor,
                                                 boolean forceContinue,
                                                 int rollouts,
                                                 Random random) {
        double total = 0.0;
        Map<Hazard, Integer> hazardCopies = resolveHazardCopies(state);
        for (int i = 0; i < rollouts; i++) {
            total += simulateRound(state, advisor, hazardCopies, forceContinue, random);
        }
        return total / rollouts;
    }

    private static int simulateRound(RoundState state,
                                     StrategyAdvisor advisor,
                                     Map<Hazard, Integer> hazardCopies,
                                     boolean forceContinue,
                                     Random random) {
        int activePlayers = Math.max(1, state.getActivePlayers());
        int turnNumber = state.getTurnNumber();
        int templeTreasure = state.getTempleTreasure();
        int sharedRoundTreasure = state.getRoundTreasure();
        int artifactsOnPath = state.getArtifactsOnPath();
        int artifactsClaimed = state.getArtifactsClaimed();
        boolean forcedDecisionUsed = false;

        Map<Hazard, Integer> hazardCounts = new EnumMap<>(Hazard.class);
        for (Hazard hazard : Hazard.values()) {
            hazardCounts.put(hazard, state.getHazardCount(hazard));
        }

        List<Card> deck = buildRemainingDeck(state, hazardCopies, random);

        while (!deck.isEmpty()) {
            int leavingCount = 0;
            boolean focusLeaving = false;

            RoundState decisionState = new RoundState(
                    turnNumber,
                    activePlayers,
                    templeTreasure,
                    sharedRoundTreasure,
                    hazardCounts,
                    hazardCopies,
                    artifactsOnPath,
                    artifactsClaimed
            );
            boolean defaultContinue = advisor.decide(decisionState).shouldContinue;

            for (int i = 0; i < activePlayers; i++) {
                boolean shouldContinue;
                if (!forcedDecisionUsed && i == 0) {
                    shouldContinue = forceContinue;
                    forcedDecisionUsed = true;
                } else {
                    shouldContinue = defaultContinue;
                }
                if (!shouldContinue) {
                    leavingCount++;
                    if (i == 0) {
                        focusLeaving = true;
                    }
                }
            }

            if (leavingCount > 0) {
                int share = templeTreasure / leavingCount;
	            templeTreasure = templeTreasure % leavingCount;

                if (focusLeaving) {
                    int gain = sharedRoundTreasure + share;
                    if (leavingCount == 1 && artifactsOnPath > 0) {
                        gain += artifactValue(artifactsOnPath, artifactsClaimed);
                    }
                    return gain;
                }

                if (leavingCount == 1 && artifactsOnPath > 0) {
                    artifactsClaimed += artifactsOnPath;
                    artifactsOnPath = 0;
                }

                activePlayers -= leavingCount;
                if (activePlayers <= 0) {
                    return 0;
                }
            }

            Card card = deck.removeLast();
            turnNumber++;

            if (card.getType() == Card.Type.TREASURE) {
                int value = card.getTreasureValue();
                int share = value / activePlayers;
                int remainder = value % activePlayers;
                sharedRoundTreasure += share;
                templeTreasure += remainder;
            } else if (card.getType() == Card.Type.HAZARD) {
                Hazard hazard = card.getHazard();
                int count = hazardCounts.getOrDefault(hazard, 0) + 1;
                hazardCounts.put(hazard, count);
                if (count >= 2) {
                    return 0;
                }
            } else {
                artifactsOnPath++;
            }
        }

        int share = templeTreasure / activePlayers;
        int gain = sharedRoundTreasure + share;
        if (activePlayers == 1 && artifactsOnPath > 0) {
            gain += artifactValue(artifactsOnPath, artifactsClaimed);
        }
        return gain;
    }

    private static int artifactValue(int artifactsOnPath, int artifactsClaimed) {
        int total = 0;
        int claimed = artifactsClaimed;
        for (int i = 0; i < artifactsOnPath; i++) {
            int value = claimed < ARTIFACT_LOW_COUNT ? ARTIFACT_LOW_VALUE : ARTIFACT_HIGH_VALUE;
            total += value;
            claimed++;
        }
        return total;
    }

    private static Map<Hazard, Integer> resolveHazardCopies(RoundState state) {
        Map<Hazard, Integer> provided = state.getHazardCopiesRemainingMap();
        boolean useDefault = provided == null || provided.isEmpty();
        EnumMap<Hazard, Integer> copies = new EnumMap<>(Hazard.class);
        for (Hazard hazard : Hazard.values()) {
            int value = useDefault ? DEFAULT_HAZARD_COPIES
                    : provided.getOrDefault(hazard, DEFAULT_HAZARD_COPIES);
            copies.put(hazard, Math.max(0, value));
        }
        return copies;
    }

    private static List<Card> buildRemainingDeck(RoundState state,
                                                 Map<Hazard, Integer> hazardCopies,
                                                 Random random) {
        List<Card> deck = new ArrayList<>();
        for (Hazard hazard : Hazard.values()) {
            int remaining = hazardCopies.getOrDefault(hazard, DEFAULT_HAZARD_COPIES)
                    - state.getHazardCount(hazard);
            if (remaining > 0) {
                for (int i = 0; i < remaining; i++) {
                    deck.add(Card.hazard(hazard));
                }
            }
        }

        int cardsDrawn = state.getTurnNumber();
        int hazardsRevealed = state.getTotalHazardsRevealed();
        int artifactsRevealed = Math.max(0, state.getArtifactsOnPath());
        int treasureDrawn = Math.max(0, cardsDrawn - hazardsRevealed - artifactsRevealed);

        List<Integer> treasureValues = new ArrayList<>(TREASURE_VALUES);
        for (int i = 0; i < treasureDrawn && !treasureValues.isEmpty(); i++) {
            int index = random.nextInt(treasureValues.size());
            treasureValues.remove(index);
        }
        for (int value : treasureValues) {
            deck.add(Card.treasure(value));
        }

        int remainingArtifacts = Math.max(0,
                TOTAL_ARTIFACTS - state.getArtifactsClaimed() - state.getArtifactsOnPath());
        for (int i = 0; i < remainingArtifacts; i++) {
            deck.add(Card.artifact(i + 1));
        }

        Collections.shuffle(deck, random);
        return deck;
    }
}
