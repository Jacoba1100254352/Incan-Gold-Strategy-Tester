package model;

/**
 * Represents a quest deck card: treasure, hazard, or artifact.
 */
public class Card {
    public enum Type {
        TREASURE,
        HAZARD,
        ARTIFACT
    }

    private final Type type;
    private final int treasureValue;
    private int remainingTreasure;
    private final Hazard hazard;
    private final int artifactId;

    private Card(Type type, int treasureValue, Hazard hazard, int artifactId) {
        this.type = type;
        this.treasureValue = treasureValue;
        this.hazard = hazard;
        this.artifactId = artifactId;
        this.remainingTreasure = 0;
    }

    /**
     * Creates a treasure card with the provided value.
     *
     * @param value treasure value on the card
     * @return treasure card instance
     */
    public static Card treasure(int value) {
        return new Card(Type.TREASURE, value, null, 0);
    }

    /**
     * Creates a hazard card with the provided hazard type.
     *
     * @param hazard hazard type
     * @return hazard card instance
     */
    public static Card hazard(Hazard hazard) {
        return new Card(Type.HAZARD, 0, hazard, 0);
    }

    /**
     * Creates an artifact card with the provided artifact id.
     *
     * @param artifactId artifact identifier
     * @return artifact card instance
     */
    public static Card artifact(int artifactId) {
        return new Card(Type.ARTIFACT, 0, null, artifactId);
    }

    /**
     * Returns the card type.
     *
     * @return card type
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the treasure value printed on the card.
     *
     * @return treasure value
     */
    public int getTreasureValue() {
        return treasureValue;
    }

    /**
     * Returns the remaining treasure left on the card.
     *
     * @return remaining treasure value
     */
    public int getRemainingTreasure() {
        return remainingTreasure;
    }

    /**
     * Sets the remaining treasure left on the card.
     *
     * @param remainingTreasure remaining treasure value
     */
    public void setRemainingTreasure(int remainingTreasure) {
        this.remainingTreasure = remainingTreasure;
    }

    /**
     * Returns the hazard type for hazard cards.
     *
     * @return hazard type or null
     */
    public Hazard getHazard() {
        return hazard;
    }

    /**
     * Returns the artifact id for artifact cards.
     *
     * @return artifact id
     */
    public int getArtifactId() {
        return artifactId;
    }
}
