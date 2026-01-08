package model;

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

    public static Card treasure(int value) {
        return new Card(Type.TREASURE, value, null, 0);
    }

    public static Card hazard(Hazard hazard) {
        return new Card(Type.HAZARD, 0, hazard, 0);
    }

    public static Card artifact(int artifactId) {
        return new Card(Type.ARTIFACT, 0, null, artifactId);
    }

    public Type getType() {
        return type;
    }

    public int getTreasureValue() {
        return treasureValue;
    }

    public int getRemainingTreasure() {
        return remainingTreasure;
    }

    public void setRemainingTreasure(int remainingTreasure) {
        this.remainingTreasure = remainingTreasure;
    }

    public Hazard getHazard() {
        return hazard;
    }

    public int getArtifactId() {
        return artifactId;
    }
}
