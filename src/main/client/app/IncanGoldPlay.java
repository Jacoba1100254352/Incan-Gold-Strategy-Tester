package client.app;

import algorithm.Strategy;
import client.ai.AIDifficulty;
import client.ai.AdaptiveAIStrategy;
import client.ai.NeuralNetworkStrategy;
import client.ai.StrategyAdvisor;
import client.play.HumanStrategy;
import model.Card;
import model.Game;
import model.Hazard;
import model.Player;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Interactive CLI for playing Incan Gold with humans and/or AI opponents.
 */
public class IncanGoldPlay {
    // Default total number of players.
    private static final int DEFAULT_PLAYERS = 4;
    // Minimum allowed number of players.
    private static final int MIN_PLAYERS = 2;
    // Maximum allowed number of players.
    private static final int MAX_PLAYERS = 8;
    // Default number of human players.
    private static final int DEFAULT_HUMAN_PLAYERS = 1;
    // Minimum allowed number of human players.
    private static final int MIN_HUMAN_PLAYERS = 0;
    // Option number for easy difficulty in the prompt.
    private static final int EASY_DIFFICULTY_OPTION = 1;
    // Option number for medium difficulty in the prompt.
    private static final int MEDIUM_DIFFICULTY_OPTION = 2;
    // Option number for hard difficulty in the prompt.
    private static final int HARD_DIFFICULTY_OPTION = 3;
    // Option number for advisor-based AI.
    private static final int ADVISOR_AI_OPTION = 1;
    // Option number for neural-network AI.
    private static final int NEURAL_AI_OPTION = 2;
    // Default difficulty option number.
    private static final int DEFAULT_DIFFICULTY_OPTION = MEDIUM_DIFFICULTY_OPTION;
    // Default AI mode option number.
    private static final int DEFAULT_AI_OPTION = ADVISOR_AI_OPTION;
    // Default neural model path.
    private static final String DEFAULT_NEURAL_MODEL_PATH = "results/models/strategy-net.json";
    // Default neural decision threshold.
    private static final double DEFAULT_NEURAL_THRESHOLD = 0.5;
    // Toggle to print per-turn card and hazard details.
    private static final boolean SHOW_HAZARD_LOG = true;

