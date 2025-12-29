/*
 * MacAC Native Library - Ring Buffer Implementation
 * 
 * Lock-free ring buffer with aligned storage for SIMD operations.
 * Uses atomic operations for thread-safe push/access.
 */

#include "macac_native.h"
#include <cstdlib>
#include <cstring>
#include <cmath>
#include <algorithm>

// Alignment for SIMD (AVX2 = 32 bytes, AVX-512 = 64 bytes)
#define SIMD_ALIGNMENT 32

extern "C" {

macac_ringbuffer_t* macac_ringbuffer_create(size_t capacity) {
    if (capacity == 0) {
        return nullptr;
    }
    
    macac_ringbuffer_t* rb = new macac_ringbuffer_t();
    if (!rb) {
        return nullptr;
    }
    
    // Allocate aligned memory for SIMD operations
    size_t aligned_size = ((capacity * sizeof(double) + SIMD_ALIGNMENT - 1) 
                           / SIMD_ALIGNMENT) * SIMD_ALIGNMENT;
    
    rb->data = (double*)aligned_alloc(SIMD_ALIGNMENT, aligned_size);
    if (!rb->data) {
        delete rb;
        return nullptr;
    }
    
    // Initialize to zero
    memset(rb->data, 0, aligned_size);
    
    rb->capacity = capacity;
    rb->head.store(0, std::memory_order_relaxed);
    rb->size.store(0, std::memory_order_relaxed);
    
    return rb;
}

void macac_ringbuffer_destroy(macac_ringbuffer_t* rb) {
    if (rb) {
        if (rb->data) {
            free(rb->data);
        }
        delete rb;
    }
}

void macac_ringbuffer_push(macac_ringbuffer_t* rb, double value) {
    if (!rb || !rb->data) {
        return;
    }
    
    // Get current head position
    size_t current_head = rb->head.load(std::memory_order_relaxed);
    
    // Store value
    rb->data[current_head] = value;
    
    // Advance head (wrap around)
    size_t new_head = (current_head + 1) % rb->capacity;
    rb->head.store(new_head, std::memory_order_release);
    
    // Update size if not full
    size_t current_size = rb->size.load(std::memory_order_relaxed);
    if (current_size < rb->capacity) {
        rb->size.store(current_size + 1, std::memory_order_release);
    }
}

double macac_ringbuffer_get(macac_ringbuffer_t* rb, size_t age) {
    if (!rb || !rb->data) {
        return std::nan("");
    }
    
    size_t current_size = rb->size.load(std::memory_order_acquire);
    if (age >= current_size) {
        return std::nan("");
    }
    
    size_t current_head = rb->head.load(std::memory_order_acquire);
    
    // Calculate index: head - 1 - age (with wrap-around)
    size_t index = (current_head + rb->capacity - 1 - age) % rb->capacity;
    
    return rb->data[index];
}

void macac_ringbuffer_clear(macac_ringbuffer_t* rb) {
    if (!rb) {
        return;
    }
    
    rb->head.store(0, std::memory_order_release);
    rb->size.store(0, std::memory_order_release);
    
    if (rb->data) {
        memset(rb->data, 0, rb->capacity * sizeof(double));
    }
}

size_t macac_ringbuffer_size(macac_ringbuffer_t* rb) {
    if (!rb) {
        return 0;
    }
    return rb->size.load(std::memory_order_acquire);
}

} // extern "C"
