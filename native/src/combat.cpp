/*
 * MacAC Native Library - Combat Analysis Implementation
 * 
 * High-performance combat pattern detection using:
 * - SIMD-optimized angle calculations
 * - Fast distance computations with SSE/AVX
 * - Pattern matching for aimbot detection
 * - Statistical analysis of combat data
 */

#include "macac_native.h"
#include <cmath>
#include <cstring>
#include <algorithm>

// Check for SIMD support
#if defined(__AVX2__)
#include <immintrin.h>
#define HAS_AVX2 1
#elif defined(__SSE4_1__)
#include <smmintrin.h>
#define HAS_SSE4 1
#elif defined(__SSE2__)
#include <emmintrin.h>
#define HAS_SSE2 1
#else
#define HAS_SIMD 0
#endif

// ============================================================================
// Constants
// ============================================================================

static const double DEG_TO_RAD = 3.14159265358979323846 / 180.0;
static const double RAD_TO_DEG = 180.0 / 3.14159265358979323846;
static const double PLAYER_EYE_HEIGHT = 1.62;

// ============================================================================
// Fast Math Functions (Assembly optimized)
// ============================================================================

#if defined(__x86_64__) || defined(__i386__)

/**
 * Fast inverse square root using SSE rsqrtss instruction.
 * Provides ~11 bits of precision, sufficient for game math.
 */
__attribute__((always_inline))
static inline float fast_rsqrt(float x) {
    float result;
    __asm__ __volatile__(
        "rsqrtss %1, %0"
        : "=x"(result)
        : "x"(x)
    );
    return result;
}

/**
 * Fast square root using SSE sqrtss instruction.
 */
__attribute__((always_inline))
static inline float fast_sqrt(float x) {
    float result;
    __asm__ __volatile__(
        "sqrtss %1, %0"
        : "=x"(result)
        : "x"(x)
    );
    return result;
}

/**
 * SIMD dot product for 3D vectors using SSE.
 */
__attribute__((always_inline))
static inline float simd_dot3(const float* a, const float* b) {
#if HAS_SSE4
    __m128 va = _mm_loadu_ps(a);
    __m128 vb = _mm_loadu_ps(b);
    __m128 dp = _mm_dp_ps(va, vb, 0x71); // Dot product, mask first 3 elements
    return _mm_cvtss_f32(dp);
#else
    return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
#endif
}

#else

// Fallback implementations for non-x86
static inline float fast_rsqrt(float x) {
    return 1.0f / sqrtf(x);
}

static inline float fast_sqrt(float x) {
    return sqrtf(x);
}

static inline float simd_dot3(const float* a, const float* b) {
    return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
}

#endif

// ============================================================================
// Distance Calculations
// ============================================================================

