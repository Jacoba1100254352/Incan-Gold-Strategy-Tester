package model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Game {
    private static final int DEFAULT_ROUNDS = 5;
    private static final int DEFAULT_HAZARD_COPIES = 3;
    private static final List<Integer> DEFAULT_TREASURE_VALUES = Arrays.asList(
            1, 2, 3, 4, 5,
            6, 7, 8, 9, 10,
            11, 12, 13, 14, 15
    );

    private final List<Player> players;
    private final int rounds;
    private final int hazardCopies;
    private final List<Integer> treasureValues;
    private final Random random;

    public Game(List<Player> players) {
        this(players, DEFAULT_ROUNDS, DEFAULT_HAZARD_COPIES, DEFAULT_TREASURE_VALUES, new Random());
    }

    public Game(List<Player> players,
                int rounds,
                int hazardCopies,
                List<Integer> treasureValues,
                Random random) {
        this.players = players;
        this.rounds = rounds;
        this.hazardCopies = hazardCopies;
        this.treasureValues = new ArrayList<>(treasureValues);
        this.random = random;
    }

    public void playGame() {
        for (int round = 0; round < rounds; round++) {
            playRound();
        }
    }

    private void playRound() {
        for (Player player : players) {
            player.startRound();
        }

        List<Card> deck = buildDeck();
        shuffleDeck(deck);

        List<Player> activePlayers = new ArrayList<>(players);
        Map<Hazard, Integer> hazardCounts = new EnumMap<>(Hazard.class);
        int templeTreasure = 0;
        int turnNumber = 0;

        for (Card card : deck) {
            if (activePlayers.isEmpty()) {
                break;
            }

            turnNumber++;

            if (card.getType() == Card.Type.TREASURE) {
                int value = card.getTreasureValue();
                int share = value / activePlayers.size();
                int remainder = value % activePlayers.size();
                for (Player player : activePlayers) {
                    player.collect(share);
                }
                templeTreasure += remainder;
            } else {
                Hazard hazard = card.getHazard();
                int count = hazardCounts.getOrDefault(hazard, 0) + 1;
                hazardCounts.put(hazard, count);
                if (count >= 2) {
                    for (Player player : activePlayers) {
                        player.loseRoundTreasure();
                    }
                    return;
                }
            }

            List<Player> leavingPlayers = new ArrayList<>();
            for (Player player : activePlayers) {
                RoundState state = new RoundState(
                        turnNumber,
                        activePlayers.size(),
                        templeTreasure,
                        player.getRoundTreasure(),
                        hazardCounts
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
                activePlayers.removeAll(leavingPlayers);
            }
        }

        if (!activePlayers.isEmpty()) {
            int share = templeTreasure / activePlayers.size();
            for (Player player : activePlayers) {
                player.leaveRound(share);
            }
        }
    }

    protected List<Card> buildDeck() {
        List<Card> deck = new ArrayList<>();
        for (Hazard hazard : Hazard.values()) {
            for (int i = 0; i < hazardCopies; i++) {
                deck.add(Card.hazard(hazard));
            }
        }
        for (int value : treasureValues) {
            deck.add(Card.treasure(value));
        }
        return deck;
    }

    protected void shuffleDeck(List<Card> deck) {
        Collections.shuffle(deck, random);
    }

    public Map<Player, Integer> getScores() {
        Map<Player, Integer> scores = new HashMap<>();
        for (Player player : players) {
            scores.put(player, player.getTotalTreasure());
        }
        return scores;
    }
}
