/*
 * MacAC Native Library - JNI Bridge
 * 
 * Provides JNI interface for Java to call native functions.
 * Auto-generates JNI headers via javah or extracts from class files.
 */

#include <jni.h>
#include "macac_native.h"
#include <cstring>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// JNI Class: com.macmoment.macac.util.NativeHelper
// ============================================================================

/**
 * Initialize native library.
 * Calibrates TSC timer.
 */
JNIEXPORT void JNICALL Java_com_macmoment_macac_util_NativeHelper_init
  (JNIEnv *env, jclass clazz) {
    macac_calibrate_tsc();
}

/**
 * Get high-precision monotonic time in nanoseconds.
 */
JNIEXPORT jlong JNICALL Java_com_macmoment_macac_util_NativeHelper_nanoTime
  (JNIEnv *env, jclass clazz) {
    return (jlong)macac_nanotime();
}

/**
 * Get TSC value directly.
 */
JNIEXPORT jlong JNICALL Java_com_macmoment_macac_util_NativeHelper_rdtscp
  (JNIEnv *env, jclass clazz) {
    return (jlong)macac_rdtscp();
}

/**
 * Create a native ring buffer.
 * Returns handle (pointer as long).
 */
JNIEXPORT jlong JNICALL Java_com_macmoment_macac_util_NativeHelper_createRingBuffer
  (JNIEnv *env, jclass clazz, jint capacity) {
    macac_ringbuffer_t* rb = macac_ringbuffer_create((size_t)capacity);
    return (jlong)(intptr_t)rb;
}

/**
 * Destroy a native ring buffer.
 */
JNIEXPORT void JNICALL Java_com_macmoment_macac_util_NativeHelper_destroyRingBuffer
  (JNIEnv *env, jclass clazz, jlong handle) {
    macac_ringbuffer_t* rb = (macac_ringbuffer_t*)(intptr_t)handle;
    macac_ringbuffer_destroy(rb);
}

/**
 * Push value to ring buffer.
 */
JNIEXPORT void JNICALL Java_com_macmoment_macac_util_NativeHelper_ringBufferPush
  (JNIEnv *env, jclass clazz, jlong handle, jdouble value) {
    macac_ringbuffer_t* rb = (macac_ringbuffer_t*)(intptr_t)handle;
    if (rb) {
        macac_ringbuffer_push(rb, value);
    }
}

/**
 * Get value from ring buffer at age.
 */
JNIEXPORT jdouble JNICALL Java_com_macmoment_macac_util_NativeHelper_ringBufferGet
  (JNIEnv *env, jclass clazz, jlong handle, jint age) {
    macac_ringbuffer_t* rb = (macac_ringbuffer_t*)(intptr_t)handle;
    if (rb) {
        return macac_ringbuffer_get(rb, (size_t)age);
    }
    return 0.0;
}

/**
 * Get ring buffer size.
 */
JNIEXPORT jint JNICALL Java_com_macmoment_macac_util_NativeHelper_ringBufferSize
  (JNIEnv *env, jclass clazz, jlong handle) {
    macac_ringbuffer_t* rb = (macac_ringbuffer_t*)(intptr_t)handle;
    if (rb) {
        return (jint)macac_ringbuffer_size(rb);
    }
    return 0;
}

/**
 * Clear ring buffer.
 */
JNIEXPORT void JNICALL Java_com_macmoment_macac_util_NativeHelper_ringBufferClear
  (JNIEnv *env, jclass clazz, jlong handle) {
    macac_ringbuffer_t* rb = (macac_ringbuffer_t*)(intptr_t)handle;
    if (rb) {
        macac_ringbuffer_clear(rb);
    }
}

/**
 * Calculate SIMD sum of double array.
 */
JNIEXPORT jdouble JNICALL Java_com_macmoment_macac_util_NativeHelper_simdSum
  (JNIEnv *env, jclass clazz, jdoubleArray data) {
    if (!data) return 0.0;
    
    jsize len = env->GetArrayLength(data);
    if (len == 0) return 0.0;
    
    jdouble* elements = env->GetDoubleArrayElements(data, NULL);
    if (!elements) return 0.0;
    
    double result = macac_simd_sum(elements, (size_t)len);
    
    env->ReleaseDoubleArrayElements(data, elements, JNI_ABORT);
    return result;
}

/**
 * Calculate SIMD mean of double array.
 */
