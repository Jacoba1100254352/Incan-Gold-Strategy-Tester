package test;

import model.Card;
import model.Game;
import model.Hazard;
import model.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class TestFixtures {
    private TestFixtures() {
    }

    static Map<Hazard, Integer> hazardCopies(int copies) {
        Map<Hazard, Integer> result = new EnumMap<>(Hazard.class);
        for (Hazard hazard : Hazard.values()) {
            result.put(hazard, copies);
        }
        return result;
    }

    static class FixedDeckGame extends Game {
        private final List<Card> fixedDeck;

        FixedDeckGame(List<Player> players, List<Card> fixedDeck) {
            super(players, 1, 0, Collections.emptyList(), new Random(0));
            this.fixedDeck = fixedDeck;
        }

        @Override
        protected List<Card> buildDeck() {
            return new ArrayList<>(fixedDeck);
        }

        @Override
        protected void shuffleDeck(List<Card> deck) {
            // Preserve deterministic order for tests.
        }
    }

    static class FixedTreasureRoundsGame extends Game {
        FixedTreasureRoundsGame(List<Player> players, int rounds, int treasureValue) {
            super(players, rounds, 0, List.of(treasureValue), new Random(0));
        }

        @Override
        protected void shuffleDeck(List<Card> deck) {
            // Preserve deterministic order for tests.
        }
    }

    static class ReusedTreasureCardGame extends Game {
        private final Card treasureCard;

        ReusedTreasureCardGame(List<Player> players, int rounds, int treasureValue) {
            super(players, rounds, 0, Collections.emptyList(), new Random(0));
            this.treasureCard = Card.treasure(treasureValue);
        }

        @Override
        protected List<Card> buildDeck() {
            return new ArrayList<>(List.of(treasureCard));
        }

        @Override
        protected void shuffleDeck(List<Card> deck) {
            // Preserve deterministic order for tests.
        }
    }
}
