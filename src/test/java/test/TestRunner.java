package test;


import algorithm.AlwaysContinueStrategy;
import algorithm.ArtifactChaserStrategy;
import algorithm.ArtifactOpportunistStrategy;
import algorithm.ArtifactSoloExitStrategy;
import algorithm.ArtifactValueRiskStrategy;
import algorithm.LeaveAfterHazardsOrTurnsStrategy;
import algorithm.LeaveAfterHazardsStrategy;
import algorithm.LeaveAfterHazardsWithMemoryStrategy;
import algorithm.LeaveAfterTempleTreasureStrategy;
import algorithm.LeaveAfterTreasureOrHazardsStrategy;
import algorithm.LeaveAfterTreasureOrTurnsStrategy;
import algorithm.LeaveAfterTreasureStrategy;
import algorithm.LeaveAfterTurnsStrategy;
import algorithm.LeaveWhenSoloStrategy;
import algorithm.RiskAverseStrategy;
import algorithm.SwitchAfterHazardsStrategy;
import algorithm.SwitchAfterHazardsForTurnsStrategy;
import client.analysis.StrategyRatings;
import model.Card;
import model.Game;
import model.Hazard;
import model.Player;
import model.RoundState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TestRunner {
    public static void main(String[] args) {
        testCard();
        testRoundState();
        testPlayer();
        testStrategies();
        testArtifactStrategies();
        testGameHazardEndsRound();
        testGameLeavingAndTempleRemainder();
        testArtifactClaim();
        testStrategyRatings();
        testStrategyRatingsWithInteractions();
        System.out.println("All tests passed.");
    }

    private static void testCard() {
        Card treasure = Card.treasure(7);
        assertEquals(Card.Type.TREASURE, treasure.getType(), "treasure type");
        assertEquals(7, treasure.getTreasureValue(), "treasure value");
        assertEquals(null, treasure.getHazard(), "treasure hazard");

        Card hazard = Card.hazard(Hazard.SNAKE);
        assertEquals(Card.Type.HAZARD, hazard.getType(), "hazard type");
        assertEquals(0, hazard.getTreasureValue(), "hazard treasure value");
        assertEquals(Hazard.SNAKE, hazard.getHazard(), "hazard value");

        Card artifact = Card.artifact(1);
        assertEquals(Card.Type.ARTIFACT, artifact.getType(), "artifact type");
        assertEquals(0, artifact.getTreasureValue(), "artifact treasure value");
        assertEquals(null, artifact.getHazard(), "artifact hazard");
        assertEquals(1, artifact.getArtifactId(), "artifact id");
    }

    private static void testRoundState() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        counts.put(Hazard.SNAKE, 1);
        counts.put(Hazard.SPIDER, 2);
        Map<Hazard, Integer> copies = new EnumMap<>(Hazard.class);
        copies.put(Hazard.SNAKE, 2);
        copies.put(Hazard.SPIDER, 1);
        RoundState state = new RoundState(3, 4, 5, 6, counts, copies, 0, 2);

        assertEquals(3, state.getTurnNumber(), "turn number");
        assertEquals(4, state.getActivePlayers(), "active players");
        assertEquals(5, state.getTempleTreasure(), "temple treasure");
        assertEquals(6, state.getRoundTreasure(), "round treasure");
        assertEquals(0, state.getArtifactsOnPath(), "artifacts on path");
        assertEquals(1, state.getHazardCount(Hazard.SNAKE), "snake count");
        assertEquals(2, state.getHazardCount(Hazard.SPIDER), "spider count");
        assertEquals(0, state.getHazardCount(Hazard.TRAP), "trap count default");
        assertEquals(3, state.getTotalHazardsRevealed(), "total hazards");
        assertEquals(2, state.getHazardCopiesRemaining(Hazard.SNAKE), "snake copies remaining");
        assertEquals(0, state.getHazardCopiesRemaining(Hazard.TRAP), "trap copies remaining default");
        assertEquals(2, state.getArtifactsClaimed(), "artifacts claimed");
    }

    private static void testPlayer() {
        Player player = new Player(new AlwaysContinueStrategy());
        player.startRound();
        player.collect(3);
        player.collect(2);
        assertEquals(5, player.getRoundTreasure(), "round treasure after collect");
        player.leaveRound(1);
        assertEquals(6, player.getTotalTreasure(), "total after leave round");
        assertEquals(0, player.getRoundTreasure(), "round reset after leave");
        player.collect(4);
        player.loseRoundTreasure();
        assertEquals(0, player.getRoundTreasure(), "round reset after loss");
    }

    private static void testStrategies() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        RoundState base = new RoundState(2, 3, 4, 5, counts, 0);

        assertTrue(new AlwaysContinueStrategy().shouldContinue(base), "always continue");
        assertTrue(new RiskAverseStrategy().shouldContinue(base), "risk averse no hazards");
        assertTrue(new LeaveAfterHazardsStrategy(2).shouldContinue(base), "hazards under limit");
        assertTrue(new LeaveAfterTurnsStrategy(3).shouldContinue(base), "turns under limit");
        assertTrue(new LeaveAfterTreasureStrategy(6).shouldContinue(base), "treasure under limit");
        assertTrue(new LeaveAfterTempleTreasureStrategy(5).shouldContinue(base), "temple under limit");
        assertTrue(new LeaveAfterHazardsOrTurnsStrategy(2, 3).shouldContinue(base), "hazards and turns under");
        assertTrue(new LeaveAfterTreasureOrHazardsStrategy(6, 2).shouldContinue(base), "treasure and hazards under");
        assertTrue(new LeaveAfterTreasureOrTurnsStrategy(6, 3).shouldContinue(base), "treasure and turns under");
        assertTrue(new LeaveWhenSoloStrategy().shouldContinue(base), "not solo yet");

        counts.put(Hazard.SNAKE, 1);
        RoundState oneHazard = new RoundState(2, 3, 4, 5, counts, 0);
        assertTrue(new LeaveAfterHazardsStrategy(2).shouldContinue(oneHazard), "hazards at 1");
        assertFalse(new RiskAverseStrategy().shouldContinue(oneHazard), "risk averse after hazard");

        counts.put(Hazard.SPIDER, 1);
        RoundState twoHazards = new RoundState(2, 3, 4, 5, counts, 0);
        assertFalse(new LeaveAfterHazardsStrategy(2).shouldContinue(twoHazards), "hazards at limit");
        assertFalse(new LeaveAfterHazardsOrTurnsStrategy(2, 3).shouldContinue(twoHazards), "hazards at limit");
        assertFalse(new LeaveAfterTreasureOrHazardsStrategy(6, 2).shouldContinue(twoHazards), "hazards at limit");

        RoundState turnLimit = new RoundState(3, 3, 4, 5, counts, 0);
        assertFalse(new LeaveAfterTurnsStrategy(3).shouldContinue(turnLimit), "turns at limit");
        assertFalse(new LeaveAfterHazardsOrTurnsStrategy(3, 3).shouldContinue(turnLimit), "turns at limit");
        assertFalse(new LeaveAfterTreasureOrTurnsStrategy(6, 3).shouldContinue(turnLimit), "turns at limit (treasure or turns)");

        RoundState treasureLimit = new RoundState(2, 3, 4, 6, counts, 0);
        assertFalse(new LeaveAfterTreasureStrategy(6).shouldContinue(treasureLimit), "treasure at limit");
        assertFalse(new LeaveAfterTreasureOrHazardsStrategy(6, 3).shouldContinue(treasureLimit), "treasure at limit");
        assertFalse(new LeaveAfterTreasureOrTurnsStrategy(6, 3).shouldContinue(treasureLimit), "treasure or turns at limit");

        RoundState templeLimit = new RoundState(2, 3, 5, 5, counts, 0);
        assertFalse(new LeaveAfterTempleTreasureStrategy(5).shouldContinue(templeLimit), "temple at limit");

        RoundState solo = new RoundState(2, 1, 0, 0, counts, 0);
        assertFalse(new LeaveWhenSoloStrategy().shouldContinue(solo), "leave when solo");

        SwitchAfterHazardsStrategy switchStrategy = new SwitchAfterHazardsStrategy(
                1,
                new AlwaysContinueStrategy(),
                new LeaveAfterTurnsStrategy(2)
        );
        RoundState beforeSwitch = new RoundState(10, 2, 0, 0, new EnumMap<>(Hazard.class), 0);
        assertTrue(switchStrategy.shouldContinue(beforeSwitch), "before switch uses always continue");
        RoundState afterSwitch = new RoundState(2, 2, 0, 0, counts, 0);
        assertFalse(switchStrategy.shouldContinue(afterSwitch), "after switch uses turn strategy");

        SwitchAfterHazardsForTurnsStrategy stayAfterHazard = new SwitchAfterHazardsForTurnsStrategy(1, 2);
        RoundState triggerHazard = new RoundState(3, 2, 0, 0, counts, 0);
        assertTrue(stayAfterHazard.shouldContinue(triggerHazard), "stay after hazard trigger");
        RoundState oneTurnLater = new RoundState(4, 2, 0, 0, counts, 0);
        assertTrue(stayAfterHazard.shouldContinue(oneTurnLater), "stay after hazard extra turn");
        RoundState twoTurnsLater = new RoundState(5, 2, 0, 0, counts, 0);
        assertFalse(stayAfterHazard.shouldContinue(twoTurnsLater), "leave after extra turns");
        RoundState newRoundReset = new RoundState(1, 2, 0, 0, new EnumMap<>(Hazard.class), 0);
        assertTrue(stayAfterHazard.shouldContinue(newRoundReset), "reset stay after hazard");
    }

    private static void testGameHazardEndsRound() {
        List<Card> deck = Arrays.asList(
                Card.treasure(5),
                Card.hazard(Hazard.SNAKE),
                Card.treasure(4),
                Card.hazard(Hazard.SNAKE)
        );
        List<Player> players = new ArrayList<>();
        players.add(new Player(new AlwaysContinueStrategy()));
        players.add(new Player(new AlwaysContinueStrategy()));

        Game game = new FixedDeckGame(players, deck);
        game.playGame();

        assertEquals(0, players.get(0).getTotalTreasure(), "hazard end player 1");
        assertEquals(0, players.get(1).getTotalTreasure(), "hazard end player 2");
    }

    private static void testGameLeavingAndTempleRemainder() {
        List<Card> deck = Arrays.asList(
                Card.treasure(5),
                Card.hazard(Hazard.SNAKE),
                Card.treasure(4),
                Card.hazard(Hazard.SPIDER)
        );
        List<Player> players = new ArrayList<>();
        players.add(new Player(new LeaveAfterHazardsStrategy(1)));
        players.add(new Player(new AlwaysContinueStrategy()));

        Game game = new FixedDeckGame(players, deck);
        game.playGame();

        assertEquals(3, players.get(0).getTotalTreasure(), "leaver total");
        assertEquals(6, players.get(1).getTotalTreasure(), "stayer total");
    }

    private static void testArtifactClaim() {
        List<Card> deck = Arrays.asList(
                Card.artifact(1),
                Card.hazard(Hazard.SNAKE),
                Card.hazard(Hazard.SNAKE)
        );
        List<Player> players = new ArrayList<>();
        players.add(new Player(new LeaveAfterTurnsStrategy(1)));
        players.add(new Player(new AlwaysContinueStrategy()));

        Game game = new FixedDeckGame(players, deck);
        game.playGame();

        assertEquals(5, players.get(0).getTotalTreasure(), "artifact claimant total");
        assertEquals(1, players.get(0).getArtifactsClaimed(), "artifact claimant count");
        assertEquals(0, players.get(1).getTotalTreasure(), "artifact non-claimant total");
    }

    private static void testArtifactStrategies() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        Map<Hazard, Integer> copies = createHazardCopies(3);

        RoundState soloArtifact = new RoundState(1, 1, 0, 0, counts, copies, 1, 0);
        assertFalse(new ArtifactSoloExitStrategy().shouldContinue(soloArtifact), "solo artifact exit");

        RoundState crowdedArtifact = new RoundState(1, 3, 0, 0, counts, copies, 1, 0);
        assertTrue(new ArtifactSoloExitStrategy().shouldContinue(crowdedArtifact), "crowded artifact continue");

        ArtifactOpportunistStrategy opportunist = new ArtifactOpportunistStrategy(
                1,
                2,
                5,
                1,
                new LeaveAfterTurnsStrategy(3)
        );
        RoundState opportunistLeave = new RoundState(2, 2, 0, 5, counts, copies, 1, 0);
        assertFalse(opportunist.shouldContinue(opportunistLeave), "opportunist leaves on treasure");

        RoundState opportunistFallback = new RoundState(3, 2, 0, 0, counts, copies, 0, 0);
        assertFalse(opportunist.shouldContinue(opportunistFallback), "opportunist leaves on fallback");

        Map<Hazard, Integer> hazardCounts = new EnumMap<>(Hazard.class);
        hazardCounts.put(Hazard.SNAKE, 1);
        RoundState opportunistHazard = new RoundState(2, 2, 0, 0, hazardCounts, copies, 1, 0);
        assertFalse(opportunist.shouldContinue(opportunistHazard), "opportunist leaves on hazard");

        RoundState opportunistStay = new RoundState(2, 3, 0, 6, counts, copies, 1, 0);
        assertTrue(opportunist.shouldContinue(opportunistStay), "opportunist waits with many players");

        ArtifactValueRiskStrategy riskStrategy = new ArtifactValueRiskStrategy(8, 2, 2, new LeaveAfterTurnsStrategy(7));
        RoundState riskLeave = new RoundState(2, 2, 0, 3, hazardCounts, copies, 1, 0);
        assertFalse(riskStrategy.shouldContinue(riskLeave), "risk strategy leaves when bank and risk align");

        RoundState riskStay = new RoundState(2, 3, 0, 3, hazardCounts, copies, 1, 0);
        assertTrue(riskStrategy.shouldContinue(riskStay), "risk strategy continues with many players");

        ArtifactChaserStrategy chaser = new ArtifactChaserStrategy(7, 4, 1, 1, 2);
        RoundState chaserBaseLeave = new RoundState(7, 2, 0, 0, counts, copies, 0, 0);
        assertFalse(chaser.shouldContinue(chaserBaseLeave), "chaser leaves at base limits");

        RoundState chaserArtifactStay = new RoundState(7, 2, 0, 0, hazardCounts, copies, 1, 0);
        assertTrue(chaser.shouldContinue(chaserArtifactStay), "chaser stays longer with artifact");

        RoundState chaserSolo = new RoundState(3, 1, 0, 0, counts, copies, 1, 0);
        assertFalse(chaser.shouldContinue(chaserSolo), "chaser leaves when solo with artifact");

        LeaveAfterHazardsWithMemoryStrategy memoryStrategy =
                new LeaveAfterHazardsWithMemoryStrategy(2, 1, 1);
        Map<Hazard, Integer> lowCopies = createHazardCopies(3);
        lowCopies.put(Hazard.SNAKE, 1);
        lowCopies.put(Hazard.SPIDER, 1);
        Map<Hazard, Integer> twoHazards = new EnumMap<>(Hazard.class);
        twoHazards.put(Hazard.SNAKE, 1);
        twoHazards.put(Hazard.SPIDER, 1);
        RoundState memoryContinue = new RoundState(3, 2, 0, 0, twoHazards, lowCopies, 0, 0);
        assertTrue(memoryStrategy.shouldContinue(memoryContinue), "memory strategy continues on low copies");

        RoundState memoryLeave = new RoundState(3, 2, 0, 0, twoHazards, copies, 0, 0);
        assertFalse(memoryStrategy.shouldContinue(memoryLeave), "memory strategy leaves on normal copies");
    }

    private static void testStrategyRatings() {
        Path ratingsPath = Paths.get("results", "strategy-ratings.json");
        String previousContent = null;
        boolean hadExisting = Files.exists(ratingsPath);
        if (hadExisting) {
            previousContent = readFileContent(ratingsPath, "ratings backup");
        }

        try {
            deleteFileIfExists(ratingsPath, "ratings cleanup");

            List<StrategyRatings.StrategyPerformance> firstRun = new ArrayList<>();
            firstRun.add(new StrategyRatings.StrategyPerformance("Alpha", 10.0, 3, 4));
            firstRun.add(new StrategyRatings.StrategyPerformance("Beta", 5.0, 1, 4));
            StrategyRatings.updateRatings(firstRun, "test-run-1");

            String firstJson = readFileContent(ratingsPath, "first ratings");
            assertNear(5.0, extractDoubleField(firstJson, "Alpha", "rating"), 1e-6, "alpha rating first run");
            assertNear(0.0, extractDoubleField(firstJson, "Beta", "rating"), 1e-6, "beta rating first run");
            assertNear(75.0, extractDoubleField(firstJson, "Alpha", "winRate"), 1e-6, "alpha win rate first run");
            assertNear(25.0, extractDoubleField(firstJson, "Beta", "winRate"), 1e-6, "beta win rate first run");
            assertEquals(1, extractIntField(firstJson, "Alpha", "ratingRank"), "alpha rank first run");
            assertEquals(2, extractIntField(firstJson, "Beta", "ratingRank"), "beta rank first run");

            List<StrategyRatings.StrategyPerformance> secondRun = new ArrayList<>();
            secondRun.add(new StrategyRatings.StrategyPerformance("Beta", 12.0, 3, 4));
            secondRun.add(new StrategyRatings.StrategyPerformance("Alpha", 6.0, 1, 4));
            StrategyRatings.updateRatings(secondRun, "test-run-2");

            String secondJson = readFileContent(ratingsPath, "second ratings");
            assertNear(2.5, extractDoubleField(secondJson, "Alpha", "rating"), 1e-6, "alpha rating second run");
            assertNear(2.5, extractDoubleField(secondJson, "Beta", "rating"), 1e-6, "beta rating second run");
            assertNear(50.0, extractDoubleField(secondJson, "Alpha", "winRate"), 1e-6, "alpha win rate second run");
            assertNear(50.0, extractDoubleField(secondJson, "Beta", "winRate"), 1e-6, "beta win rate second run");
            assertEquals(1, extractIntField(secondJson, "Alpha", "ratingRank"), "alpha rank second run");
            assertEquals(2, extractIntField(secondJson, "Beta", "ratingRank"), "beta rank second run");
        } finally {
            if (hadExisting) {
                writeFileContent(ratingsPath, previousContent, "restore ratings");
            } else {
                deleteFileIfExists(ratingsPath, "remove ratings");
            }
        }
    }

    private static void testStrategyRatingsWithInteractions() {
        Path ratingsPath = Paths.get("results", "strategy-ratings.json");
        String previousContent = null;
        boolean hadExisting = Files.exists(ratingsPath);
        if (hadExisting) {
            previousContent = readFileContent(ratingsPath, "ratings backup interactions");
        }

        try {
            deleteFileIfExists(ratingsPath, "ratings interactions cleanup");

            List<StrategyRatings.StrategyPerformance> performances = new ArrayList<>();
            performances.add(new StrategyRatings.StrategyPerformance("Alpha", 10.0, 4, 4));
            performances.add(new StrategyRatings.StrategyPerformance("Beta", 8.0, 2, 4));

            Map<String, StrategyRatings.InteractionPerformance> interactions = new HashMap<>();
            interactions.put("Alpha", new StrategyRatings.InteractionPerformance("Alpha", 0.0, 0.0));
            interactions.put("Beta", new StrategyRatings.InteractionPerformance("Beta", 20.0, 100.0));

            StrategyRatings.updateRatings(performances, "test-interactions", interactions, true);

            String json = readFileContent(ratingsPath, "interaction ratings");
            assertNear(0.0, extractDoubleField(json, "Alpha", "rating"), 1e-6, "alpha rating interaction");
            assertNear(5.0, extractDoubleField(json, "Beta", "rating"), 1e-6, "beta rating interaction");
            assertNear(30.0, extractDoubleField(json, "Alpha", "winRate"), 1e-6, "alpha win rate interaction");
            assertNear(85.0, extractDoubleField(json, "Beta", "winRate"), 1e-6, "beta win rate interaction");
            assertNear(100.0, extractDoubleField(json, "Alpha", "sweepWinRate"), 1e-6,
                    "alpha sweep win rate interaction");
            assertNear(50.0, extractDoubleField(json, "Beta", "sweepWinRate"), 1e-6,
                    "beta sweep win rate interaction");
            assertNear(0.0, extractDoubleField(json, "Alpha", "interactionWinRate"), 1e-6,
                    "alpha interaction win rate");
            assertNear(100.0, extractDoubleField(json, "Beta", "interactionWinRate"), 1e-6,
                    "beta interaction win rate");
            assertEquals(2, extractIntField(json, "Alpha", "ratingRank"), "alpha rank interaction");
            assertEquals(1, extractIntField(json, "Beta", "ratingRank"), "beta rank interaction");
        } finally {
            if (hadExisting) {
                writeFileContent(ratingsPath, previousContent, "restore ratings interactions");
            } else {
                deleteFileIfExists(ratingsPath, "remove ratings interactions");
            }
        }
    }

    private static class FixedDeckGame extends Game {
        private final List<Card> fixedDeck;

        private FixedDeckGame(List<Player> players, List<Card> fixedDeck) {
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

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + " but got " + actual + ": " + message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but got " + actual + ": " + message);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertNear(double expected, double actual, double epsilon, String message) {
        if (Math.abs(expected - actual) > epsilon) {
            throw new AssertionError("Expected " + expected + " but got " + actual + ": " + message);
        }
    }

    private static String readFileContent(Path path, String message) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Failed to read file for " + message + ": " + e.getMessage());
        }
    }

    private static void writeFileContent(Path path, String content, String message) {
        try {
            Files.writeString(path, content == null ? "" : content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Failed to write file for " + message + ": " + e.getMessage());
        }
    }

    private static void deleteFileIfExists(Path path, String message) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            throw new AssertionError("Failed to delete file for " + message + ": " + e.getMessage());
        }
    }

    private static Map<Hazard, Integer> createHazardCopies(int copies) {
        Map<Hazard, Integer> result = new EnumMap<>(Hazard.class);
        for (Hazard hazard : Hazard.values()) {
            result.put(hazard, copies);
        }
        return result;
    }

    private static String extractEntry(String json, String name) {
        String pattern = "\\{\\s*\"name\"\\s*:\\s*\"" + Pattern.quote(name) + "\"(.*?)\\n\\s*\\}";
        Matcher matcher = Pattern.compile(pattern, Pattern.DOTALL).matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Could not find entry for " + name);
        }
        return matcher.group();
    }

    private static double extractDoubleField(String json, String name, String field) {
        String entry = extractEntry(json, name);
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([0-9.]+)")
                .matcher(entry);
        if (!matcher.find()) {
            throw new AssertionError("Missing field " + field + " for " + name);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static int extractIntField(String json, String name, String field) {
        String entry = extractEntry(json, name);
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(\\d+)")
                .matcher(entry);
        if (!matcher.find()) {
            throw new AssertionError("Missing field " + field + " for " + name);
        }
        return Integer.parseInt(matcher.group(1));
    }
}
