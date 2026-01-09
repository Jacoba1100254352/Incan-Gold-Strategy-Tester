package client.ai;

/**
 * Defines AI difficulty levels by controlling simulation depth.
 */
public enum AIDifficulty {
    /** Low-cost evaluation for faster, less consistent choices. */
    EASY(1, 500),
    /** Balanced evaluation for reasonable runtime and stability. */
    MEDIUM(2, 2000),
    /** High-cost evaluation for more stable, stronger choices. */
    HARD(3, 5000);

    private final int repeats;
    private final int simulations;
    /**
     * Creates a aidifficulty.
     */
    AIDifficulty(int repeats, int simulations) {
        this.repeats = repeats;
        this.simulations = simulations;
    }

    /**
     * Returns the number of sweep repeats for this difficulty.
     */
    public int getRepeats() {
        return repeats;
    }

    /**
     * Returns the simulations per strategy for this difficulty.
     */
    public int getSimulations() {
        return simulations;
    }

    /**
     * Maps user input to a difficulty, defaulting to medium when unknown.
     */
    public static AIDifficulty fromInput(String input) {
        String normalized = input.trim().toLowerCase();
	    return switch (normalized) {
		    case "1", "easy" -> EASY;
		    case "2", "medium" -> MEDIUM;
		    case "3", "hard" -> HARD;
		    default -> MEDIUM;
	    };
    }
}
