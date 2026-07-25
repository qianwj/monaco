# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Monaco is an MQTT 5.0 broker built on Vert.x 5.0.0 with Java 21. It supports TCP and WebSocket transports, pluggable authentication, clustering via Infinispan, and Prometheus metrics.

## Build Commands

```bash
# Build entire project
./gradlew build

# Build a specific module
./gradlew :gateway:build

# Run tests
./gradlew test

# Run a single test class
./gradlew :gateway:test --tests "cn.elvis.monaco.topics.TopicTest"

# Run a single test method
./gradlew :gateway:test --tests "cn.elvis.monaco.topics.TopicTest.testMethodName"

# Clean build
./gradlew clean build
```

## Module Structure

- **app** — CLI entry point (`MonacoApplication`), configuration loading, legacy transport/session/persistence code
- **gateway** — Main broker implementation (`Application` entry point), modular architecture
- **common** — Shared interfaces and entities (event bus channels, authentication contracts)
- **extension-core** — Extension framework using FlatBuffers for IPC
- **extension-protocol** — FlatBuffers schema definitions (`.fbs` files), compile with `compile.sh`
- **logging** — Custom lightweight logging framework

## Architecture

### Module System (gateway)

The gateway uses a modular initialization pattern. Each major subsystem extends `Module` (abstract base class) and is initialized in order:

**Init order: Store → Manager → Session → Transport**

- `StoreModule` — Data persistence interfaces and in-memory implementations (ClientSessionStore, SubscriptionStore, RetainMessageStore, MessageStore, TopicAliasStore, FlightWindow)
- `ManagerModule` — Client session, publisher, subscriber, will, and packet ID management
- `SessionModule` — Endpoint handling, client sessions, push service, authentication
- `TransportModule` — TCP and WebSocket transport setup

### Topic System

- `Topics` — Static validation utilities for MQTT topic names and filters (wildcards `+`, `#`, shared subscriptions `$share/`)
- `TopicForest` / `TopicForestImpl` / `ShareableTopicForest` — Subscription matching with wildcard support
- `TopicTree` / `TopicTreeNode` — Tree-based topic hierarchy

### Configuration

Settings are loaded via `EnvironmentSettings` using `MONACO_` prefixed env vars (e.g., `MONACO_TCP_TRANSPORT_PORT`). See `DefaultSettings` for defaults.

Key defaults: TCP port 1883, WebSocket port 8083, metrics on port 9090 at `/metrics`.

### Event Communication

Modules communicate via Vert.x EventBus using channel keys defined in `common/.../ChannelKeys.java` (sealed interface pattern).

## Key Dependencies

Defined in `gradle/libs.versions.toml`:
- Vert.x 5.0.0 (core, mqtt, config, cluster, metrics)
- FlatBuffers 25.2.10 (extension protocol)
- Jackson 2.18.2 (serialization)
- JUnit Jupiter 5.10.3 + Vert.x test extensions

## Conventions

- Package root: `cn.elvis.monaco`
- Interface + `Default*` or concrete implementation naming
- Managers suffixed with `Manager`, modules with `Module`
- Tests use `@ExtendWith(VertxExtension.class)` and `VertxTestContext`

## Git

- Commit messages: do NOT include `Co-Authored-By` or any Claude/AI attribution lines
- Use conventional commits format: `type(scope): description`