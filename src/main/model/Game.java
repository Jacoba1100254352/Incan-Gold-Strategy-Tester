package model;

import java.util.*;

public class Game {
    private final List<Player> players;
    private final List<Hazard> hazards;
    private final Random random;

    public Game(List<Player> players) {
        this.players = players;
        this.random = new Random();
        this.hazards = Arrays.asList(Hazard.values());
    }

    public void playRound() {
        Collections.shuffle(hazards);
        List<Hazard> revealedHazards = new ArrayList<>();
        int treasure = 0;

        for (Hazard hazard : hazards) {
            treasure += random.nextInt(10) + 1; // Random treasure between 1 and 10

            for (Player player : players) {
                if (!player.makeDecision(treasure, revealedHazards)) {
                    player.addTreasure(treasure / players.size());
                    players.remove(player);
                }
            }

            if (players.isEmpty()) {
                break;
            }

            revealedHazards.add(hazard);

            if (Collections.frequency(revealedHazards, hazard) > 1) {
                // Hazard occurred twice, round ends
                break;
            }
        }
    }

    public Map<Player, Integer> getScores() {
        Map<Player, Integer> scores = new HashMap<>();
        for (Player player : players) {
            scores.put(player, player.getTreasure());
        }
        return scores;
    }
}
