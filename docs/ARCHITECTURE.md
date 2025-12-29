# MacAC Architecture

This document describes the internal architecture of the MacAC anti-cheat plugin.

## Overview

MacAC uses a pipeline architecture to process player movement data through multiple stages:

```
Packet/Event → Ingest → Feature Extraction → Checks → Aggregation → Mitigation → Actions
```

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              MacACPlugin                                      │
│  (Entry point, event handlers, command processing)                           │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                                Engine                                        │
│  (Orchestrates all components, manages lifecycle)                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
┌──────────────────┐    ┌──────────────────┐    ┌──────────────────────────┐
│  PacketIngestor  │    │   HistoryStore   │    │     CheckRegistry        │
│                  │    │                  │    │                          │
│  - ProtocolLib   │    │  - PlayerContext │    │  - PacketTimingCheck     │
│  - Fallback      │    │  - RingBuffer    │    │  - MovementConsistency   │
│                  │    │                  │    │  - PredictionDriftCheck  │
└──────────────────┘    └──────────────────┘    └──────────────────────────┘
        │                                                   │
        ▼                                                   ▼
┌──────────────────┐                            ┌──────────────────────────┐
│ FeatureExtractor │                            │       Aggregator         │
│                  │                            │  (Confidence fusion)     │
│  - Speed         │                            └──────────────────────────┘
│  - Acceleration  │                                        │
│  - Jitter        │                                        ▼
│  - TimingSkew    │                            ┌──────────────────────────┐
└──────────────────┘                            │    MitigationPolicy      │
                                                │                          │
                                                │  - Exemptions            │
                                                │  - Cooldowns             │
                                                │  - Teleport guards       │
                                                └──────────────────────────┘
                                                            │
                                    ┌───────────────────────┼───────────────┐
                                    ▼                       ▼               ▼
                            ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
                            │AlertPublisher│    │Punishment    │    │Whitelist     │
                            │              │    │Handler       │    │Manager       │
                            └──────────────┘    └──────────────┘    └──────────────┘
```

## Core Components

### Engine (`com.macmoment.macac.core.Engine`)

The central orchestrator that:
- Initializes all components
- Manages component lifecycle
- Routes telemetry through the pipeline
- Handles player join/quit/teleport events

### PacketIngestor (`com.macmoment.macac.ingest.PacketIngestor`)

Interface for packet/event ingestion with two implementations:

1. **ProtocolLibPacketIngestor**: Uses ProtocolLib for packet-level interception
2. **FallbackEventIngestor**: Uses Bukkit's PlayerMoveEvent

Both produce `TelemetryInput` records for processing.

### HistoryStore (`com.macmoment.macac.pipeline.HistoryStore`)

Manages per-player state using `PlayerContext`:
- `TelemetryInput` history (RingBuffer)
- `Features` history (RingBuffer)
- Ping statistics (RollingWindow, EWMA)
- Exemption state

### FeatureExtractor (`com.macmoment.macac.pipeline.FeatureExtractor`)

Transforms raw telemetry into derived features:
- Horizontal/vertical speed
- Acceleration
- Rotation metrics
- Jitter score
- Timing skew

### CheckRegistry (`com.macmoment.macac.pipeline.CheckRegistry`)

Manages check lifecycle and configuration:
- Registers built-in checks
- Allows custom check registration
- Returns enabled checks for processing

### Aggregator (`com.macmoment.macac.pipeline.Aggregator`)

Combines results from multiple checks:
- Uses max-confidence fusion (conservative)
- Applies confidence thresholds
- Creates `Violation` records

### MitigationPolicy (`com.macmoment.macac.pipeline.MitigationPolicy`)

Applies safety policies:
- Exemption window after teleport/join
- Cooldown between alerts
- Game mode exemptions
- Whitelist checking

## Data Flow

### TelemetryInput

```java
record TelemetryInput(
    double dx, dy, dz,          // Movement deltas
    float yaw, pitch,           // Current rotation
    float deltaYaw, deltaPitch, // Rotation change
    boolean onGround,           // Ground state
    boolean inVehicle,          // Vehicle state
    boolean teleporting,        // Teleport flag
    long ping,                  // Current ping
    long nanoTime,              // Monotonic timestamp
    long tickDelta              // Time since last input
)
```

### Features

```java
record Features(
    double horizSpeed,          // Horizontal speed
    double vertSpeed,           // Vertical speed
    double horizAccel,          // Horizontal acceleration
    double vertAccel,           // Vertical acceleration
    double jitterScore,         // Movement irregularity
    double timingSkew,          // Timing deviation
    long pingNormalized,        // Smoothed ping
    boolean isLagging           // Lag detection flag
)
```

### CheckResult

```java
record CheckResult(
    String checkName,           // Check identifier
    double confidence,          // Detection confidence (0.0-1.0)
    double severity,            // Violation severity (0.0-1.0)
    Map<String, Object> explain // Explanation data
)
```

### Violation

```java
record Violation(
    UUID playerId,
    String playerName,
    String category,
    double confidence,
    double severity,
    long timestamp,
    long ping,
    List<CheckResult> checkResults,
    Map<String, Object> explanation
)
```

### Decision

```java
record Decision(
    Action action,              // NONE, ALERT, PUNISH, FLAG
    Violation violation,
    String reason
)
```

## Check Architecture

Each check implements the `Check` interface:

```java
public interface Check {
    String getName();
    String getCategory();
    boolean isEnabled();
    double getWeight();
    
