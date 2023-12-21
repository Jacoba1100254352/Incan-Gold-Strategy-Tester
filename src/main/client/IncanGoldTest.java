package client;

import algorithm.RiskAverseStrategy;
import model.*;

import java.util.ArrayList;
import java.util.List;

public class IncanGoldTest {
    public static void main(String[] args) {
        List<Player> players = new ArrayList<>();
        players.add(new Player(new RiskAverseStrategy()));
        // Add more players with different strategies

        Game game = new Game(players);

        game.playRound();

        System.out.println("Scores: " + game.getScores());
    }
}
