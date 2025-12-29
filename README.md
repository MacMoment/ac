# MacAC Anti-Cheat Plugin

A production-ready anti-cheat plugin for Paper (1.20.1+) with multi-layered detection, confidence-based decisions, and emphasis on low false positives.

## Features

- **Multi-layered Detection**: Packet-level analysis + behavioral monitoring + history-based checks
- **Confidence-based Decisions**: All checks output confidence scores (0.0-1.0) with configurable thresholds
- **Low False Positives**: Exemption windows, cooldown periods, and ping normalization
- **Hot-path Performance**: Ring buffer history, minimal allocations, per-player state isolation
- **Native Acceleration**: Optional C++17 native library with RDTSCP timing and AVX2 SIMD
- **Network Analytics**: Optional centralized violation reporting via TCP
- **Modular Architecture**: Easy to extend with custom checks

### Movement Detection
- **PacketTimingCheck**: Detects burst patterns and timing anomalies
- **MovementConsistencyCheck**: Validates physics and speed limits
- **PredictionDriftCheck**: Tracks movement prediction accuracy

### Combat Detection
- **CombatAimbotCheck**: Detects aimbot via snap angles, aim consistency, and robotic patterns
- **CombatReachCheck**: Detects reach hacks with ping-compensated distance validation
- **CombatAutoClickerCheck**: Detects killaura and auto-clickers via hit rates, attack intervals, and pattern analysis

## Requirements

- Paper 1.20.1 or higher
- Java 17 or higher
- (Optional) ProtocolLib 5.1.0+ for packet-level interception
- (Optional) Native library for high-performance operations

## Installation

1. Download the latest MacAC release
2. Place `MacAC-x.x.x.jar` in your server's `plugins` folder
3. (Optional) Install ProtocolLib for enhanced detection
4. (Optional) Place `libmacac_native.so` (Linux), `libmacac_native.dylib` (macOS), or `macac_native.dll` (Windows) in the server directory or `java.library.path`
5. Start/restart your server
6. Configure `plugins/MacAC/config.yml` as needed

## Building from Source

### Java Plugin
```bash
./gradlew build
```

The compiled JAR will be in `build/libs/MacAC-x.x.x.jar`

### Native Library (Optional)
```bash
cd native
mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
make -j$(nproc)
```

The compiled library will be `libmacac_native.so` (Linux), `libmacac_native.dylib` (macOS), or `macac_native.dll` (Windows).

## Native Library Features

The optional native library provides:

- **RDTSCP Timing**: Nanosecond-precision monotonic timing using x86 TSC
- **AVX2 SIMD Statistics**: Vectorized sum, mean, and variance calculations
- **Lock-free Ring Buffer**: High-performance per-player history storage
- **Native Networking**: Low-overhead TCP for analytics server communication
- **Combat Math**: Fast distance and angle calculations with SSE/AVX optimizations

### Combat Analysis Functions

The native library includes assembly-optimized combat math:

```cpp
// SIMD-optimized distance calculation
double macac_distance_3d(double x1, double y1, double z1,
                         double x2, double y2, double z2);

// Aim angle calculations
void macac_calc_aim_angles(double attackerX, double attackerY, double attackerZ,
                           double targetX, double targetY, double targetZ,
                           double* out_yaw, double* out_pitch);

// Combat pattern analysis (aimbot, reach, autoclicker detection)
void macac_analyze_combat(const double* aim_errors, const double* snap_angles,
                          const double* reaches, const double* attack_intervals,
                          const double* hits, size_t count,
                          macac_combat_analysis_t* result);
```

The plugin automatically detects and uses the native library if available, falling back to pure Java implementations otherwise.

## ProtocolLib Optionality

MacAC works in two modes:

### With ProtocolLib (Recommended)
- Uses packet-level interception for movement analysis
- Lower latency detection
- More accurate timing analysis
- Detailed packet information

### Without ProtocolLib (Fallback)
- Uses Bukkit's `PlayerMoveEvent`
- Slightly higher latency
- Still fully functional
- Graceful degradation

The plugin automatically detects ProtocolLib availability and switches modes.

## Configuration

The main configuration file is `plugins/MacAC/config.yml`.

### Key Settings

```yaml
thresholds:
  action_confidence: 0.997  # Confidence required to trigger alerts
  min_severity: 0.3         # Minimum severity to report

windows:
  exemption_ms: 250         # Grace period after teleport/join
  cooldown_ms: 1500         # Cooldown between alerts for same player

checks:
  packet_timing:
    enabled: true
  movement_consistency:
    enabled: true
  prediction_drift:
    enabled: true

actions:
  alerts:
    enabled: true
    console_log: true
  punishment:
    enabled: false          # DANGEROUS - keep off until tuned
    type: FLAG_ONLY         # Options: KICK, TEMP_MUTE, FLAG_ONLY
```

### Tuning Guidance

1. **Start Conservative**: Keep `action_confidence` at 0.997 or higher initially
2. **Monitor Alerts**: Watch for patterns in false positives
3. **Adjust Per-Check**: Fine-tune individual check thresholds
4. **Enable Punishment Carefully**: Only after extensive testing

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/macac reload` | `macac.admin` | Reload configuration |
| `/macac status` | `macac.admin` | Show engine status |
| `/macac exempt <player>` | `macac.admin` | Exempt a player from checks |
| `/macac unexempt <player>` | `macac.admin` | Remove exemption |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `macac.alerts` | op | Receive anti-cheat alerts |
| `macac.admin` | op | Full administration access |
| `macac.bypass` | false | Bypass all checks |

## Checks

### PacketTimingCheck
Analyzes packet inter-arrival times to detect unnatural burst patterns.
- Detects: Timer manipulation, packet spam, automation
- Uses ping-normalized timing with rolling median and MAD

### MovementConsistencyCheck
Analyzes movement patterns for physically impossible changes.
- Detects: Speed hacks, fly hacks, impossible acceleration
- Uses physics expectations with ping tolerance

### PredictionDriftCheck
Predicts player movement based on recent history.
- Detects: Movement inconsistencies, prediction failures
- Requires sustained evidence to avoid false positives

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for detailed architecture documentation.

## Performance

See [docs/PERFORMANCE.md](docs/PERFORMANCE.md) for performance considerations and optimization.

## License

This project is provided for educational and server administration purposes.

## Support

For issues and feature requests, please use the GitHub issue tracker.
