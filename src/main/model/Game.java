package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Core game engine for simulating Incan Gold rounds and scoring.
 */
public class Game {
    // Default number of rounds per game.
    private static final int DEFAULT_ROUNDS = 5;
    // Default copies of each hazard in the deck.
    private static final int DEFAULT_HAZARD_COPIES = 3;
    // Total artifacts included across all rounds.
    private static final int TOTAL_ARTIFACTS = 5;
    // Artifact point value for the first set claimed.
    private static final int ARTIFACT_LOW_VALUE = 5;
    // Artifact point value for later claims.
    private static final int ARTIFACT_HIGH_VALUE = 10;
    // Number of artifacts scored at the lower value.
    private static final int ARTIFACT_LOW_COUNT = 3;
    // Default treasure values included in the deck.
    private static final List<Integer> DEFAULT_TREASURE_VALUES = Arrays.asList(
            1, 2, 3, 4, 5,
            6, 7, 8, 9, 10,
            11, 12, 13, 14, 15
    );

    private final List<Player> players;
    private final int rounds;
    private final int hazardCopies;
    private final List<Card> treasureCards;
    private final Random random;
    private final Map<Hazard, Integer> hazardCopiesRemaining;
    private final List<Card> remainingArtifacts;
    private int nextArtifactIndex;
    private int artifactsClaimed;

    /**
     * Creates a game using default deck and round settings.
     */
    public Game(List<Player> players) {
        this(players, DEFAULT_ROUNDS, DEFAULT_HAZARD_COPIES, DEFAULT_TREASURE_VALUES, new Random());
    }

    /**
     * Creates a game with explicit deck configuration and RNG.
     */
    public Game(List<Player> players,
                int rounds,
                int hazardCopies,
                List<Integer> treasureValues,
                Random random) {
        this.players = players;
        this.rounds = rounds;
        this.hazardCopies = hazardCopies;
        this.treasureCards = new ArrayList<>();
        for (int value : treasureValues) {
            this.treasureCards.add(Card.treasure(value));
        }
        this.random = random;
        this.hazardCopiesRemaining = new EnumMap<>(Hazard.class);
        for (Hazard hazard : Hazard.values()) {
            this.hazardCopiesRemaining.put(hazard, hazardCopies);
        }
        this.remainingArtifacts = new ArrayList<>();
        this.nextArtifactIndex = 0;
        this.artifactsClaimed = 0;
    }

    /**
     * Plays all rounds of the game using current players and deck settings.
     */
    public void playGame() {
        for (int round = 0; round < rounds; round++) {
            playRound();
        }
    }

    protected void playRound() {
        for (Player player : players) {
            player.startRound();
        }

        List<Card> deck = createRoundDeck();

        List<Player> activePlayers = new ArrayList<>(players);
        Map<Hazard, Integer> hazardCounts = new EnumMap<>(Hazard.class);
        List<Card> artifactsOnPath = new ArrayList<>();
        List<Card> treasureCardsOnPath = new ArrayList<>();
        int templeTreasure = 0;
        int turnNumber = 0;
        boolean firstTurn = true;

        while (!deck.isEmpty()) {
            if (!firstTurn) {
                List<Player> leavingPlayers = new ArrayList<>();
                for (Player player : activePlayers) {
                    RoundState state = new RoundState(
                            turnNumber,
                            activePlayers.size(),
                            templeTreasure,
                            player.getRoundTreasure(),
                            hazardCounts,
                            artifactsOnPath.size()
                    );
                    if (!player.makeDecision(state)) {
                        leavingPlayers.add(player);
                    }
                }

                if (!leavingPlayers.isEmpty()) {
                    int share = templeTreasure / leavingPlayers.size();
                    int remainder = templeTreasure % leavingPlayers.size();
                    for (Player player : leavingPlayers) {
                        player.leaveRound(share);
                    }
                    templeTreasure = remainder;
                    redistributeTreasureRemainder(treasureCardsOnPath, remainder);

                    if (leavingPlayers.size() == 1 && !artifactsOnPath.isEmpty()) {
                        awardArtifacts(leavingPlayers.getFirst(), artifactsOnPath);
                    }

                    activePlayers.removeAll(leavingPlayers);
                }
            } else {
                firstTurn = false;
            }

            if (activePlayers.isEmpty()) {
                break;
            }

            Card card = deck.removeFirst();
            turnNumber++;

            if (card.getType() == Card.Type.TREASURE) {
                int value = card.getTreasureValue() + card.getRemainingTreasure();
                int share = value / activePlayers.size();
                int remainder = value % activePlayers.size();
                for (Player player : activePlayers) {
                    player.collect(share);
                }
                card.setRemainingTreasure(remainder);
                templeTreasure += remainder;
                treasureCardsOnPath.add(card);
                onCardRevealed(card, turnNumber, templeTreasure, snapshotHazards(hazardCounts),
                        activePlayers.size(), artifactsOnPath.size());
            } else if (card.getType() == Card.Type.HAZARD) {
                Hazard hazard = card.getHazard();
                int count = hazardCounts.getOrDefault(hazard, 0) + 1;
                hazardCounts.put(hazard, count);
                onCardRevealed(card, turnNumber, templeTreasure, snapshotHazards(hazardCounts),
                        activePlayers.size(), artifactsOnPath.size());
                if (count >= 2) {
                    onRoundEndedByHazard(hazard, snapshotHazards(hazardCounts));
                    removeHazardCopy(hazard);
                    removeArtifactsFromGame(artifactsOnPath);
                    for (Player player : activePlayers) {
                        player.loseRoundTreasure();
                    }
                    return;
                }
            } else {
                artifactsOnPath.add(card);
                onCardRevealed(card, turnNumber, templeTreasure, snapshotHazards(hazardCounts),
                        activePlayers.size(), artifactsOnPath.size());
            }
        }

        if (!activePlayers.isEmpty()) {
            int share = templeTreasure / activePlayers.size();
            int remainder = templeTreasure % activePlayers.size();
            for (Player player : activePlayers) {
                player.leaveRound(share);
            }
            redistributeTreasureRemainder(treasureCardsOnPath, remainder);
            if (activePlayers.size() == 1 && !artifactsOnPath.isEmpty()) {
                awardArtifacts(activePlayers.getFirst(), artifactsOnPath);
            }
        }
    }

