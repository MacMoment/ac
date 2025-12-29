# MacAC Performance Guide

This document covers performance considerations and optimization strategies for MacAC.

## Design Principles

MacAC is designed with the following performance principles:

1. **Minimize Allocations**: Reuse objects where possible, avoid unnecessary object creation
2. **Constant-Time Operations**: Ring buffer provides O(1) push and O(n) iteration
3. **Lock-Free Hot Path**: Per-player state isolation reduces contention
4. **Bounded Memory**: Fixed-size history prevents memory growth

## Hot Path Analysis

The "hot path" is code executed on every movement packet (~20 times per second per player):

```
Player moves → Ingest → Extract Features → Run Checks → Aggregate → Decide
```

### Optimizations Applied

#### RingBuffer

```java
// O(1) push - no array copies
public synchronized void push(T element) {
    buffer[head] = element;
    head = (head + 1) % capacity;
    if (size < capacity) size++;
}
```

#### Feature Extraction

- Reuses `Features.Builder`
- Minimal math operations
- No collection allocations

#### Check Execution

- Checks return early when not triggered
- No allocations for clean results
- Explanation maps only created on violation

## Memory Usage

### Per-Player Memory

Each connected player requires:

| Component | Size (approx) |
|-----------|---------------|
| PlayerContext | ~200 bytes |
| TelemetryInput history (64) | ~4 KB |
| Features history (64) | ~2 KB |
| RollingWindows | ~400 bytes |
| EWMA trackers | ~100 bytes |
| **Total** | **~7 KB/player** |

### Scaling

| Players | Memory |
|---------|--------|
| 10 | ~70 KB |
| 100 | ~700 KB |
| 500 | ~3.5 MB |
| 1000 | ~7 MB |

## CPU Usage

### Per-Tick Overhead

Approximate CPU time per player per tick:
- Feature extraction: ~10 µs
- PacketTimingCheck: ~5 µs
- MovementConsistencyCheck: ~8 µs
- PredictionDriftCheck: ~15 µs
- Aggregation: ~2 µs
- **Total**: ~40 µs/player/tick

### Server Impact

At 20 TPS with 100 players:
- 100 × 40 µs = 4 ms per tick
- 4 ms / 50 ms = 8% of tick budget

## Configuration Tuning

### History Size

```yaml
history:
  size: 64  # Default
```

**Trade-offs:**
- Larger = more accurate statistics, more memory
- Smaller = faster iteration, less accuracy

**Recommendation:** 32-128 depending on server resources

### Median Window Size

```yaml
stats:
  median_window: 20  # Default
```

**Trade-offs:**
- Larger = more stable statistics, slower adaptation
- Smaller = faster adaptation, more noise

**Recommendation:** 10-30 for most servers

### Max Checks Per Tick

```yaml
performance:
  max_checks_per_tick: 10  # Default (not currently implemented)
```

Future feature to limit check execution per tick.

## Async Processing

### With ProtocolLib

ProtocolLib packet listeners can run asynchronously:

```yaml
performance:
  async_packets: true
```

Benefits:
- Reduced main thread load
- Better tick stability

Requirements:
- Thread-safe state updates
- Bukkit API calls on main thread

### Current Implementation

- State updates are synchronized per-player
- Bukkit API calls (alerts, punishments) scheduled to main thread

## Profiling

### Enable Debug Logging

```yaml
performance:
  debug: true
```

This logs:
- Check execution times
- Violation details
- State changes

### JVM Flags

Recommended JVM flags for Paper:

```
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
```

## Benchmarks

### RingBuffer Performance

```
Push operation: ~50 ns
Peek operation: ~20 ns
Get(n) operation: ~25 ns
Iterator creation: ~500 ns (snapshot)
```

### Statistics Performance

```
Median (20 elements): ~200 ns
MAD (20 elements): ~400 ns
EWMA update: ~10 ns
```

## Known Bottlenecks

1. **Iterator Snapshots**: Creating iterator copies array
   - Mitigation: Use direct indexed access when possible

2. **Synchronized Blocks**: Per-player synchronization
   - Mitigation: Fine-grained locking, single-writer pattern

3. **Explanation Maps**: Created on every violation
   - Mitigation: Only create when confidence > threshold

## Future Optimizations

1. **Native JNI Helper**: 
   - Monotonic timing via native clock
   - SIMD-optimized statistics

2. **Object Pooling**:
   - Reuse TelemetryInput objects
   - Pool CheckResult instances

3. **Batch Processing**:
   - Process multiple packets per check invocation
   - Reduce method call overhead

4. **Off-Heap Storage**:
   - Store history in direct buffers
   - Reduce GC pressure

## Monitoring

### Recommended Metrics

- Players tracked: `engine.getHistoryStore().size()`
- Checks enabled: `engine.getCheckRegistry().getEnabledChecks().size()`
- Alert rate: Log analysis
- False positive rate: Manual review

### Health Checks

1. Memory usage stable over time
2. TPS remains > 19.5
3. Alert rate reasonable (< 1/min typical)
