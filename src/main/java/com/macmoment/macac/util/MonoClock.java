package com.macmoment.macac.util;

/**
 * Interface for monotonic time sources.
 * 
 * <p>A monotonic clock provides timestamps that only ever increase (or stay the same),
 * making it suitable for measuring elapsed time. Unlike wall-clock time, monotonic
 * time is immune to system clock adjustments and is the appropriate choice for
 * performance measurements and timeout calculations.
 * 
 * <p>This interface allows for:
 * <ul>
 *   <li>Production use with the system clock ({@link #SYSTEM})</li>
 *   <li>Testing with a controllable mock clock ({@link #mock(long)})</li>
 *   <li>Potential native implementations (e.g., RDTSCP for higher precision)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> Implementations should be thread-safe.
 * The default {@link #SYSTEM} implementation delegates to {@link System#nanoTime()}
 * which is thread-safe.
 * 
 * @author MacAC Development Team
 * @since 1.0.0
 */
public interface MonoClock {
    
    /** Nanoseconds per millisecond constant for conversions. */
    long NANOS_PER_MILLI = 1_000_000L;
    
    /**
     * Returns the current monotonic time in nanoseconds.
     * 
     * <p>The returned value is relative to an arbitrary origin and should only
     * be used for measuring elapsed time by comparing two timestamps.
     * 
     * @return current monotonic time in nanoseconds
     */
    long nanoTime();

    /**
     * Returns the current monotonic time in milliseconds.
     * 
     * <p>This is a convenience method equivalent to {@code nanoTime() / 1_000_000L}.
     * 
     * @return current monotonic time in milliseconds
     */
    default long milliTime() {
        return nanoTime() / NANOS_PER_MILLI;
    }

    /**
     * Default implementation using {@link System#nanoTime()}.
     * 
     * <p>This provides nanosecond-precision monotonic time on most platforms.
     * The actual resolution may vary by JVM and operating system.
     */
    MonoClock SYSTEM = System::nanoTime;

    /**
     * Creates a mock clock for testing purposes.
     * 
     * <p>The mock clock provides controllable time that can be advanced
     * programmatically, enabling deterministic testing of time-dependent code.
     * 
     * @param initialNanos initial time value in nanoseconds
     * @return new MockClock instance
     */
    static MockClock mock(final long initialNanos) {
        return new MockClock(initialNanos);
    }

    /**
     * Mutable mock clock for testing purposes.
     * 
     * <p>This class allows test code to precisely control the passage of time,
     * enabling deterministic testing of timeouts, delays, and rate limiting.
     * 
     * <p><strong>Thread Safety:</strong> This class is NOT thread-safe.
     * Use external synchronization if accessed from multiple threads.
     * 
     * @since 1.0.0
     */
    final class MockClock implements MonoClock {
        private long currentNanos;

        /**
         * Creates a mock clock starting at the specified time.
         * 
         * @param initialNanos initial time value in nanoseconds
         */
        public MockClock(final long initialNanos) {
            this.currentNanos = initialNanos;
        }

        @Override
        public long nanoTime() {
            return currentNanos;
        }

        /**
         * Advances the clock by the specified number of nanoseconds.
         * 
         * @param nanos number of nanoseconds to advance (may be negative)
         * @return the new time value after advancing
         */
        public long advance(final long nanos) {
            this.currentNanos += nanos;
            return this.currentNanos;
        }

        /**
         * Sets the clock to an absolute time value.
         * 
         * @param nanos new time value in nanoseconds
         */
        public void set(final long nanos) {
            this.currentNanos = nanos;
        }
        
        /**
         * Advances the clock by the specified number of milliseconds.
         * 
         * <p>This is a convenience method for advancing by millisecond-scale amounts.
         * 
         * @param millis number of milliseconds to advance
         * @return the new time value after advancing
         */
        public long advanceMillis(final long millis) {
            return advance(millis * NANOS_PER_MILLI);
        }
    }
}