JNIEXPORT jdouble JNICALL Java_com_macmoment_macac_util_NativeHelper_simdMean
  (JNIEnv *env, jclass clazz, jdoubleArray data) {
    if (!data) return 0.0;
    
    jsize len = env->GetArrayLength(data);
    if (len == 0) return 0.0;
    
    jdouble* elements = env->GetDoubleArrayElements(data, NULL);
    if (!elements) return 0.0;
    
    double result = macac_simd_mean(elements, (size_t)len);
    
    env->ReleaseDoubleArrayElements(data, elements, JNI_ABORT);
    return result;
}

/**
 * Calculate median of double array.
 */
JNIEXPORT jdouble JNICALL Java_com_macmoment_macac_util_NativeHelper_median
  (JNIEnv *env, jclass clazz, jdoubleArray data) {
    if (!data) return 0.0;
    
    jsize len = env->GetArrayLength(data);
    if (len == 0) return 0.0;
    
    jdouble* elements = env->GetDoubleArrayElements(data, NULL);
    if (!elements) return 0.0;
    
    // Create copy for median (modifies array)
    double* copy = new double[len];
    memcpy(copy, elements, len * sizeof(double));
    
    double result = macac_median(copy, (size_t)len);
    
    delete[] copy;
    env->ReleaseDoubleArrayElements(data, elements, JNI_ABORT);
    return result;
}

/**
 * Calculate MAD of double array.
 */
JNIEXPORT jdouble JNICALL Java_com_macmoment_macac_util_NativeHelper_mad
  (JNIEnv *env, jclass clazz, jdoubleArray data) {
    if (!data) return 0.0;
    
    jsize len = env->GetArrayLength(data);
    if (len == 0) return 0.0;
    
    jdouble* elements = env->GetDoubleArrayElements(data, NULL);
    if (!elements) return 0.0;
    
    // Create copy for MAD (modifies array)
    double* copy = new double[len];
    memcpy(copy, elements, len * sizeof(double));
    
    double result = macac_mad(copy, (size_t)len);
    
    delete[] copy;
    env->ReleaseDoubleArrayElements(data, elements, JNI_ABORT);
    return result;
}

// ============================================================================
// JNI Network Functions
// ============================================================================

/**
 * Connect to analytics server.
 * Returns connection handle or 0 on failure.
 */
JNIEXPORT jlong JNICALL Java_com_macmoment_macac_util_NativeHelper_netConnect
  (JNIEnv *env, jclass clazz, jstring host, jint port) {
    if (!host) return 0;
    
    const char* hostStr = env->GetStringUTFChars(host, NULL);
    if (!hostStr) return 0;
    
    macac_connection_t* conn = macac_net_connect(hostStr, port);
    
    env->ReleaseStringUTFChars(host, hostStr);
    return (jlong)(intptr_t)conn;
}

/**
 * Send violation to server.
 */
JNIEXPORT jint JNICALL Java_com_macmoment_macac_util_NativeHelper_netSendViolation
  (JNIEnv *env, jclass clazz, jlong handle, jstring playerUuid, jstring category,
   jdouble confidence, jdouble severity, jlong timestamp) {
    
    macac_connection_t* conn = (macac_connection_t*)(intptr_t)handle;
    if (!conn || !playerUuid || !category) return -1;
    
    const char* uuidStr = env->GetStringUTFChars(playerUuid, NULL);
    const char* catStr = env->GetStringUTFChars(category, NULL);
    
    if (!uuidStr || !catStr) {
        if (uuidStr) env->ReleaseStringUTFChars(playerUuid, uuidStr);
        if (catStr) env->ReleaseStringUTFChars(category, catStr);
        return -1;
    }
    
    int result = macac_net_send_violation(conn, uuidStr, catStr, confidence, severity, timestamp);
    
    env->ReleaseStringUTFChars(playerUuid, uuidStr);
    env->ReleaseStringUTFChars(category, catStr);
    
    return result;
}

/**
 * Close network connection.
 */
JNIEXPORT void JNICALL Java_com_macmoment_macac_util_NativeHelper_netClose
  (JNIEnv *env, jclass clazz, jlong handle) {
    macac_connection_t* conn = (macac_connection_t*)(intptr_t)handle;
    macac_net_close(conn);
}

/**
 * Check if connection is alive.
 */
JNIEXPORT jboolean JNICALL Java_com_macmoment_macac_util_NativeHelper_netIsConnected
  (JNIEnv *env, jclass clazz, jlong handle) {
    macac_connection_t* conn = (macac_connection_t*)(intptr_t)handle;
    return macac_net_is_connected(conn) ? JNI_TRUE : JNI_FALSE;
}

#ifdef __cplusplus
}
#endif
