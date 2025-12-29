package com.macmoment.macac.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * JNI bridge to native helper library.
 * Provides high-performance operations:
 * - Monotonic timing via RDTSCP
 * - SIMD-optimized statistics
 * - Native ring buffer
 * - Network communication for analytics
 * 
 * The native library is optional - if not available, Java fallbacks are used.
 */
public final class NativeHelper {
    
    private static final Logger LOGGER = Logger.getLogger(NativeHelper.class.getName());
    
    private static boolean nativeLoaded = false;
    private static boolean loadAttempted = false;
    
    // Library names per platform
    private static final String LIB_NAME = "macac_native";
    private static final String LIB_NAME_WINDOWS = "macac_native.dll";
    private static final String LIB_NAME_LINUX = "libmacac_native.so";
    private static final String LIB_NAME_MACOS = "libmacac_native.dylib";
    
    /**
     * Load native library if available.
     * Safe to call multiple times - only loads once.
     * 
     * @return true if native library is loaded
     */
    public static synchronized boolean loadNativeLibrary() {
        if (loadAttempted) {
            return nativeLoaded;
        }
        loadAttempted = true;
        
        try {
            // Try loading from java.library.path first
            System.loadLibrary(LIB_NAME);
            nativeLoaded = true;
            init();
            LOGGER.info("Native library loaded from system path");
            return true;
        } catch (UnsatisfiedLinkError e1) {
            // Try extracting from resources
            try {
                loadFromResources();
                nativeLoaded = true;
                init();
                LOGGER.info("Native library loaded from resources");
                return true;
            } catch (Exception e2) {
                LOGGER.fine("Native library not available, using Java fallbacks: " + e2.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Extract and load native library from JAR resources.
     */
    private static void loadFromResources() throws Exception {
        String osName = System.getProperty("os.name").toLowerCase();
        String libName;
        
        if (osName.contains("win")) {
            libName = LIB_NAME_WINDOWS;
        } else if (osName.contains("mac")) {
            libName = LIB_NAME_MACOS;
        } else {
            libName = LIB_NAME_LINUX;
        }
        
        String resourcePath = "/native/" + libName;
        
        try (InputStream in = NativeHelper.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new Exception("Native library not found in resources: " + resourcePath);
            }
            
            // Extract to temp directory
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "macac-native");
            tempDir.mkdirs();
            
            File tempLib = new File(tempDir, libName);
            tempLib.deleteOnExit();
            
            try (FileOutputStream out = new FileOutputStream(tempLib)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            System.load(tempLib.getAbsolutePath());
        }
    }
    
    /**
     * Check if native library is available.
     * 
     * @return true if native operations can be used
     */
    public static boolean isNativeAvailable() {
        if (!loadAttempted) {
            loadNativeLibrary();
        }
        return nativeLoaded;
    }
    
    // ========================================================================
    // Native Method Declarations
    // ========================================================================
    
    /**
     * Initialize native library (calibrate TSC, etc.)
     */
    private static native void init();
    
    /**
     * Get high-precision monotonic time in nanoseconds.
     */
    public static native long nanoTime();
    
    /**
     * Get raw RDTSCP value.
     */
    public static native long rdtscp();
    
    /**
     * Create a native ring buffer.
     * @param capacity Buffer capacity
     * @return Handle to native buffer, or 0 on failure
     */
    public static native long createRingBuffer(int capacity);
    
    /**
     * Destroy a native ring buffer.
     * @param handle Buffer handle from createRingBuffer
     */
    public static native void destroyRingBuffer(long handle);
    
    /**
     * Push value to native ring buffer.
     * @param handle Buffer handle
     * @param value Value to push
     */
    public static native void ringBufferPush(long handle, double value);
    
    /**
     * Get value from native ring buffer.
     * @param handle Buffer handle
     * @param age Age (0 = most recent)
     * @return Value at age, or NaN if out of range
     */
    public static native double ringBufferGet(long handle, int age);
    
    /**
     * Get native ring buffer size.
     * @param handle Buffer handle
     * @return Current size
     */
    public static native int ringBufferSize(long handle);
    
    /**
     * Clear native ring buffer.
     * @param handle Buffer handle
     */
    public static native void ringBufferClear(long handle);
    
    /**
     * Calculate sum using SIMD.
     * @param data Array of doubles
     * @return Sum
     */
    public static native double simdSum(double[] data);
    
    /**
     * Calculate mean using SIMD.
     * @param data Array of doubles
     * @return Mean
     */
    public static native double simdMean(double[] data);
    
    /**
     * Calculate median.
     * @param data Array of doubles
     * @return Median
     */
    public static native double median(double[] data);
    
    /**
     * Calculate MAD (Median Absolute Deviation).
     * @param data Array of doubles
     * @return MAD
     */
    public static native double mad(double[] data);
    
    /**
     * Connect to analytics server.
     * @param host Server hostname
     * @param port Server port
     * @return Connection handle, or 0 on failure
     */
    public static native long netConnect(String host, int port);
    
    /**
     * Send violation data to server.
     * @param handle Connection handle
     * @param playerUuid Player UUID string
     * @param category Violation category
     * @param confidence Confidence score
     * @param severity Severity score
     * @param timestamp Unix timestamp
     * @return Bytes sent, or negative on error
     */
    public static native int netSendViolation(long handle, String playerUuid, 
            String category, double confidence, double severity, long timestamp);
    
    /**
     * Close network connection.
     * @param handle Connection handle
     */
    public static native void netClose(long handle);
    
    /**
     * Check if connection is alive.
     * @param handle Connection handle
     * @return true if connected
     */
    public static native boolean netIsConnected(long handle);
    
    // ========================================================================
    // Java Fallback Implementations
    // ========================================================================
    
    /**
     * Get nanoTime using native or Java fallback.
     * 
     * @return Monotonic time in nanoseconds
     */
    public static long getNanoTime() {
        if (nativeLoaded) {
            return nanoTime();
        }
        return System.nanoTime();
    }
    
    /**
     * Calculate median using native or Java fallback.
     * 
     * @param data Array of values
     * @return Median value
     */
    public static double getMedian(double[] data) {
        if (nativeLoaded && data != null && data.length > 0) {
            return median(data);
        }
        return Stats.median(data);
    }
    
    /**
     * Calculate MAD using native or Java fallback.
     * 
     * @param data Array of values
     * @return MAD value
     */
    public static double getMad(double[] data) {
        if (nativeLoaded && data != null && data.length > 0) {
            return mad(data);
        }
        return Stats.mad(data);
    }
    
    /**
     * Calculate sum using native or Java fallback.
     * 
     * @param data Array of values
     * @return Sum
     */
    public static double getSum(double[] data) {
        if (nativeLoaded && data != null && data.length > 0) {
            return simdSum(data);
        }
        double sum = 0;
        if (data != null) {
            for (double v : data) sum += v;
        }
        return sum;
    }
    
    /**
     * Calculate mean using native or Java fallback.
     * 
     * @param data Array of values
     * @return Mean
     */
    public static double getMean(double[] data) {
        if (nativeLoaded && data != null && data.length > 0) {
            return simdMean(data);
        }
        return Stats.mean(data);
    }
    
    // ========================================================================
    // Combat Analysis Native Methods
    // ========================================================================
    
    /**
     * Calculate 3D Euclidean distance using SIMD.
     */
    public static native double distance3D(double x1, double y1, double z1,
                                           double x2, double y2, double z2);
    
    /**
     * Calculate horizontal (XZ plane) distance.
     */
    public static native double distanceHorizontal(double x1, double z1,
                                                   double x2, double z2);
    
    /**
     * Calculate expected aim angles to a target.
     * @return Array [yaw, pitch] in degrees
     */
    public static native double[] calcAimAngles(double attackerX, double attackerY, double attackerZ,
                                                double targetX, double targetY, double targetZ);
    
    /**
     * Calculate aim error (angular difference).
     * @return Error in degrees
     */
    public static native double calcAimError(double actualYaw, double actualPitch,
                                             double expectedYaw, double expectedPitch);
    
    /**
     * Calculate snap angle (rotation change between frames).
     * @return Angle in degrees
     */
    public static native double calcSnapAngle(double prevYaw, double prevPitch,
                                              double currYaw, double currPitch);
    
    /**
     * Analyze combat data for cheating patterns.
     * @param aimErrors Array of aim error values
     * @param snapAngles Array of snap angle values
     * @param reaches Array of reach distances
     * @param attackIntervals Array of attack intervals in ms
     * @param hits Array of hit flags (1.0 = hit, 0.0 = miss)
     * @return Analysis result array [aimbot_conf, reach_conf, autoclicker_conf, combined_conf,
     *         avg_aim_error, aim_variance, avg_snap, avg_reach, hit_rate, avg_interval]
     */
    public static native double[] analyzeCombat(double[] aimErrors, double[] snapAngles,
                                                double[] reaches, double[] attackIntervals,
                                                double[] hits);
    
    // ========================================================================
    // Combat Analysis Fallback Methods
    // ========================================================================
    
    /**
     * Calculate 3D distance using native or Java fallback.
     */
    public static double getDistance3D(double x1, double y1, double z1,
                                       double x2, double y2, double z2) {
        if (nativeLoaded) {
            return distance3D(x1, y1, z1, x2, y2, z2);
        }
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
    
    /**
     * Calculate aim error using native or Java fallback.
     */
    public static double getAimError(double actualYaw, double actualPitch,
                                     double expectedYaw, double expectedPitch) {
        if (nativeLoaded) {
            return calcAimError(actualYaw, actualPitch, expectedYaw, expectedPitch);
        }
        // Java fallback
        double yawDiff = actualYaw - expectedYaw;
        while (yawDiff > 180.0) yawDiff -= 360.0;
        while (yawDiff < -180.0) yawDiff += 360.0;
        double pitchDiff = actualPitch - expectedPitch;
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }
    
    /**
     * Calculate snap angle using native or Java fallback.
     */
    public static double getSnapAngle(double prevYaw, double prevPitch,
                                      double currYaw, double currPitch) {
        if (nativeLoaded) {
            return calcSnapAngle(prevYaw, prevPitch, currYaw, currPitch);
        }
        // Java fallback
        double yawDiff = currYaw - prevYaw;
        while (yawDiff > 180.0) yawDiff -= 360.0;
        while (yawDiff < -180.0) yawDiff += 360.0;
        double pitchDiff = currPitch - prevPitch;
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }
}