    CheckResult analyze(TelemetryInput input, Features features, PlayerContext context);
    void configure(EngineConfig config);
}
```

### PacketTimingCheck

Analyzes packet timing patterns:
1. Computes inter-arrival time statistics
2. Detects burst patterns (too many fast packets)
3. Detects suspicious consistency (machine-like precision)
4. Uses ping-adjusted thresholds

### MovementConsistencyCheck

Analyzes movement physics:
1. Checks horizontal speed against maximum
2. Checks vertical speed against maximum
3. Detects impossible acceleration
4. Validates ground state consistency

### PredictionDriftCheck

Predicts movement from history:
1. Computes average velocity from recent samples
2. Predicts next position
3. Measures drift from prediction
4. Requires sustained drift to flag

## Thread Safety

- `PlayerContext` is thread-safe for single-writer scenarios
- `HistoryStore` uses `ConcurrentHashMap`
- `RingBuffer` operations are synchronized
- Bukkit API calls are scheduled to main thread

## Configuration Flow

```
config.yml → EngineConfig → Component.configure(config)
```

Each component reads its configuration from `EngineConfig` during:
- Initial startup
- Configuration reload (`/macac reload`)

## Extending MacAC

### Adding a Custom Check

1. Implement the `Check` interface
2. Register with `CheckRegistry`:

```java
CheckRegistry registry = engine.getCheckRegistry();
registry.register(new MyCustomCheck());
```

### Custom Packet Ingestor

Implement `PacketIngestor` interface:

```java
public interface PacketIngestor {
    void start();
    void stop();
    boolean isActive();
    String getName();
    void setCallback(BiConsumer<Player, TelemetryInput> callback);
}
```

## Native Library Architecture

MacAC includes an optional C++17 native library that provides high-performance operations via JNI.

### Components

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          NativeHelper (Java)                             │
│  (JNI bridge, automatic fallback to pure Java)                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ JNI
┌─────────────────────────────────────────────────────────────────────────┐
│                        libmacac_native.so                                │
├─────────────────┬─────────────────┬─────────────────┬──────────────────┤
│    timing.cpp   │  ringbuffer.cpp │    stats.cpp    │   network.cpp    │
│   (RDTSCP ASM)  │  (Lock-free)    │  (AVX2 SIMD)    │    (TCP I/O)     │
└─────────────────┴─────────────────┴─────────────────┴──────────────────┘
```

### Timing (timing.cpp)

Uses x86 RDTSCP instruction for nanosecond-precision monotonic timing:

```cpp
// Inline assembly for RDTSCP
__asm__ __volatile__(
    "rdtscp"                    // Read TSC, serialize
    : "=a"(lo), "=d"(hi), "=c"(*aux)
    : : "memory"
);
```

- Serializing instruction prevents out-of-order execution interference
- Auxiliary register provides CPU ID for core migration detection
- Calibrated against system clock for accurate nanosecond conversion

### Ring Buffer (ringbuffer.cpp)

Lock-free implementation with SIMD-aligned storage:

- 32-byte alignment for AVX2 operations
- Atomic head/size for single-writer concurrency
- O(1) push with wrap-around

### Statistics (stats.cpp)

AVX2 SIMD vectorized operations:

```cpp
// Process 4 doubles at a time
__m256d sum_vec = _mm256_setzero_pd();
for (; i + 4 <= count; i += 4) {
    __m256d vals = _mm256_loadu_pd(&data[i]);
    sum_vec = _mm256_add_pd(sum_vec, vals);
}
```

- 4x throughput for sum/mean calculations
- Horizontal reduction for final result
- Scalar fallback for remaining elements

### Networking (network.cpp)

Non-blocking TCP for analytics:

- TCP_NODELAY for low-latency sends
- Non-blocking I/O with poll()
- JSON serialization for violation data

## Analytics Server Integration

MacAC can optionally send violation data to a centralized analytics server:

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────────┐
│   Engine    │────▶│ AnalyticsClient │────▶│ Analytics Server │
│             │     │   (Async queue) │     │   (TCP receiver) │
└─────────────┘     └─────────────────┘     └──────────────────┘
```

### Message Format

```json
{
  "type": "violation",
  "player_uuid": "uuid-string",
  "player_name": "PlayerName",
  "category": "movement",
  "confidence": 0.998500,
  "severity": 0.750000,
  "timestamp": 1703864123456
}
```

### Connection Management

- Background sender thread with queue
- Auto-reconnection on failure
- Graceful degradation if server unavailable
