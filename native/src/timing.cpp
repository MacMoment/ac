/*
 * MacAC Native Library - Timing Implementation
 * 
 * Provides high-precision monotonic timing using:
 * - RDTSCP instruction (x86/x86_64) for nanosecond-level precision
 * - clock_gettime fallback for other architectures
 * 
 * Assembly is used for RDTSCP to ensure proper serialization
 * and to access the auxiliary register for CPU identification.
 */

#include "macac_native.h"
#include <time.h>
#include <chrono>

// TSC calibration factor (nanoseconds per tick)
static double tsc_nanos_per_tick = 0.0;
static bool tsc_calibrated = false;

// ============================================================================
// x86/x86_64 Assembly for RDTSCP
// ============================================================================

#if defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
#define HAS_RDTSCP 1

/**
 * Read Time Stamp Counter with processor ID (RDTSCP)
 * 
 * RDTSCP is a serializing instruction that waits for all previous
 * instructions to complete before reading the TSC. This prevents
 * out-of-order execution from affecting timing measurements.
 * 
 * The instruction also stores the processor ID in ECX, which we
 * can use to detect if the thread migrated between cores.
 */
__attribute__((always_inline))
static inline uint64_t read_tsc_serialized(uint32_t* aux) {
    uint32_t lo, hi;
    
    // RDTSCP: Read TSC and store auxiliary value (CPU ID) in aux
    // This is a serializing instruction - waits for all prior instructions
    __asm__ __volatile__(
        "rdtscp"                    // Read TSC, serialize
        : "=a"(lo),                 // EAX -> low 32 bits of TSC
          "=d"(hi),                 // EDX -> high 32 bits of TSC
          "=c"(*aux)                // ECX -> auxiliary (processor ID)
        :
        : "memory"                  // Barrier: memory clobbered
    );
    
    return ((uint64_t)hi << 32) | lo;
}

/**
 * Read TSC without serialization (RDTSC)
 * Faster but less accurate for timing measurements.
 */
__attribute__((always_inline))
static inline uint64_t read_tsc_fast(void) {
    uint32_t lo, hi;
    
    __asm__ __volatile__(
        "rdtsc"
        : "=a"(lo), "=d"(hi)
        :
        : "memory"
    );
    
    return ((uint64_t)hi << 32) | lo;
}

/**
 * Memory fence using MFENCE instruction.
 * Full barrier for loads and stores.
 */
__attribute__((always_inline))
static inline void memory_fence(void) {
    __asm__ __volatile__("mfence" ::: "memory");
}

/**
 * CPU pause instruction for spin-wait loops.
 * Reduces power consumption and improves performance in contended locks.
 */
__attribute__((always_inline))
static inline void cpu_pause(void) {
    __asm__ __volatile__("pause" ::: "memory");
}

#else
#define HAS_RDTSCP 0
#endif

// ============================================================================
// Public API Implementation
// ============================================================================

extern "C" {

uint64_t macac_rdtscp(void) {
#if HAS_RDTSCP
    uint32_t aux;
    return read_tsc_serialized(&aux);
#else
    // Fallback: use steady_clock
    auto now = std::chrono::steady_clock::now();
    return now.time_since_epoch().count();
#endif
}

double macac_calibrate_tsc(void) {
    if (tsc_calibrated) {
        return tsc_nanos_per_tick;
    }
    
#if HAS_RDTSCP
    // Calibrate TSC against system clock
    // Take multiple samples for accuracy
    
    const int SAMPLES = 10;
    double total_ratio = 0.0;
    
    for (int i = 0; i < SAMPLES; i++) {
        struct timespec start_ts, end_ts;
        uint64_t start_tsc, end_tsc;
        
        // Memory fence before timing
        memory_fence();
        
        // Get starting timestamps
        clock_gettime(CLOCK_MONOTONIC, &start_ts);
        start_tsc = macac_rdtscp();
        
        // Busy wait for ~10ms
        volatile int dummy = 0;
        for (int j = 0; j < 10000000; j++) {
            dummy++;
        }
        (void)dummy;
        
        // Get ending timestamps
        end_tsc = macac_rdtscp();
        clock_gettime(CLOCK_MONOTONIC, &end_ts);
        
        // Calculate elapsed time in nanos
        int64_t elapsed_nanos = (end_ts.tv_sec - start_ts.tv_sec) * 1000000000LL +
                                (end_ts.tv_nsec - start_ts.tv_nsec);
        uint64_t elapsed_tsc = end_tsc - start_tsc;
        
        if (elapsed_tsc > 0) {
            total_ratio += (double)elapsed_nanos / (double)elapsed_tsc;
        }
    }
    
    tsc_nanos_per_tick = total_ratio / SAMPLES;
#else
    // No TSC, ratio is 1.0 (already in nanos)
    tsc_nanos_per_tick = 1.0;
#endif
    
    tsc_calibrated = true;
    return tsc_nanos_per_tick;
}

int64_t macac_tsc_to_nanos(uint64_t tsc_value) {
    if (!tsc_calibrated) {
        macac_calibrate_tsc();
    }
    return (int64_t)(tsc_value * tsc_nanos_per_tick);
}

int64_t macac_nanotime(void) {
#if HAS_RDTSCP
    // Use high-precision TSC if available and calibrated
    if (tsc_calibrated) {
        return macac_tsc_to_nanos(macac_rdtscp());
    }
#endif
    
    // Fallback to clock_gettime
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

} // extern "C"
