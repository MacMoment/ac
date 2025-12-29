/*
 * MacAC Native Library
 * High-performance native helpers for anti-cheat detection
 * 
 * Features:
 * - Monotonic timing using RDTSC/RDTSCP (x86) or clock_gettime
 * - SIMD-optimized ring buffer operations
 * - High-precision statistics calculations
 */

#ifndef MACAC_NATIVE_H
#define MACAC_NATIVE_H

#include <cstdint>
#include <cstddef>
#include <atomic>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// Timing Functions
// ============================================================================

/**
 * Get high-precision monotonic timestamp in nanoseconds.
 * Uses RDTSCP on x86_64 for highest precision, falls back to clock_gettime.
 */
int64_t macac_nanotime(void);

/**
 * Get TSC (Time Stamp Counter) directly via RDTSCP instruction.
 * Only available on x86/x86_64.
 */
uint64_t macac_rdtscp(void);

/**
 * Get TSC calibration factor (nanoseconds per TSC tick).
 * Must be called once during initialization.
 */
double macac_calibrate_tsc(void);

/**
 * Convert TSC ticks to nanoseconds using calibration factor.
 */
int64_t macac_tsc_to_nanos(uint64_t tsc_value);

// ============================================================================
// Ring Buffer (Lock-free, SIMD-optimized)
// ============================================================================

/**
 * Native ring buffer structure for double values.
 * Uses aligned storage for SIMD operations.
 */
typedef struct {
    double* data;           // Aligned buffer storage
    size_t capacity;        // Maximum elements
    std::atomic<size_t> head;   // Write position (atomic for lock-free)
    std::atomic<size_t> size;   // Current element count
} macac_ringbuffer_t;

/**
 * Create a new ring buffer with specified capacity.
 * Buffer is aligned for SIMD access.
 */
macac_ringbuffer_t* macac_ringbuffer_create(size_t capacity);

/**
 * Destroy a ring buffer and free memory.
 */
void macac_ringbuffer_destroy(macac_ringbuffer_t* rb);

/**
 * Push a value onto the ring buffer (O(1)).
 */
void macac_ringbuffer_push(macac_ringbuffer_t* rb, double value);

/**
 * Get element at age (0 = most recent).
 * Returns NaN if index out of range.
 */
double macac_ringbuffer_get(macac_ringbuffer_t* rb, size_t age);

/**
 * Clear the ring buffer.
 */
void macac_ringbuffer_clear(macac_ringbuffer_t* rb);

/**
 * Get current size.
 */
size_t macac_ringbuffer_size(macac_ringbuffer_t* rb);

// ============================================================================
// SIMD Statistics (AVX2 optimized)
// ============================================================================

/**
 * Calculate sum using SIMD (AVX2 if available).
 */
double macac_simd_sum(const double* data, size_t count);

/**
 * Calculate mean using SIMD.
 */
double macac_simd_mean(const double* data, size_t count);

/**
 * Calculate variance using SIMD.
 */
double macac_simd_variance(const double* data, size_t count, double mean);

/**
 * Calculate median (uses partial sort, not SIMD).
 */
double macac_median(double* data, size_t count);

/**
 * Calculate MAD (Median Absolute Deviation).
 */
double macac_mad(double* data, size_t count);

// ============================================================================
// Network Functions
// ============================================================================

/**
 * Network connection handle.
 */
typedef struct macac_connection macac_connection_t;

/**
 * Create a TCP connection to the analytics server.
 */
macac_connection_t* macac_net_connect(const char* host, int port);

/**
 * Send violation data to the server.
 * Data is serialized as JSON.
 */
int macac_net_send_violation(macac_connection_t* conn, 
                              const char* player_uuid,
                              const char* category,
                              double confidence,
                              double severity,
                              int64_t timestamp);

/**
 * Close the connection.
 */
void macac_net_close(macac_connection_t* conn);

/**
 * Check if connection is alive.
 */
int macac_net_is_connected(macac_connection_t* conn);

// ============================================================================
// Combat Analysis Functions
// ============================================================================

/**
 * Calculate 3D Euclidean distance.
 */
double macac_distance_3d(double x1, double y1, double z1,
                         double x2, double y2, double z2);

/**
 * Calculate horizontal (XZ plane) distance.
 */
double macac_distance_horizontal(double x1, double z1, double x2, double z2);

/**
 * Batch calculate distances using SIMD.
 * coords layout: [x1,y1,z1,x2,y2,z2] per element
 */
void macac_batch_distance_3d(const double* coords, double* distances, size_t count);

/**
 * Calculate yaw angle from direction vector.
 */
double macac_calc_yaw(double dx, double dz);

/**
 * Calculate pitch angle from direction vector.
 */
double macac_calc_pitch(double dx, double dy, double dz);

/**
 * Calculate expected aim angles to a target.
 */
void macac_calc_aim_angles(double attacker_x, double attacker_y, double attacker_z,
                           double target_x, double target_y, double target_z,
                           double* out_yaw, double* out_pitch);

/**
 * Calculate aim error (angular difference).
 */
double macac_calc_aim_error(double actual_yaw, double actual_pitch,
                            double expected_yaw, double expected_pitch);

/**
 * Calculate snap angle (rotation change).
 */
double macac_calc_snap_angle(double prev_yaw, double prev_pitch,
                             double curr_yaw, double curr_pitch);

/**
 * Combat analysis results structure.
 */
typedef struct {
    double aimbot_confidence;
    double reach_confidence;
    double autoclicker_confidence;
    double combined_confidence;
    double avg_aim_error;
    double aim_variance;
    double avg_snap_angle;
    double avg_reach;
    double hit_rate;
    double avg_attack_interval;
} macac_combat_analysis_t;

/**
 * Analyze combat data for cheating patterns.
 */
void macac_analyze_combat(const double* aim_errors, const double* snap_angles,
                          const double* reaches, const double* attack_intervals,
                          const double* hits, size_t count,
                          macac_combat_analysis_t* result);

#ifdef __cplusplus
}
#endif

#endif // MACAC_NATIVE_H
