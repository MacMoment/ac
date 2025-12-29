package com.macmoment.macac.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Fixed-size ring buffer (circular buffer) with O(1) push and O(1) random access.
 * 
 * <p>This data structure provides a fixed-capacity buffer that automatically
 * overwrites the oldest elements when capacity is reached. It is ideal for
 * maintaining a sliding window of recent observations.
 * 
 * <p>Key characteristics:
 * <ul>
 *   <li>Constant-time O(1) push operations</li>
 *   <li>Constant-time O(1) access by age (0 = most recent)</li>
 *   <li>Linear-time O(n) iteration (oldest to newest)</li>
 *   <li>Snapshot-based iteration (modifications during iteration are safe)</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This class uses synchronized methods for
 * thread-safe single-writer, multiple-reader scenarios. The iterator creates
 * a snapshot and is safe to use even if the buffer is modified during iteration.
 * 
 * @param <T> element type; null elements are permitted
 * @author MacAC Development Team
 * @since 1.0.0
 */
public final class RingBuffer<T> implements Iterable<T> {
    
    /** Minimum valid capacity for a ring buffer. */
    private static final int MIN_CAPACITY = 1;
    
    private final Object[] buffer;
    private final int capacity;
    private volatile int head;      // Next write position
    private volatile int size;      // Current number of elements

    /**
     * Creates a new ring buffer with the specified capacity.
     * 
     * @param capacity maximum number of elements; must be at least 1
     * @throws IllegalArgumentException if capacity is less than {@value #MIN_CAPACITY}
     */
    public RingBuffer(final int capacity) {
        if (capacity < MIN_CAPACITY) {
            throw new IllegalArgumentException(
                String.format("Capacity must be at least %d, got: %d", MIN_CAPACITY, capacity));
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
        this.head = 0;
        this.size = 0;
    }

    /**
     * Adds an element to the buffer, overwriting the oldest if at capacity.
     * 
     * <p>This operation has O(1) time complexity.
     * 
     * @param element element to add; null is permitted
     */
    public synchronized void push(final T element) {
        buffer[head] = element;
        head = (head + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    /**
     * Returns the most recently added element without removing it.
     * 
     * @return most recent element, or null if the buffer is empty
     */
    @SuppressWarnings("unchecked")
    public synchronized T peek() {
        if (size == 0) {
            return null;
        }
        final int index = (head - 1 + capacity) % capacity;
        return (T) buffer[index];
    }

    /**
     * Returns the element at the specified age (0 = most recent).
     * 
     * <p>Age 0 returns the most recently added element, age 1 returns the
     * second most recent, and so on. Age (size-1) returns the oldest element.
     * 
     * @param age age of element to retrieve (0 = newest, size-1 = oldest)
     * @return element at the specified age, or null if age is out of range
     */
    @SuppressWarnings("unchecked")
    public synchronized T get(final int age) {
        if (age < 0 || age >= size) {
            return null;
        }
        // Calculate index: start from head-1 (most recent) and go back by age
        final int index = (head - 1 - age + capacity * 2) % capacity;
        return (T) buffer[index];
    }

    /**
     * Returns the current number of elements in the buffer.
     * 
     * @return number of elements (0 to capacity)
     */
    public int size() {
        return size;
    }

    /**
     * Returns the maximum capacity of the buffer.
     * 
     * @return maximum number of elements this buffer can hold
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns true if the buffer contains no elements.
     * 
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns true if the buffer has reached its capacity.
     * 
     * <p>When full, subsequent push operations will overwrite the oldest elements.
     * 
     * @return true if the buffer contains exactly capacity elements
     */
    public boolean isFull() {
        return size == capacity;
    }

    /**
     * Removes all elements from the buffer.
     * 
     * <p>After this operation, size() returns 0 and isEmpty() returns true.
     */
    public synchronized void clear() {
        Arrays.fill(buffer, null);
        head = 0;
        size = 0;
    }

    /**
     * Returns a snapshot copy of all elements as an array.
     * 
     * <p>Elements are ordered from oldest (index 0) to newest (index size-1).
     * The returned array is a defensive copy and can be safely modified.
     * 
     * @return array copy of elements; never null, may be empty
     */
    @SuppressWarnings("unchecked")
    public synchronized Object[] toArray() {
        final Object[] result = new Object[size];
        for (int i = 0; i < size; i++) {
            final int index = (head - size + i + capacity) % capacity;
            result[i] = buffer[index];
        }
        return result;
    }

    /**
     * Returns an iterator over elements from oldest to newest.
     * 
     * <p>The iterator operates on a snapshot taken at iteration start time.
     * Modifications to the buffer during iteration will not affect the iterator.
     * 
     * @return snapshot-based iterator; never null
     */
    @Override
    public Iterator<T> iterator() {
        return new SnapshotIterator();
    }

    /**
     * Snapshot-based iterator that is immune to concurrent modifications.
     */
    private final class SnapshotIterator implements Iterator<T> {
        private final Object[] snapshot;
        private int index;

        SnapshotIterator() {
            this.snapshot = toArray();
            this.index = 0;
        }

        @Override
        public boolean hasNext() {
            return index < snapshot.length;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more elements in ring buffer iterator");
            }
            return (T) snapshot[index++];
        }
    }

    /**
     * Returns a string representation of this buffer for debugging.
     * 
     * <p>Format: "RingBuffer[oldest, ..., newest]" where elements are shown
     * from oldest to newest.
     * 
     * @return string representation; never null
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("RingBuffer[");
        final int currentSize = size;
        
        for (int i = 0; i < currentSize; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(get(currentSize - 1 - i));
        }
        
        sb.append("]");
        return sb.toString();
    }
}
