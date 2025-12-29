package com.macmoment.macac.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JNI bridge to optional native helper library.
 * 
 * <p>This class provides high-performance native operations when available,
 * with automatic fallback to pure-Java implementations when the native
 * library is not present or fails to load.
 * 
 * <p><strong>Native capabilities when available:</strong>
 * <ul>
 *   <li>High-precision monotonic timing via RDTSCP instruction</li>
 *   <li>SIMD-optimized statistical calculations (AVX2)</li>
 *   <li>Native ring buffer for reduced GC pressure</li>
 *   <li>Optimized combat angle calculations</li>
 *   <li>Network communication for external analytics</li>
 * </ul>
 * 
 * <p><strong>Platform Support:</strong>
 * <ul>
 *   <li>Windows (x64): macac_native.dll</li>
 *   <li>Linux (x64): libmacac_native.so</li>
 *   <li>macOS (x64/arm64): libmacac_native.dylib</li>
 * </ul>
 * 
 * <p>The native library is completely optional. All functionality has
 * pure-Java fallbacks that provide identical results, though potentially
 * with higher latency or CPU usage.
 * 
 * <p><strong>Thread Safety:</strong> All methods are thread-safe.
 * The library loading uses synchronized initialization.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class NativeHelper {
    
    private static final Logger LOGGER = Logger.getLogger(NativeHelper.class.getName());
    
    /** Load state flag to prevent repeated load attempts. */
    private static volatile boolean loadAttempted = false;
    
    /** Whether native library was successfully loaded. */
    private static volatile boolean nativeLoaded = false;
    
    /** Native library base name (without platform-specific extension). */
    private static final String LIB_NAME = "macac_native";
    
    /** Windows library file name. */
    private static final String LIB_NAME_WINDOWS = "macac_native.dll";
    
    /** Linux library file name. */
    private static final String LIB_NAME_LINUX = "libmacac_native.so";
    
    /** macOS library file name. */
    private static final String LIB_NAME_MACOS = "libmacac_native.dylib";
    
    /** Extraction buffer size for copying library from JAR. */
    private static final int EXTRACTION_BUFFER_SIZE = 8192;
    
    /**
     * Private constructor prevents instantiation.
     */
    private NativeHelper() {
        throw new AssertionError("NativeHelper is a utility class and cannot be instantiated");
    }
    
    /**
     * Loads the native library if available.
     * 
     * <p>This method is safe to call multiple times; the library is only
     * loaded once. Subsequent calls return the cached result.
     * 
     * <p>Loading is attempted in this order:
     * <ol>
     *   <li>System library path (java.library.path)</li>
     *   <li>Extraction from JAR resources to temp directory</li>
     * </ol>
     * 
     * @return true if native library is loaded and available
     */
    public static synchronized boolean loadNativeLibrary() {
        if (loadAttempted) {
            return nativeLoaded;
        }
        loadAttempted = true;
        
        // Try loading from system library path first
        if (tryLoadFromSystemPath()) {
            return true;
        }
        
        // Try extracting from JAR resources
        if (tryLoadFromResources()) {
            return true;
        }
        
        LOGGER.fine("Native library not available, using Java fallbacks");
        return false;
    }
    
    /**
     * Attempts to load the library from the system library path.
     */
    private static boolean tryLoadFromSystemPath() {
        try {
            System.loadLibrary(LIB_NAME);
            nativeLoaded = true;
            init();
            LOGGER.info("Native library loaded from system path");
            return true;
        } catch (final UnsatisfiedLinkError e) {
            LOGGER.fine("Native library not found in system path: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Attempts to load the library by extracting from JAR resources.
     */
    private static boolean tryLoadFromResources() {
        try {
            loadFromResources();
            nativeLoaded = true;
            init();
            LOGGER.info("Native library loaded from resources");
            return true;
        } catch (final Exception e) {
            LOGGER.log(Level.FINE, "Failed to load native library from resources", e);
            return false;
        }
    }
    
    /**
     * Extracts and loads the native library from JAR resources.
     */
    private static void loadFromResources() throws IOException {
        final String osName = System.getProperty("os.name").toLowerCase();
        final String libName = getLibraryName(osName);
        final String resourcePath = "/native/" + libName;
        
        try (final InputStream in = NativeHelper.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Native library not found in resources: " + resourcePath);
            }
            
            // Extract to temp directory
            final File tempDir = new File(System.getProperty("java.io.tmpdir"), "macac-native");
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw new IOException("Failed to create temp directory: " + tempDir);
            }
            
            final File tempLib = new File(tempDir, libName);
            tempLib.deleteOnExit();
            
            try (final FileOutputStream out = new FileOutputStream(tempLib)) {
                final byte[] buffer = new byte[EXTRACTION_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            
            System.load(tempLib.getAbsolutePath());
        }
    }
    
    /**
     * Returns the platform-specific library file name.
     */
    private static String getLibraryName(final String osName) {
        if (osName.contains("win")) {
            return LIB_NAME_WINDOWS;
        } else if (osName.contains("mac")) {
            return LIB_NAME_MACOS;
        } else {
            return LIB_NAME_LINUX;
        }
    }
    
    /**
     * Checks if the native library is available for use.
     * 
     * <p>If the library hasn't been loaded yet, this method will attempt
     * to load it first.
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
     * Initializes the native library (calibrates TSC frequency, etc.).
     */
    private static native void init();
    
    /**
     * Returns high-precision monotonic time in nanoseconds via RDTSCP.
     * 
     * @return monotonic time in nanoseconds
     */
    public static native long nanoTime();
    
    /**
     * Returns the raw RDTSCP counter value.
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
