package com.macmoment.macac.util;

/**
 * Interface for monotonic time sources.
 * Allows for testing with mock time and potential native implementations.
 */
public interface MonoClock {
    
    /**
     * Returns the current monotonic time in nanoseconds.
     * This time is relative to an arbitrary origin and should only
     * be used for measuring elapsed time.
     * 
     * @return Current monotonic time in nanoseconds
     */
    long nanoTime();

    /**
     * Returns the current monotonic time in milliseconds.
     * 
     * @return Current monotonic time in milliseconds
     */
    default long milliTime() {
        return nanoTime() / 1_000_000L;
    }

    /**
     * Default implementation using System.nanoTime().
     */
    MonoClock SYSTEM = System::nanoTime;

    /**
     * Creates a mock clock for testing.
     * 
     * @param initialNanos Initial time value
     * @return Mock clock instance
     */
    static MockClock mock(long initialNanos) {
        return new MockClock(initialNanos);
    }

    /**
     * Mutable clock for testing purposes.
     */
    class MockClock implements MonoClock {
        private long currentNanos;

        public MockClock(long initialNanos) {
            this.currentNanos = initialNanos;
        }

        @Override
        public long nanoTime() {
            return currentNanos;
        }

        public void advance(long nanos) {
            this.currentNanos += nanos;
        }

        public void set(long nanos) {
            this.currentNanos = nanos;
        }
    }
}
