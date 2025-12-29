/*
 * MacAC Native Library - SIMD Statistics Implementation
 * 
 * Uses AVX2 SIMD instructions for fast statistics calculations.
 * Falls back to scalar operations if AVX2 is not available.
 */

#include "macac_native.h"
#include <cmath>
#include <algorithm>
#include <cstring>

// Check for AVX2 support at compile time
#if defined(__AVX2__)
#include <immintrin.h>
#define HAS_AVX2 1
#else
#define HAS_AVX2 0
#endif

extern "C" {

// ============================================================================
// AVX2 SIMD Implementation
// ============================================================================

#if HAS_AVX2

/**
 * Sum 4 doubles using AVX2.
 * Process 4 elements at a time using 256-bit registers.
 */
static double simd_sum_avx2(const double* data, size_t count) {
    __m256d sum_vec = _mm256_setzero_pd();
    
    // Process 4 doubles at a time
    size_t i = 0;
    for (; i + 4 <= count; i += 4) {
        __m256d vals = _mm256_loadu_pd(&data[i]);
        sum_vec = _mm256_add_pd(sum_vec, vals);
    }
    
    // Horizontal sum of the 4 doubles in sum_vec
    // sum_vec = [a, b, c, d]
    // After first add: [a+c, b+d, a+c, b+d]
    // After second add: [a+b+c+d, ...]
    __m128d low = _mm256_castpd256_pd128(sum_vec);
    __m128d high = _mm256_extractf128_pd(sum_vec, 1);
    __m128d sum128 = _mm_add_pd(low, high);
    sum128 = _mm_hadd_pd(sum128, sum128);
    
    double sum = _mm_cvtsd_f64(sum128);
    
    // Handle remaining elements
    for (; i < count; i++) {
        sum += data[i];
    }
    
    return sum;
}

/**
 * Variance using AVX2.
 * Computes sum of squared differences from mean.
 */
static double simd_variance_avx2(const double* data, size_t count, double mean) {
    __m256d mean_vec = _mm256_set1_pd(mean);
    __m256d sum_sq_vec = _mm256_setzero_pd();
    
    size_t i = 0;
    for (; i + 4 <= count; i += 4) {
        __m256d vals = _mm256_loadu_pd(&data[i]);
        __m256d diff = _mm256_sub_pd(vals, mean_vec);
        __m256d sq = _mm256_mul_pd(diff, diff);
        sum_sq_vec = _mm256_add_pd(sum_sq_vec, sq);
    }
    
    // Horizontal sum
    __m128d low = _mm256_castpd256_pd128(sum_sq_vec);
    __m128d high = _mm256_extractf128_pd(sum_sq_vec, 1);
    __m128d sum128 = _mm_add_pd(low, high);
    sum128 = _mm_hadd_pd(sum128, sum128);
    
    double sum_sq = _mm_cvtsd_f64(sum128);
    
    // Handle remaining elements
    for (; i < count; i++) {
        double diff = data[i] - mean;
        sum_sq += diff * diff;
    }
    
    return sum_sq / (count > 1 ? count - 1 : 1);
}

#endif // HAS_AVX2

// ============================================================================
// Scalar Fallback Implementation
// ============================================================================

static double scalar_sum(const double* data, size_t count) {
    double sum = 0.0;
    for (size_t i = 0; i < count; i++) {
        sum += data[i];
    }
    return sum;
}

static double scalar_variance(const double* data, size_t count, double mean) {
    double sum_sq = 0.0;
    for (size_t i = 0; i < count; i++) {
        double diff = data[i] - mean;
        sum_sq += diff * diff;
    }
    return sum_sq / (count > 1 ? count - 1 : 1);
}

// ============================================================================
// Public API
// ============================================================================

double macac_simd_sum(const double* data, size_t count) {
    if (!data || count == 0) {
        return 0.0;
    }
    
#if HAS_AVX2
    return simd_sum_avx2(data, count);
#else
    return scalar_sum(data, count);
#endif
}

double macac_simd_mean(const double* data, size_t count) {
    if (!data || count == 0) {
        return 0.0;
    }
    
    return macac_simd_sum(data, count) / count;
}

double macac_simd_variance(const double* data, size_t count, double mean) {
    if (!data || count < 2) {
        return 0.0;
    }
    
#if HAS_AVX2
    return simd_variance_avx2(data, count, mean);
#else
    return scalar_variance(data, count, mean);
#endif
}

/**
 * Partition helper for quickselect algorithm.
 */
static size_t partition(double* data, size_t left, size_t right, size_t pivot_idx) {
    double pivot_val = data[pivot_idx];
    
    // Move pivot to end
    std::swap(data[pivot_idx], data[right]);
    
    size_t store_idx = left;
    for (size_t i = left; i < right; i++) {
        if (data[i] < pivot_val) {
            std::swap(data[i], data[store_idx]);
            store_idx++;
        }
    }
    
    // Move pivot to final position
    std::swap(data[store_idx], data[right]);
    return store_idx;
}

/**
 * Quickselect algorithm for O(n) median finding.
 */
static double quickselect(double* data, size_t left, size_t right, size_t k) {
    while (left < right) {
        // Choose pivot (middle element)
        size_t pivot_idx = left + (right - left) / 2;
        pivot_idx = partition(data, left, right, pivot_idx);
        
        if (k == pivot_idx) {
            return data[k];
        } else if (k < pivot_idx) {
            right = pivot_idx - 1;
        } else {
            left = pivot_idx + 1;
        }
    }
    
    return data[left];
}

double macac_median(double* data, size_t count) {
    if (!data || count == 0) {
        return 0.0;
    }
    
    if (count == 1) {
        return data[0];
    }
    
    // Create a copy to avoid modifying original
    double* copy = new double[count];
    memcpy(copy, data, count * sizeof(double));
    
    double median;
    size_t mid = count / 2;
    
    if (count % 2 == 0) {
        // Even count: average of two middle elements
        double a = quickselect(copy, 0, count - 1, mid - 1);
        double b = quickselect(copy, 0, count - 1, mid);
        median = (a + b) / 2.0;
    } else {
        // Odd count: middle element
        median = quickselect(copy, 0, count - 1, mid);
    }
    
    delete[] copy;
    return median;
}

double macac_mad(double* data, size_t count) {
    if (!data || count == 0) {
        return 0.0;
    }
    
    double median = macac_median(data, count);
    
    // Calculate absolute deviations
    double* deviations = new double[count];
    for (size_t i = 0; i < count; i++) {
        deviations[i] = std::abs(data[i] - median);
    }
    
    double mad = macac_median(deviations, count);
    
    delete[] deviations;
    return mad;
}

} // extern "C"
