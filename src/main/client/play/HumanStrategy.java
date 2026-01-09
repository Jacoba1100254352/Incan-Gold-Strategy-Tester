package client.play;

import algorithm.Strategy;
import model.Hazard;
import model.RoundState;

import java.util.Scanner;

/**
 * Prompts a human player to decide whether to continue or leave.
 */
public class HumanStrategy implements Strategy {
    private final String name;
    private final Scanner scanner;

    /**
     * Creates a human strategy that prompts on each decision.
     *
     * @param name display name for prompts
     * @param scanner scanner for reading input
     */
    public HumanStrategy(String name, Scanner scanner) {
        this.name = name;
        this.scanner = scanner;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        while (true) {
            System.out.printf(
                    "%s | Turn %d | Active players: %d | Unclaimed treasure: %d | Your treasure: %d | Hazards: %s | Artifacts: %d%n",
                    name,
                    state.getTurnNumber(),
                    state.getActivePlayers(),
                    state.getTempleTreasure(),
                    state.getRoundTreasure(),
                    formatHazardCounts(state),
                    state.getArtifactsOnPath()
            );
            System.out.print("Continue or leave? (c/l): ");
            String input = scanner.nextLine().trim().toLowerCase();
            if (input.startsWith("c")) {
                return true;
            }
            if (input.startsWith("l")) {
                return false;
            }
            System.out.println("Please enter c to continue or l to leave.");
        }
    }
    /**
     * Formats hazard counts.
     */
    private String formatHazardCounts(RoundState state) {
        StringBuilder builder = new StringBuilder();
        for (Hazard hazard : Hazard.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(hazard).append('=').append(state.getHazardCount(hazard));
        }
        return builder.toString();
    }
}
