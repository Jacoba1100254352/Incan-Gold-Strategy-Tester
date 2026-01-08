package algorithm;

import model.RoundState;

/**
 * Continues until hazards appear, then stays for a fixed number of additional turns.
 */
public class SwitchAfterHazardsForTurnsStrategy implements Strategy {
    private final int hazardThreshold;
    private final int extraTurns;
    private int hazardTriggerTurn = -1;
    private int lastTurnNumber = -1;
    private int lastHazardCount = 0;

    /**
     * Creates a strategy that stays for a fixed number of turns after hazards appear.
     *
     * @param hazardThreshold hazard count that triggers the extra-turn window
     * @param extraTurns turns to stay after the trigger
     */
    public SwitchAfterHazardsForTurnsStrategy(int hazardThreshold, int extraTurns) {
        this.hazardThreshold = hazardThreshold;
        this.extraTurns = Math.max(0, extraTurns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldContinue(RoundState state) {
        int turnNumber = state.getTurnNumber();
        int hazardsSeen = state.getTotalHazardsRevealed();

        if (turnNumber < lastTurnNumber || hazardsSeen < lastHazardCount) {
            hazardTriggerTurn = -1;
        }

        lastTurnNumber = turnNumber;
        lastHazardCount = hazardsSeen;

        if (hazardsSeen < hazardThreshold) {
            return true;
        }

        if (hazardTriggerTurn < 0) {
            hazardTriggerTurn = turnNumber;
        }

        int turnsSinceTrigger = turnNumber - hazardTriggerTurn;
        return turnsSinceTrigger < extraTurns;
    }

    /**
     * Returns a display name for logging.
     */
    @Override
    public String toString() {
        return "SwitchAfterHazardsForTurns(" + hazardThreshold + "," + extraTurns + ")";
    }
}
