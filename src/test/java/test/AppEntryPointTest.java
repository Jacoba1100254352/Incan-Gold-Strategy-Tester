package test;

import com.google.gson.JsonParser;
import client.analysis.StrategyInteractionEvaluator;
import client.analysis.StrategyRatings;
import client.app.IncanGoldTest;
import client.app.NeuralNetworkTrainer;
import client.app.StrategyValidationRunner;
import client.ml.NeuralNetworkModel;
import client.ml.RoundStateVectorizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppEntryPointTest {
    @TempDir
    private Path tempDir;

    @Test
    void strategySweepCliAcceptsMinimalSeededArgumentsAndWritesReports() throws Exception {
        Path previousRatingsPath = StrategyRatings.getRatingsPath();
        Path previousInteractionPath = StrategyInteractionEvaluator.getOutputPath();
        Path ratingsPath = tempDir.resolve("strategy-ratings.json");
        Path interactionPath = tempDir.resolve("strategy-interactions.json");

        try {
            StrategyRatings.setRatingsPath(ratingsPath);
            StrategyInteractionEvaluator.setOutputPath(interactionPath);

            IncanGoldTest.main(new String[] {
                    "1", "1", "2", "1", "false", "12345", "false", "true", "false"
            });

            assertTrue(Files.isRegularFile(ratingsPath));
            var ratings = JsonParser.parseString(Files.readString(ratingsPath)).getAsJsonObject();
            assertEquals(
                    "strategy-test seed=12345 repeats=1 simulations=1 players=2 paired=true catalog=default",
                    ratings.get("source").getAsString()
            );
            assertTrue(!ratings.get("historyBlended").getAsBoolean());
            assertTrue(Files.isRegularFile(interactionPath));
            assertTrue(Files.readString(interactionPath).contains("\"seed\":"));
        } finally {
            StrategyRatings.setRatingsPath(previousRatingsPath);
            StrategyInteractionEvaluator.setOutputPath(previousInteractionPath);
        }
    }

    @Test
    void neuralTrainerCliWritesLoadableModelForSmallSeededRun() throws Exception {
        Path modelPath = tempDir.resolve("strategy-net.json");

        NeuralNetworkTrainer.main(new String[] {
                "1",
                "1",
                "1",
                "1",
                "1",
                "2",
                "0.01",
                "0.4",
                "easy",
                modelPath.toString(),
                "false",
                "1",
                "12345"
        });

        NeuralNetworkModel model = NeuralNetworkModel.load(modelPath);
        double probability = model.predict(new double[RoundStateVectorizer.featureCount()]);
        assertTrue(probability >= 0.0 && probability <= 1.0);
    }

    @Test
    void twoStageValidationCliAcceptsASeededMinimalRun() {
        StrategyValidationRunner.main(new String[] {
                "1", "1", "2", "1", "1", "2", "12345"
        });
    }
}