    /**
     * Builds and shuffles the deck for the current round.
     */
    protected List<Card> createRoundDeck() {
        addNextArtifactToDeck();
        List<Card> deck = buildDeck();
        shuffleDeck(deck);
        return deck;
    }

    /**
     * Hook for subclasses to react when a card is revealed.
     */
    protected void onCardRevealed(Card card,
                                  int turnNumber,
                                  int templeTreasure,
                                  Map<Hazard, Integer> hazardCounts,
                                  int activePlayers,
                                  int artifactsOnPath) {
    }

    /**
     * Hook for subclasses to react when a round ends due to a repeated hazard.
     */
    protected void onRoundEndedByHazard(Hazard hazard, Map<Hazard, Integer> hazardCounts) {
    }

    /**
     * Returns a defensive snapshot of hazard counts.
     */
    protected Map<Hazard, Integer> snapshotHazards(Map<Hazard, Integer> hazardCounts) {
        return Collections.unmodifiableMap(new EnumMap<>(hazardCounts));
    }

    /**
     * Adds the next round's artifact card to the deck pool.
     */
    protected void addNextArtifactToDeck() {
        if (nextArtifactIndex < TOTAL_ARTIFACTS) {
            remainingArtifacts.add(Card.artifact(nextArtifactIndex + 1));
            nextArtifactIndex++;
        }
    }

    /**
     * Removes a hazard card copy from future rounds.
     */
    protected void removeHazardCopy(Hazard hazard) {
        int remaining = hazardCopiesRemaining.getOrDefault(hazard, 0);
        if (remaining > 0) {
            hazardCopiesRemaining.put(hazard, remaining - 1);
        }
    }

    /**
     * Removes unclaimed artifacts from the game after a hazard ends the round.
     */
    protected void removeArtifactsFromGame(List<Card> artifactsOnPath) {
        remainingArtifacts.removeAll(artifactsOnPath);
        artifactsOnPath.clear();
    }

    private void awardArtifacts(Player player, List<Card> artifactsOnPath) {
        for (int i = 0; i < artifactsOnPath.size(); i++) {
            int value = artifactsClaimed < ARTIFACT_LOW_COUNT ? ARTIFACT_LOW_VALUE : ARTIFACT_HIGH_VALUE;
            player.claimArtifact(value);
            artifactsClaimed++;
        }
        remainingArtifacts.removeAll(artifactsOnPath);
        artifactsOnPath.clear();
    }

    private void redistributeTreasureRemainder(List<Card> treasureCardsOnPath, int remainder) {
        for (Card treasureCard : treasureCardsOnPath) {
            treasureCard.setRemainingTreasure(0);
        }
        if (!treasureCardsOnPath.isEmpty()) {
            treasureCardsOnPath.getLast().setRemainingTreasure(remainder);
        }
    }


    /**
     * Builds a fresh deck with configured hazards and treasure values.
     */
    protected List<Card> buildDeck() {
        List<Card> deck = new ArrayList<>();
        for (Hazard hazard : Hazard.values()) {
            int copies = hazardCopiesRemaining.getOrDefault(hazard, hazardCopies);
            for (int i = 0; i < copies; i++) {
                deck.add(Card.hazard(hazard));
            }
        }
        deck.addAll(treasureCards);
        deck.addAll(remainingArtifacts);
        return deck;
    }

    /**
     * Shuffles the deck using the configured RNG.
     */
    protected void shuffleDeck(List<Card> deck) {
        Collections.shuffle(deck, random);
    }

    /**
     * Returns total treasure for each player after the game completes.
     */
    public Map<Player, Integer> getScores() {
        Map<Player, Integer> scores = new HashMap<>();
        for (Player player : players) {
            scores.put(player, player.getTotalTreasure());
        }
        return scores;
    }
}
