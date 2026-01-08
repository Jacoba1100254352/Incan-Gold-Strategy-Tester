package model;

public class Card {
    public enum Type {
        TREASURE,
        HAZARD
    }

    private final Type type;
    private final int treasureValue;
    private final Hazard hazard;

    private Card(Type type, int treasureValue, Hazard hazard) {
        this.type = type;
        this.treasureValue = treasureValue;
        this.hazard = hazard;
    }

    public static Card treasure(int value) {
        return new Card(Type.TREASURE, value, null);
    }

    public static Card hazard(Hazard hazard) {
        return new Card(Type.HAZARD, 0, hazard);
    }

    public Type getType() {
        return type;
    }

    public int getTreasureValue() {
        return treasureValue;
    }

    public Hazard getHazard() {
        return hazard;
    }
}
