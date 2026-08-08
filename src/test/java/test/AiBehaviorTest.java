package test;

import algorithm.AlwaysContinueStrategy;
import algorithm.LeaveAfterTurnsStrategy;
import algorithm.Strategy;
import client.ai.AIDifficulty;
import client.ai.AdaptiveAIStrategy;
import client.ai.NeuralNetworkStrategy;
import client.ai.StrategyAdvisor;
import client.analysis.StrategyEvaluator;
import client.ml.NeuralNetworkModel;
import model.Hazard;
import model.RoundState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiBehaviorTest {
    @TempDir
    private Path tempDir;

    private final RoundState state = new RoundState(1, 2, 0, 0, new EnumMap<>(Hazard.class), 0);

    @Test
    void difficultyParsingAcceptsNumbersNamesAndUnknowns() {
        assertEquals(AIDifficulty.EASY, AIDifficulty.fromInput("1"));
        assertEquals(AIDifficulty.EASY, AIDifficulty.fromInput(" EASY "));
        assertEquals(AIDifficulty.MEDIUM, AIDifficulty.fromInput("2"));
        assertEquals(AIDifficulty.HARD, AIDifficulty.fromInput("hard"));
        assertEquals(AIDifficulty.MEDIUM, AIDifficulty.fromInput("unknown"));
        assertEquals(AIDifficulty.MEDIUM, AIDifficulty.fromInput(null));
    }

    @Test
    void strategyAdvisorChoosesBestAvailableDecision() {
        StrategyAdvisor advisor = new StrategyAdvisor(List.of(
                score("continue", AlwaysContinueStrategy::new, 10.0),
                score("leave", () -> new LeaveAfterTurnsStrategy(1), 12.0)
        ));

        StrategyAdvisor.Decision decision = advisor.decide(state);

        assertFalse(decision.shouldContinue);
        assertEquals("leave", decision.strategyName);
        assertEquals(12.0, decision.score);
    }

    @Test
    void strategyAdvisorHandlesAllContinueAndAllLeaveCases() {
        StrategyAdvisor allContinue = new StrategyAdvisor(List.of(
                score("continue", AlwaysContinueStrategy::new, 10.0)
        ));
        assertTrue(allContinue.decide(state).shouldContinue);

        StrategyAdvisor allLeave = new StrategyAdvisor(List.of(
                score("leave", () -> new LeaveAfterTurnsStrategy(1), 10.0)
        ));
        assertFalse(allLeave.decide(state).shouldContinue);

        assertThrows(NullPointerException.class, () -> new StrategyAdvisor(null));
        assertThrows(IllegalArgumentException.class, () -> new StrategyAdvisor(List.of()));
        assertThrows(NullPointerException.class,
                () -> StrategyAdvisor.buildDefault(null, 2, new Random(0)));
        assertThrows(NullPointerException.class,
                () -> StrategyAdvisor.buildDefault(AIDifficulty.EASY, 2, null));
    }

    @Test
    void adaptiveAiDelegatesToAdvisorDecision() {
        StrategyAdvisor advisor = new StrategyAdvisor(List.of(
                score("leave", () -> new LeaveAfterTurnsStrategy(1), 10.0)
        ));
        AdaptiveAIStrategy strategy = new AdaptiveAIStrategy("AI", advisor, false);

        assertFalse(strategy.shouldContinue(state));
        assertThrows(NullPointerException.class, () -> new AdaptiveAIStrategy("AI", null, false));
    }

    @Test
    void neuralStrategyUsesFallbackWhenModelCannotLoad() {
        NeuralNetworkStrategy withFallback = new NeuralNetworkStrategy(
                "AI",
                tempDir.resolve("missing.json"),
                0.5,
                new AlwaysContinueStrategy());
        NeuralNetworkStrategy withoutFallback = new NeuralNetworkStrategy(
                "AI",
                tempDir.resolve("missing.json"),
                0.5,
                null);

        assertTrue(withFallback.shouldContinue(state));
        assertFalse(withoutFallback.shouldContinue(state));
    }

    @Test
    void neuralStrategyAppliesThresholdToLoadedModel() throws IOException {
        Path modelPath = tempDir.resolve("model.json");
        NeuralNetworkModel.initialize(client.ml.RoundStateVectorizer.featureCount(), 2, new Random(0)).save(modelPath);

        NeuralNetworkStrategy lowThreshold = new NeuralNetworkStrategy("AI", modelPath, 0.0, null);
        NeuralNetworkStrategy highThreshold = new NeuralNetworkStrategy("AI", modelPath, 1.0, null);

        assertTrue(lowThreshold.shouldContinue(state));
        assertFalse(highThreshold.shouldContinue(state));
        assertThrows(NullPointerException.class,
                () -> new NeuralNetworkStrategy(null, modelPath, 0.5, null));
        assertThrows(NullPointerException.class,
                () -> new NeuralNetworkStrategy("AI", null, 0.5, null));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralNetworkStrategy("AI", modelPath, -0.1, null));
        assertThrows(IllegalArgumentException.class,
                () -> new NeuralNetworkStrategy("AI", modelPath, 1.1, null));
    }

    private static StrategyEvaluator.StrategyScore score(String name,
                                                         java.util.function.Supplier<Strategy> factory,
                                                         double average) {
        StrategyEvaluator.StrategyScore score = new StrategyEvaluator.StrategyScore(name, factory);
        score.average = average;
        return score;
    }
}
