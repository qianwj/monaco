# Repository Guidelines

## Project Structure & Module Organization

Monaco is a Java 21, multi-module Gradle MQTT broker built on Vert.x. `app/` and `gateway/` contain broker applications; `common/` holds shared session and entity contracts; `extension-core/` implements the extension API; and `logging/` provides the custom logging layer. Java follows the standard `src/main/java` and `src/test/java` layout within each module. FlatBuffers schemas and generation scripts live in `extension-protocol/`. Operational assets are under `deploy/dev/`, while architecture sources and exports are in `design/`.

## Build, Test, and Development Commands

Use JDK 21 and Gradle 8.11.1. Install the FlatBuffers compiler (`flatc`) before compiling `extension-core`. The checkout includes `gradlew`, but currently omits `gradle/wrapper/gradle-wrapper.jar`; restore the wrapper JAR or use a compatible local Gradle installation first.

- `./gradlew build` compiles every module and runs all tests.
- `./gradlew test` runs the complete JUnit suite.
- `./gradlew :gateway:test --tests 'cn.elvis.monaco.topics.TopicTest'` runs one test class.
- `./gradlew :app:run` starts the configured application locally using `app/src/main/resources/monaco.properties`.
- `./gradlew :extension-core:generateProtocol` regenerates Java sources from `extension-protocol/*.fbs`.

Do not commit generated `build/` directories.

## Coding Style & Naming Conventions

Use four-space indentation, braces on the same line, and one public top-level type per Java file. Keep packages lowercase under `cn.elvis.monaco`; use `PascalCase` for types, `lowerCamelCase` for methods and fields, and descriptive interface/implementation pairs such as `TopicForest` and `TopicForestImpl`. No formatter or linter is currently enforced, so match adjacent code and avoid unrelated reformatting.

## Testing Guidelines

Tests use JUnit 5 and, where asynchronous Vert.x behavior is involved, `VertxExtension` and `VertxTestContext`. Mirror production package paths under `src/test/java`, name classes `*Test` or `*Tests`, and name test methods for the behavior under test. Prefer JUnit assertions for clear failure messages. There is no configured coverage threshold; add focused regression tests for changed behavior.

## Commit & Pull Request Guidelines

History uses Conventional Commit-style subjects, chiefly `feat(scope): concise description`, for example `feat(auth): enhanced authenticator.` Keep commits focused and use a meaningful module or feature scope. Pull requests should explain behavior changes, list verification commands, link relevant issues, and call out configuration or protocol changes. Include screenshots only for changes to exported design or monitoring UI assets.