    /**
     * Entry point for interactive play.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Incan Gold - Play Mode");

        int totalPlayers = promptInt(scanner,
                "Total players (" + MIN_PLAYERS + "-" + MAX_PLAYERS + ")",
                DEFAULT_PLAYERS,
                MIN_PLAYERS,
                MAX_PLAYERS);
        int humanPlayers = promptInt(scanner,
                "Human players (0-" + totalPlayers + ")",
                Math.min(DEFAULT_HUMAN_PLAYERS, totalPlayers),
                MIN_HUMAN_PLAYERS,
                totalPlayers);
        int aiPlayers = totalPlayers - humanPlayers;

        StrategyAdvisor advisor = null;
        AiMode aiMode = AiMode.ADVISOR;
        String neuralModelPath = DEFAULT_NEURAL_MODEL_PATH;
        double neuralThreshold = DEFAULT_NEURAL_THRESHOLD;
        if (aiPlayers > 0) {
            aiMode = promptAiMode(scanner);
            if (aiMode == AiMode.NEURAL) {
                neuralModelPath = promptString(scanner, "Neural model path", DEFAULT_NEURAL_MODEL_PATH);
                neuralThreshold = promptDouble(scanner, "Neural continue threshold (0-1)",
                        DEFAULT_NEURAL_THRESHOLD, 0.0, 1.0);
            }
            AIDifficulty difficulty = promptDifficulty(scanner,
                    aiMode == AiMode.NEURAL ? "Fallback advisor difficulty" : "AI difficulty");
            System.out.printf("Building AI strategy table (%s)...%n", difficulty);
            advisor = StrategyAdvisor.buildDefault(difficulty, totalPlayers);
            System.out.println("AI strategy table ready.");
        }

        List<NamedPlayer> participants = new ArrayList<>();
        for (int i = 1; i <= humanPlayers; i++) {
            String name = "Player " + i;
            participants.add(new NamedPlayer(name, new Player(new HumanStrategy(name, scanner))));
        }
        for (int i = 1; i <= aiPlayers; i++) {
            String name = "AI " + i;
            participants.add(new NamedPlayer(name, new Player(buildAiStrategy(
                    name,
                    aiMode,
                    advisor,
                    neuralModelPath,
                    neuralThreshold))));
        }

        List<Player> players = new ArrayList<>();
        for (NamedPlayer participant : participants) {
            players.add(participant.player);
        }

        Game game = SHOW_HAZARD_LOG ? new VerboseGame(players) : new Game(players);
        game.playGame();
        printScores(participants);
    }
    /**
     * Handles print scores.
     */
    private static void printScores(List<NamedPlayer> participants) {
        System.out.println("Final scores:");
        int bestScore = Integer.MIN_VALUE;
        int bestArtifacts = Integer.MIN_VALUE;
        List<NamedPlayer> winners = new ArrayList<>();
        for (NamedPlayer participant : participants) {
            int score = participant.player.getTotalTreasure();
            int artifacts = participant.player.getArtifactsClaimed();
            System.out.printf("%s: %d (artifacts: %d)%n", participant.name, score, artifacts);
            if (score > bestScore || (score == bestScore && artifacts > bestArtifacts)) {
                bestScore = score;
                bestArtifacts = artifacts;
                winners.clear();
                winners.add(participant);
            } else if (score == bestScore && artifacts == bestArtifacts) {
                winners.add(participant);
            }
        }
        if (!winners.isEmpty()) {
            System.out.printf("Winner%s: %s (%d)%n",
                    winners.size() > 1 ? "s" : "",
                    joinNames(winners),
                    bestScore);
        }
    }
    /**
     * Handles join names.
     */
    private static String joinNames(List<NamedPlayer> participants) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < participants.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(participants.get(i).name);
        }
        return builder.toString();
    }
    /**
     * Handles prompt int.
     */
    private static int promptInt(Scanner scanner, String prompt, int defaultValue, int min, int max) {
        while (true) {
            System.out.printf("%s [default %d]: ", prompt, defaultValue);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("Enter a number between %d and %d.%n", min, max);
        }
    }
    /**
     * Handles prompt ai mode.
     */
    private static AiMode promptAiMode(Scanner scanner) {
        System.out.printf("AI mode: %d) advisor %d) neural%n", ADVISOR_AI_OPTION, NEURAL_AI_OPTION);
        System.out.printf("Choose AI mode [default %d]: ", DEFAULT_AI_OPTION);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return AiMode.ADVISOR;
        }
        return AiMode.fromInput(input);
    }
    /**
     * Handles prompt difficulty.
     */
    private static AIDifficulty promptDifficulty(Scanner scanner, String label) {
        System.out.printf("%s: %d) easy %d) medium %d) hard%n",
                label, EASY_DIFFICULTY_OPTION, MEDIUM_DIFFICULTY_OPTION, HARD_DIFFICULTY_OPTION);
        System.out.printf("Choose difficulty [default %d]: ", DEFAULT_DIFFICULTY_OPTION);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return AIDifficulty.MEDIUM;
        }
        return AIDifficulty.fromInput(input);
    }
    /**
     * Handles prompt string.
     */
    private static String promptString(Scanner scanner, String prompt, String defaultValue) {
        System.out.printf("%s [default %s]: ", prompt, defaultValue);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return defaultValue;
        }
        return input;
    }
    /**
     * Handles prompt double.
     */
    private static double promptDouble(Scanner scanner, String prompt, double defaultValue, double min, double max) {
        while (true) {
            System.out.printf("%s [default %.2f]: ", prompt, defaultValue);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                double value = Double.parseDouble(input);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
            }
            System.out.printf("Enter a number between %.2f and %.2f.%n", min, max);
        }
    }
    /**
     * Builds ai strategy.
     */
    private static Strategy buildAiStrategy(String name,
                                            AiMode aiMode,
                                            StrategyAdvisor advisor,
                                            String modelPath,
                                            double threshold) {
        if (aiMode == AiMode.NEURAL) {
            Strategy fallback = advisor == null ? null : new AdaptiveAIStrategy(name, advisor, false);
            return new NeuralNetworkStrategy(name, Path.of(modelPath), threshold, fallback);
        }
        return new AdaptiveAIStrategy(name, advisor, true);
    }
    
    private record NamedPlayer(String name, Player player)
    {
    }

    private enum AiMode {
        ADVISOR,
        NEURAL;
        /**
         * Handles from input.
         */
        private static AiMode fromInput(String input) {
            String normalized = input.trim().toLowerCase();
            return switch (normalized) {
                case "2", "neural", "nn" -> NEURAL;
                default -> ADVISOR;
            };
        }
    }

    /**
     * Game subclass that prints revealed cards and hazard counts each turn.
     */
    private static class VerboseGame extends Game {
        /**
         * Creates a verbose game.
         */
        private VerboseGame(List<Player> players) {
            super(players);
        }
        /**
         * Handles on card revealed.
         */

        @Override
        protected void onCardRevealed(Card card,
                                      int turnNumber,
                                      int templeTreasure,
                                      Map<Hazard, Integer> hazardCounts,
                                      int activePlayers,
                                      int artifactsOnPath) {
            String hazardSummary = formatHazards(hazardCounts);
            if (card.getType() == Card.Type.HAZARD) {
                System.out.printf("Turn %d: Hazard %s | Hazards: %s | Artifacts: %d%n",
                        turnNumber, card.getHazard(), hazardSummary, artifactsOnPath);
            } else if (card.getType() == Card.Type.ARTIFACT) {
                System.out.printf("Turn %d: Artifact | Hazards: %s | Artifacts: %d%n",
                        turnNumber, hazardSummary, artifactsOnPath);
            } else {
                System.out.printf("Turn %d: Treasure %d | Hazards: %s | Artifacts: %d%n",
                        turnNumber, card.getTreasureValue(), hazardSummary, artifactsOnPath);
            }
        }

        @Override
        protected void onRoundEndedByHazard(Hazard hazard, Map<Hazard, Integer> hazardCounts) {
            System.out.printf("Round ends: second %s revealed.%n", hazard);
        }
        private String formatHazards(Map<Hazard, Integer> hazardCounts) {
            StringBuilder builder = new StringBuilder();
            for (Hazard hazard : Hazard.values()) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                builder.append(hazard).append('=').append(hazardCounts.getOrDefault(hazard, 0));
            }
            return builder.toString();
        }
    }
}
