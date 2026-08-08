package test;

import algorithm.AlwaysContinueStrategy;
import client.ml.NeuralNetworkModel;
import client.ml.RoundStateVectorizer;
import client.ml.TrainingSample;
import model.Card;
import model.Game;
import model.Hazard;
import model.Player;
import model.RoundState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCoreTest {
    @TempDir
    private Path tempDir;

    @Test
    void cardFactoriesExposeExpectedState() {
        Card treasure = Card.treasure(7);
        assertEquals(Card.Type.TREASURE, treasure.getType());
        assertEquals(7, treasure.getTreasureValue());
        assertNull(treasure.getHazard());

        Card hazard = Card.hazard(Hazard.SNAKE);
        assertEquals(Card.Type.HAZARD, hazard.getType());
        assertEquals(Hazard.SNAKE, hazard.getHazard());

        Card artifact = Card.artifact(1);
        assertEquals(Card.Type.ARTIFACT, artifact.getType());
        assertEquals(1, artifact.getArtifactId());
    }

    @Test
    void playerFinalStandingUsesArtifactCountAsTheOfficialTiebreaker() {
        Player artifactHolder = new Player(new AlwaysContinueStrategy());
        artifactHolder.collect(5);
        artifactHolder.leaveRound(0);
        artifactHolder.claimArtifact(5);

        Player gemHolder = new Player(new AlwaysContinueStrategy());
        gemHolder.collect(10);
        gemHolder.leaveRound(0);

        assertEquals(10, artifactHolder.getTotalTreasure());
        assertEquals(10, gemHolder.getTotalTreasure());
        assertTrue(artifactHolder.compareFinalStanding(gemHolder) > 0);
        assertTrue(gemHolder.compareFinalStanding(artifactHolder) < 0);
        assertThrows(NullPointerException.class, () -> artifactHolder.compareFinalStanding(null));
    }

    @Test
    void defaultRulesetUsesCorrectTreasureAndArtifactValues() {
        assertEquals(List.of(1, 2, 3, 4, 5, 5, 7, 7, 9, 11, 11, 13, 14, 15),
                Game.defaultTreasureValues());
        assertEquals(5, Game.totalArtifacts());
        assertEquals(5, Game.artifactLowValue());
        assertEquals(10, Game.artifactHighValue());
        assertEquals(3, Game.artifactLowCount());
    }

    @Test
    void roundStateCopiesGenericMapsAndKeepsArtifactDeckContext() {
        Map<Hazard, Integer> counts = new HashMap<>();
        counts.put(Hazard.SNAKE, 1);
        Map<Hazard, Integer> copies = new HashMap<>();
        copies.put(Hazard.SNAKE, 2);

        RoundState state = new RoundState(3, 4, 5, 6, counts, copies, 1, 2, 3);
        counts.put(Hazard.SNAKE, 2);
        copies.put(Hazard.SNAKE, 1);

        assertEquals(1, state.getHazardCount(Hazard.SNAKE));
        assertEquals(2, state.getHazardCopiesRemaining(Hazard.SNAKE));
        assertEquals(3, state.getArtifactsRemainingInDeck());
    }

    @Test
    void modelObjectsRejectInvalidState() {
        assertThrows(IllegalArgumentException.class, () -> Card.treasure(0));
        assertThrows(NullPointerException.class, () -> Card.hazard(null));
        assertThrows(IllegalArgumentException.class, () -> Card.artifact(0));
        assertThrows(IllegalArgumentException.class, () -> Card.treasure(1).setRemainingTreasure(-1));
        assertThrows(NullPointerException.class, () -> new Player(null));

        Player player = new Player(new AlwaysContinueStrategy());
        assertThrows(IllegalArgumentException.class, () -> player.collect(-1));
        assertThrows(IllegalArgumentException.class, () -> player.leaveRound(-1));
        assertThrows(IllegalArgumentException.class, () -> player.claimArtifact(-1));

        assertThrows(IllegalArgumentException.class,
                () -> new RoundState(-1, 1, 0, 0, new EnumMap<>(Hazard.class), 0));
        Map<Hazard, Integer> negativeHazards = new EnumMap<>(Hazard.class);
        negativeHazards.put(Hazard.SNAKE, -1);
        assertThrows(IllegalArgumentException.class,
                () -> new RoundState(1, 1, 0, 0, negativeHazards, 0));
    }

    @Test
    void trainingSamplesDefensivelyCopyFeatures() {
        double[] features = {0.25, 0.5};
        TrainingSample sample = new TrainingSample(features, 1.0);
        features[0] = 0.75;

        assertArrayEquals(new double[]{0.25, 0.5}, sample.features());

        double[] returned = sample.features();
        returned[1] = 0.0;
        assertArrayEquals(new double[]{0.25, 0.5}, sample.features());

        assertThrows(NullPointerException.class, () -> new TrainingSample(null, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new TrainingSample(new double[]{0.0}, -0.1));
    }

    @Test
    void neuralNetworkModelRoundTripsThroughStructuredJson() throws IOException {
        NeuralNetworkModel model = NeuralNetworkModel.initialize(3, 2, new Random(0));
        double[] input = {0.1, 0.2, 0.3};
        double prediction = model.predict(input);

        Path modelPath = tempDir.resolve("strategy-net.json");
        model.save(modelPath);
        NeuralNetworkModel loaded = NeuralNetworkModel.load(modelPath);

        assertEquals(prediction, loaded.predict(input), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> NeuralNetworkModel.initialize(0, 1, new Random(0)));
        assertThrows(NullPointerException.class, () -> RoundStateVectorizer.toFeatures(null));
    }

    @Test
    void neuralNetworkModelRejectsInvalidTrainingAndModelShapes() throws IOException {
        NeuralNetworkModel model = NeuralNetworkModel.initialize(2, 2, new Random(0));
        List<TrainingSample> samples = List.of(new TrainingSample(new double[]{0.0, 1.0}, 1.0));

        assertThrows(NullPointerException.class, () -> NeuralNetworkModel.initialize(2, 2, null));
        assertThrows(NullPointerException.class, () -> model.predict(null));
        assertThrows(IllegalArgumentException.class, () -> model.predict(new double[]{1.0}));
        assertThrows(NullPointerException.class, () -> model.train(null, 1, 1, 0.1, new Random(0)));
        assertThrows(NullPointerException.class, () -> model.train(samples, 1, 1, 0.1, null));
        assertThrows(IllegalArgumentException.class, () -> model.train(samples, 0, 1, 0.1, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> model.train(samples, 1, 0, 0.1, new Random(0)));
        assertThrows(IllegalArgumentException.class, () -> model.train(samples, 1, 1, 0.0, new Random(0)));

        model.train(Collections.emptyList(), 1, 1, 0.1, new Random(0));
        model.train(samples, 1, 1, 0.1, new Random(0));

        Path invalidPath = tempDir.resolve("invalid-model.json");
        Files.writeString(invalidPath,
                "{\"inputSize\":2,\"hiddenSize\":2,\"weights1\":[[0.0]],\"bias1\":[0.0,0.0],"
                        + "\"weights2\":[0.0,0.0],\"bias2\":0.0}",
                StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> NeuralNetworkModel.load(invalidPath));
    }

    @Test
    void vectorizerNormalizesAndClampsStateFeatures() {
        Map<Hazard, Integer> counts = new EnumMap<>(Hazard.class);
        counts.put(Hazard.SNAKE, 3);
        Map<Hazard, Integer> copies = new EnumMap<>(Hazard.class);
        copies.put(Hazard.SNAKE, 5);

        RoundState state = new RoundState(40, 9, 80, 75, counts, copies, 9, 7, 6);
        double[] features = RoundStateVectorizer.toFeatures(state);

        assertEquals(8 + Hazard.values().length * 2, RoundStateVectorizer.featureCount());
        assertEquals(RoundStateVectorizer.featureCount(), features.length);
        assertEquals(1.0, features[0], 1e-12);
        assertEquals(1.0, features[1], 1e-12);
        assertEquals(1.0, features[2], 1e-12);
        assertEquals(1.0, features[3], 1e-12);
        assertEquals(1.0, features[4], 1e-12);
        assertEquals(1.0, features[5], 1e-12);
        assertEquals(1.0, features[6], 1e-12);
        assertTrue(features[7] > 0.0, "total hazards feature should reflect revealed hazards");
    }
}