extern "C" {

/**
 * Calculate 3D Euclidean distance using SIMD.
 */
double macac_distance_3d(double x1, double y1, double z1,
                         double x2, double y2, double z2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double dz = z2 - z1;
    return sqrt(dx*dx + dy*dy + dz*dz);
}

/**
 * Calculate horizontal (XZ plane) distance.
 */
double macac_distance_horizontal(double x1, double z1, double x2, double z2) {
    double dx = x2 - x1;
    double dz = z2 - z1;
    return sqrt(dx*dx + dz*dz);
}

/**
 * Batch calculate distances using AVX2.
 * Input: arrays of x1,y1,z1,x2,y2,z2 coordinates
 * Output: array of distances
 */
void macac_batch_distance_3d(const double* coords, double* distances, size_t count) {
    // coords layout: [x1,y1,z1,x2,y2,z2] per element, total 6 * count
    
#if HAS_AVX2
    // Process 4 distances at a time
    size_t i = 0;
    for (; i + 4 <= count; i += 4) {
        // Load coordinates for 4 distance calculations
        __m256d x1 = _mm256_set_pd(
            coords[(i+3)*6 + 0], coords[(i+2)*6 + 0],
            coords[(i+1)*6 + 0], coords[(i+0)*6 + 0]
        );
        __m256d y1 = _mm256_set_pd(
            coords[(i+3)*6 + 1], coords[(i+2)*6 + 1],
            coords[(i+1)*6 + 1], coords[(i+0)*6 + 1]
        );
        __m256d z1 = _mm256_set_pd(
            coords[(i+3)*6 + 2], coords[(i+2)*6 + 2],
            coords[(i+1)*6 + 2], coords[(i+0)*6 + 2]
        );
        __m256d x2 = _mm256_set_pd(
            coords[(i+3)*6 + 3], coords[(i+2)*6 + 3],
            coords[(i+1)*6 + 3], coords[(i+0)*6 + 3]
        );
        __m256d y2 = _mm256_set_pd(
            coords[(i+3)*6 + 4], coords[(i+2)*6 + 4],
            coords[(i+1)*6 + 4], coords[(i+0)*6 + 4]
        );
        __m256d z2 = _mm256_set_pd(
            coords[(i+3)*6 + 5], coords[(i+2)*6 + 5],
            coords[(i+1)*6 + 5], coords[(i+0)*6 + 5]
        );
        
        // Calculate deltas
        __m256d dx = _mm256_sub_pd(x2, x1);
        __m256d dy = _mm256_sub_pd(y2, y1);
        __m256d dz = _mm256_sub_pd(z2, z1);
        
        // Square and sum
        __m256d dx2 = _mm256_mul_pd(dx, dx);
        __m256d dy2 = _mm256_mul_pd(dy, dy);
        __m256d dz2 = _mm256_mul_pd(dz, dz);
        
        __m256d sum = _mm256_add_pd(dx2, _mm256_add_pd(dy2, dz2));
        
        // Square root
        __m256d dist = _mm256_sqrt_pd(sum);
        
        // Store results
        _mm256_storeu_pd(&distances[i], dist);
    }
    
    // Handle remaining elements
    for (; i < count; i++) {
        distances[i] = macac_distance_3d(
            coords[i*6 + 0], coords[i*6 + 1], coords[i*6 + 2],
            coords[i*6 + 3], coords[i*6 + 4], coords[i*6 + 5]
        );
    }
#else
    // Scalar fallback
    for (size_t i = 0; i < count; i++) {
        distances[i] = macac_distance_3d(
            coords[i*6 + 0], coords[i*6 + 1], coords[i*6 + 2],
            coords[i*6 + 3], coords[i*6 + 4], coords[i*6 + 5]
        );
    }
#endif
}

// ============================================================================
// Angle Calculations
// ============================================================================

/**
 * Calculate yaw angle from direction vector.
 * Returns angle in degrees [-180, 180].
 */
double macac_calc_yaw(double dx, double dz) {
    return atan2(-dx, dz) * RAD_TO_DEG;
}

/**
 * Calculate pitch angle from direction vector.
 * Returns angle in degrees [-90, 90].
 */
double macac_calc_pitch(double dx, double dy, double dz) {
    double horiz_dist = sqrt(dx*dx + dz*dz);
    return -atan2(dy, horiz_dist) * RAD_TO_DEG;
}

/**
 * Calculate expected aim angles to a target.
 * 
 * @param attacker_x, y, z - Attacker position
 * @param target_x, y, z - Target position (center mass)
 * @param out_yaw - Output expected yaw
 * @param out_pitch - Output expected pitch
 */
void macac_calc_aim_angles(double attacker_x, double attacker_y, double attacker_z,
                           double target_x, double target_y, double target_z,
                           double* out_yaw, double* out_pitch) {
    double dx = target_x - attacker_x;
    double dy = target_y - (attacker_y + PLAYER_EYE_HEIGHT);
    double dz = target_z - attacker_z;
    
    *out_yaw = macac_calc_yaw(dx, dz);
    *out_pitch = macac_calc_pitch(dx, dy, dz);
}

/**
 * Calculate aim error (angular difference between actual and expected aim).
 * 
 * @param actual_yaw, pitch - Player's actual aim
 * @param expected_yaw, pitch - Expected aim to target
 * @return Angular error in degrees
 */
double macac_calc_aim_error(double actual_yaw, double actual_pitch,
                            double expected_yaw, double expected_pitch) {
    // Normalize yaw difference to [-180, 180]
    double yaw_diff = actual_yaw - expected_yaw;
    while (yaw_diff > 180.0) yaw_diff -= 360.0;
    while (yaw_diff < -180.0) yaw_diff += 360.0;
    
    double pitch_diff = actual_pitch - expected_pitch;
    
    // Euclidean angular distance
    return sqrt(yaw_diff * yaw_diff + pitch_diff * pitch_diff);
}

/**
 * Calculate snap angle (rotation change between frames).
 */
double macac_calc_snap_angle(double prev_yaw, double prev_pitch,
                             double curr_yaw, double curr_pitch) {
    double yaw_diff = curr_yaw - prev_yaw;
    while (yaw_diff > 180.0) yaw_diff -= 360.0;
    while (yaw_diff < -180.0) yaw_diff += 360.0;
    
    double pitch_diff = curr_pitch - prev_pitch;
    
    return sqrt(yaw_diff * yaw_diff + pitch_diff * pitch_diff);
}

// ============================================================================
// Combat Pattern Analysis
// ============================================================================

/**
 * Analyze combat data for cheating patterns.
 * 
 * @param aim_errors - Array of aim errors
 * @param snap_angles - Array of snap angles
 * @param reaches - Array of reach distances
 * @param attack_intervals - Array of attack intervals in ms
 * @param hits - Array of hit flags (1.0 = hit, 0.0 = miss)
 * @param count - Number of samples
 * @param result - Output analysis results
 */
void macac_analyze_combat(const double* aim_errors, const double* snap_angles,
                          const double* reaches, const double* attack_intervals,
                          const double* hits, size_t count,
                          macac_combat_analysis_t* result) {
    if (!result || count < 5) {
        if (result) memset(result, 0, sizeof(macac_combat_analysis_t));
        return;
    }
    
    // Calculate statistics using SIMD where possible
    double avg_aim = macac_simd_mean(aim_errors, count);
    double aim_var = macac_simd_variance(aim_errors, count, avg_aim);
    double avg_snap = macac_simd_mean(snap_angles, count);
    double avg_reach = macac_simd_mean(reaches, count);
    double hit_rate = macac_simd_mean(hits, count);
    double avg_interval = macac_simd_mean(attack_intervals, count);
    double interval_var = macac_simd_variance(attack_intervals, count, avg_interval);
    
    // Store debug data
    result->avg_aim_error = avg_aim;
    result->aim_variance = aim_var;
    result->avg_snap_angle = avg_snap;
    result->avg_reach = avg_reach;
    result->hit_rate = hit_rate;
    result->avg_attack_interval = avg_interval;
    
    // === Aimbot Detection ===
    double aimbot_score = 0.0;
    
    // Very low aim variance is suspicious
    if (aim_var < 1.0 && avg_aim < 3.0) {
        aimbot_score += (1.0 - aim_var) * 2.0;
    }
    
    // High snap angles with low aim error
    if (avg_snap > 30.0 && avg_aim < 5.0) {
        aimbot_score += (avg_snap / 90.0) * (1.0 - avg_aim / 10.0);
    }
    
    // Convert to confidence
    result->aimbot_confidence = 1.0 - exp(-aimbot_score);
    
    // === Reach Detection ===
    double reach_score = 0.0;
    
    // Check for reach beyond vanilla limits
    const double MAX_REACH = 3.5;
    if (avg_reach > MAX_REACH) {
        reach_score += (avg_reach - MAX_REACH) * 3.0;
    }
    
    result->reach_confidence = 1.0 - exp(-reach_score);
    
    // === Auto-clicker Detection ===
    double autoclicker_score = 0.0;
    
    // Very consistent attack intervals
    double interval_cv = avg_interval > 0 ? sqrt(interval_var) / avg_interval : 0;
    if (interval_cv < 0.1) {
        autoclicker_score += (0.1 - interval_cv) * 10.0;
    }
    
    // Too fast clicking
    if (avg_interval < 50.0 && avg_interval > 0) {
        autoclicker_score += (50.0 - avg_interval) / 50.0;
    }
    
    // Suspiciously high hit rate
    if (hit_rate > 0.85) {
        autoclicker_score += (hit_rate - 0.85) * 5.0;
    }
    
    result->autoclicker_confidence = 1.0 - exp(-autoclicker_score);
    
    // === Combined Confidence ===
    // Use max of individual confidences
    result->combined_confidence = std::max({
        result->aimbot_confidence,
        result->reach_confidence,
        result->autoclicker_confidence
    });
}

} // extern "C"
