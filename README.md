# Incan Gold Strategy Tester

Java simulator and strategy-analysis tooling for the legacy Incan Gold ruleset
implemented by Board Game Arena. That target uses 14 treasure cards
(`1, 2, 3, 4, 5, 5, 7, 7, 9, 11, 11, 13, 14, 15`) and classic artifact scoring
(the first three claimed artifacts are worth 5; the last two are worth 10).
The project uses a Gradle wrapper, so a local Gradle install is not required.

## Requirements

- JDK 21 or newer to compile the project.
- Network access on first build so Gradle can download Maven dependencies.

## Build And Test

For a checkout stored in Dropbox or another synchronized folder, use the local
scratch runner to avoid sending generated classes and reports through the sync
provider:

```sh
./scripts/check-local.sh
```

The equivalent direct Gradle command is:

```sh
./gradlew --no-daemon clean check
```

The `test` task runs the focused JUnit suite. The `check` task also enforces
core JaCoCo coverage thresholds and writes the
HTML report to `build/reports/jacoco/test/html/index.html`. The coverage gate
excludes `client.app` JavaFX/CLI entrypoints because they are smoke-tested
separately and are not stable unit-test targets.

When `scripts/check-local.sh` is used, the same report is written beneath
`${TMPDIR:-/tmp}/incan-gold-strategy-tester/build/`.

## Run The App

```sh
./gradlew run
```

## Strategy Sweep

```sh
./gradlew runSweep --args="20 10000 4 1000 true 12345 true true false false"
```

Arguments are:

1. `repeats`
2. `simulations`
3. `playersPerGame`
4. `matchupSimulations`
5. `includeInteractionRatings`
6. `seed`
7. `runPlayerCountSweep`
8. `runInteractions`
9. `blendHistoricalRatings`
10. `useExpandedValidationCatalog`

Omit the seed to generate one. The CLI prints the seed so the run can be
repeated. Strategies within each sweep receive the same game-seed schedule,
making comparisons independent of catalog ordering. Summaries include
game-level 95% confidence intervals and paired-batch win counts. The two-stage
validator also reports paired-batch confidence intervals for the final winner's
advantage over each other finalist.

The standard player-count sweep covers 3-8 players. You can still run a
targeted nonstandard 1- or 2-player experiment by passing that player count as
argument 3 and disabling argument 7.

Saved ratings represent the current run by default. Set argument 9 to `true`
only when you intentionally want to blend the result with an existing ratings
file.

For a focused four-player screen of the previous treasure/turn optimum, test
the default strategies plus every treasure/turn threshold from 4-12 without
the player-count or interaction passes:

```sh
./gradlew runSweep --args="10 5000 4 100 false 12345 false false false true"
```

For the full two-stage validation workflow, screen the expanded catalog and
then rerun only the top finalists at a higher sample size:

```sh
./gradlew runValidation --args="5 2000 10 20 10000 4 12345"
```

Those arguments are screening repeats, screening simulations, finalist count,
final repeats, final simulations, player count, and seed.

## Charts

Render the bucket chart:

```sh
./gradlew renderBucketChart --args="20 10000 4 8 results/charts/original 12345"
```

Render ratings and interaction charts:

```sh
./gradlew renderStrategyCharts
```

Generated chart images and metadata are written under `results/`.

## Neural Training

```sh
./gradlew trainNeuralNetwork --args="2000 4 100000 12 128 32 0.01 0.4 hard results/models/strategy-net.json true 30 12345"
```

Arguments are:

1. `games`
2. `players`
3. `samples`
4. `epochs`
5. `batch`
6. `hidden`
7. `learningRate`
8. `advisorFollowRate`
9. `difficulty`
10. `outputPath`
11. `useMonteCarloLabels`
12. `rollouts`
13. `seed`

## Generated Outputs

`results/` is treated as generated output and is ignored by Git. Regenerate ratings, interaction reports, charts, and trained models from the Gradle tasks above instead of editing or committing generated artifacts.
