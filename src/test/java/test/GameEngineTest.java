package test;

import algorithm.AlwaysContinueStrategy;
import algorithm.LeaveAfterHazardsStrategy;
import algorithm.LeaveAfterTurnsStrategy;
import algorithm.RiskAverseStrategy;
import algorithm.Strategy;
import model.Card;
import model.Game;
import model.Hazard;
import model.Player;
import model.RoundState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameEngineTest {
    @Test
    void repeatedHazardEndsRoundAndLosesUnbankedTreasure() {
        List<Card> deck = Arrays.asList(
                Card.treasure(5),
                Card.hazard(Hazard.SNAKE),
                Card.treasure(4),
                Card.hazard(Hazard.SNAKE)
        );
        List<Player> players = new ArrayList<>();
        players.add(new Player(new AlwaysContinueStrategy()));
        players.add(new Player(new AlwaysContinueStrategy()));

        Game game = new TestFixtures.FixedDeckGame(players, deck);
        game.playGame();

        assertEquals(0, players.get(0).getTotalTreasure());
        assertEquals(0, players.get(1).getTotalTreasure());
    }

    @Test
    void leavingBanksTempleShareAndRemainderStaysOnPath() {
        List<Card> deck = Arrays.asList(
                Card.treasure(5),
                Card.hazard(Hazard.SNAKE),
                Card.treasure(4),
                Card.hazard(Hazard.SPIDER)
        );
        List<Player> players = new ArrayList<>();
        players.add(new Player(new LeaveAfterHazardsStrategy(1)));
        players.add(new Player(new AlwaysContinueStrategy()));

        Game game = new TestFixtures.FixedDeckGame(players, deck);
        game.playGame();

        assertEquals(3, players.get(0).getTotalTreasure());
        assertEquals(6, players.get(1).getTotalTreasure());
    }

    @Test
    void loneLeaverClaimsArtifactsOnPath() {
        List<Card> deck = Arrays.asList(
                Card.artifact(1),
                Card.hazard(Hazard.SNAKE),
                Card.hazard(Hazard.SNAKE)
        );
        List<Player> players = new ArrayList<>();
        players.add(new Player(new LeaveAfterTurnsStrategy(1)));
        players.add(new Player(new AlwaysContinueStrategy()));

        Game game = new TestFixtures.FixedDeckGame(players, deck);
        game.playGame();

        assertEquals(5, players.get(0).getTotalTreasure());
        assertEquals(1, players.get(0).getArtifactsClaimed());
        assertEquals(0, players.get(1).getTotalTreasure());
    }

    @Test
    void treasureRemainderDoesNotLeakAcrossRounds() {
        List<Player> players = new ArrayList<>();
        players.add(new Player(new AlwaysContinueStrategy()));
        players.add(new Player(new AlwaysContinueStrategy()));

        Game game = new TestFixtures.FixedTreasureRoundsGame(players, 2, 5);
        game.playGame();

        assertEquals(4, players.get(0).getTotalTreasure());
        assertEquals(4, players.get(1).getTotalTreasure());
    }

    @Test
    void staleRemainderOnAnInjectedCardIsNeverCountedAsPrintedTreasure() {
        List<Player> players = List.of(
                new Player(new AlwaysContinueStrategy()),
                new Player(new AlwaysContinueStrategy())
        );

        Game game = new TestFixtures.ReusedTreasureCardGame(players, 2, 5);
        game.playGame();

        assertEquals(4, players.get(0).getTotalTreasure());
        assertEquals(4, players.get(1).getTotalTreasure());
    }

    @Test
    void constructorRejectsInvalidGameConfiguration() {
        List<Player> players = List.of(new Player(new AlwaysContinueStrategy()));

        assertThrows(NullPointerException.class, () -> new Game(null));
        assertThrows(NullPointerException.class, () -> new Game(Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class, () -> new Game(players, -1, 3, List.of(1), new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> new Game(players, 1, -1, List.of(1), new Random(0)));
        assertThrows(NullPointerException.class, () -> new Game(players, 1, 3, null, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> new Game(players, 1, 3, List.of(0), new Random(0)));
        assertThrows(NullPointerException.class, () -> new Game(players, 1, 3, List.of(1), null));
    }

    @Test
    void repeatedHazardRemovesOnlyOneFutureHazardCopy() {
        Player player = new Player(new AlwaysContinueStrategy());
        DeckInspectionGame game = new DeckInspectionGame(List.of(player), 3, 1);

        game.removeHazard(Hazard.SNAKE);
        List<Card> deck = game.roundDeck();

        long snakes = deck.stream()
                .filter(card -> card.getType() == Card.Type.HAZARD && card.getHazard() == Hazard.SNAKE)
                .count();
        long spiders = deck.stream()
                .filter(card -> card.getType() == Card.Type.HAZARD && card.getHazard() == Hazard.SPIDER)
                .count();

        assertEquals(2, snakes);
        assertEquals(3, spiders);
    }

    @Test
    void unclaimedArtifactsAreRemovedAfterHazardAndClaimedArtifactsLeaveDeck() {
        Player player = new Player(new AlwaysContinueStrategy());
        DeckInspectionGame game = new DeckInspectionGame(List.of(player), 0, 1);

        List<Card> firstRoundDeck = game.roundDeck();
        List<Card> artifactOnPath = firstRoundDeck.stream()
                .filter(card -> card.getType() == Card.Type.ARTIFACT)
                .toList();
        assertEquals(1, artifactOnPath.size());

        game.removeArtifacts(artifactOnPath);
        List<Card> secondRoundDeck = game.roundDeck();
        assertEquals(1, secondRoundDeck.stream()
                .filter(card -> card.getType() == Card.Type.ARTIFACT)
                .count());
    }

    @Test
    void revealedArtifactsAreRemovedWhenEveryoneLeavesTogether() {
        List<Player> players = List.of(
                new Player(new LeaveAfterTurnsStrategy(1)),
                new Player(new LeaveAfterTurnsStrategy(1))
        );
        ArtifactLifecycleGame game = new ArtifactLifecycleGame(players, 2, List.of(1), true);

        game.playGame();

        assertEquals(List.of(1, 1), game.artifactCountsAtRoundStart);
        assertEquals(0, players.stream().mapToInt(Player::getArtifactsClaimed).sum());
    }

    @Test
    void revealedArtifactsAreRemovedWhenTheDeckRunsOutWithMultiplePlayers() {
        List<Player> players = List.of(
                new Player(new AlwaysContinueStrategy()),
                new Player(new AlwaysContinueStrategy())
        );
        ArtifactLifecycleGame game = new ArtifactLifecycleGame(players, 2, List.of(), true);

        game.playGame();

        assertEquals(List.of(1, 1), game.artifactCountsAtRoundStart);
        assertEquals(0, players.stream().mapToInt(Player::getArtifactsClaimed).sum());
    }

    @Test
    void unrevealedArtifactsCarryIntoTheNextRound() {
        List<Player> players = List.of(
                new Player(new LeaveAfterTurnsStrategy(1)),
                new Player(new LeaveAfterTurnsStrategy(1))
        );
        ArtifactLifecycleGame game = new ArtifactLifecycleGame(players, 2, List.of(1), false);

        game.playGame();

        assertEquals(List.of(1, 2), game.artifactCountsAtRoundStart);
    }

    @Test
    void firstThreeArtifactsScoreFiveAndTheLastTwoScoreTen() {
        Player player = new Player(new LeaveAfterTurnsStrategy(1));
        ArtifactLifecycleGame game = new ArtifactLifecycleGame(List.of(player), 5, List.of(), true);

        game.playGame();

        assertEquals(5, player.getArtifactsClaimed());
        assertEquals(35, player.getTotalTreasure());
    }

    @Test
    void firstCardIsRevealedBeforeTheFirstDecision() {
        CountingLeaveStrategy strategy = new CountingLeaveStrategy();
        Player player = new Player(strategy);
        Game game = new TestFixtures.FixedDeckGame(
                List.of(player),
                List.of(Card.treasure(5), Card.treasure(9))
        );

        game.playGame();

        assertEquals(1, strategy.decisions);
        assertEquals(5, player.getTotalTreasure());
    }

    @Test
    void defaultDeckMatchesTheTargetBgaRuleset() {
        List<Card> deck = new DefaultDeckInspectionGame().roundDeck();

        assertEquals(14, deck.stream().filter(card -> card.getType() == Card.Type.TREASURE).count());
        assertEquals(15, deck.stream().filter(card -> card.getType() == Card.Type.HAZARD).count());
        assertEquals(1, deck.stream().filter(card -> card.getType() == Card.Type.ARTIFACT).count());
        assertEquals(Game.defaultTreasureValues(), deck.stream()
                .filter(card -> card.getType() == Card.Type.TREASURE)
                .map(Card::getTreasureValue)
                .sorted()
                .toList());
    }

    @Test
    void randomizedGamesRespectTreasureAndArtifactConservationBounds() {
        int maximumTotalScore = Game.defaultTreasureValues().stream().mapToInt(Integer::intValue).sum() * 5
                + Game.artifactLowValue() * Game.artifactLowCount()
                + Game.artifactHighValue() * (Game.totalArtifacts() - Game.artifactLowCount());

        for (int seed = 0; seed < 100; seed++) {
            List<Player> players = List.of(
                    new Player(new AlwaysContinueStrategy()),
                    new Player(new LeaveAfterTurnsStrategy(7)),
                    new Player(new LeaveAfterHazardsStrategy(3)),
                    new Player(new RiskAverseStrategy())
            );
            new Game(players, new Random(seed)).playGame();

            int totalScore = players.stream().mapToInt(Player::getTotalTreasure).sum();
            int claimedArtifacts = players.stream().mapToInt(Player::getArtifactsClaimed).sum();
            assertTrue(totalScore >= 0 && totalScore <= maximumTotalScore,
                    "score conservation failed for seed " + seed + ": " + totalScore);
            assertTrue(claimedArtifacts >= 0 && claimedArtifacts <= Game.totalArtifacts(),
                    "artifact conservation failed for seed " + seed + ": " + claimedArtifacts);
        }
    }

    private static class DeckInspectionGame extends Game {
        private DeckInspectionGame(List<Player> players, int hazardCopies, int treasureValue) {
            super(players, 0, hazardCopies, List.of(treasureValue), new Random(0));
        }

        private List<Card> roundDeck() {
            return createRoundDeck();
        }

        private void removeHazard(Hazard hazard) {
            removeHazardCopy(hazard);
        }

        private void removeArtifacts(List<Card> artifacts) {
            removeArtifactsFromGame(new ArrayList<>(artifacts));
        }

        @Override
        protected void shuffleDeck(List<Card> deck) {
            // Preserve deterministic order for tests.
        }
    }

    private static final class DefaultDeckInspectionGame extends Game {
        private DefaultDeckInspectionGame() {
            super(List.of(), new Random(0));
        }

        private List<Card> roundDeck() {
            return createRoundDeck();
        }

        @Override
        protected void shuffleDeck(List<Card> deck) {
            deck.sort(Comparator.comparingInt(Card::getTreasureValue));
        }
    }

    private static final class ArtifactLifecycleGame extends Game {
        private final List<Integer> artifactCountsAtRoundStart = new ArrayList<>();
        private final boolean artifactFirst;

        private ArtifactLifecycleGame(List<Player> players,
                                      int rounds,
                                      List<Integer> treasureValues,
                                      boolean artifactFirst) {
            super(players, rounds, 0, treasureValues, new Random(0));
            this.artifactFirst = artifactFirst;
        }

        @Override
        protected List<Card> createRoundDeck() {
            List<Card> deck = super.createRoundDeck();
            artifactCountsAtRoundStart.add((int) deck.stream()
                    .filter(card -> card.getType() == Card.Type.ARTIFACT)
                    .count());
            return deck;
        }

        @Override
        protected void shuffleDeck(List<Card> deck) {
            deck.sort(Comparator.comparingInt(this::cardPriority));
        }

        private int cardPriority(Card card) {
            if (artifactFirst) {
                return card.getType() == Card.Type.ARTIFACT ? 0 : 1;
            }
            return card.getType() == Card.Type.TREASURE ? 0 : 1;
        }
    }

    private static final class CountingLeaveStrategy implements Strategy {
        private int decisions;

        @Override
        public boolean shouldContinue(RoundState state) {
            decisions++;
            return false;
        }
    }
}
