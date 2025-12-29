package com.macmoment.macac.util;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Fixed-size ring buffer with O(1) push and O(n) iteration.
 * Thread-safe for single-writer, multiple-reader scenarios.
 * 
 * @param <T> Element type
 */
public final class RingBuffer<T> implements Iterable<T> {
    private final Object[] buffer;
    private final int capacity;
    private volatile int head;      // Next write position
    private volatile int size;      // Current number of elements

    /**
     * Creates a new ring buffer with the specified capacity.
     * 
     * @param capacity Maximum number of elements
     * @throws IllegalArgumentException if capacity is less than 1
     */
    public RingBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
        this.head = 0;
        this.size = 0;
    }

    /**
     * Adds an element to the buffer, overwriting the oldest if full.
     * O(1) time complexity.
     * 
     * @param element Element to add (may be null)
     */
    public synchronized void push(T element) {
        buffer[head] = element;
        head = (head + 1) % capacity;
        if (size < capacity) {
            size++;
        }
    }

    /**
     * Returns the most recently added element, or null if empty.
     * 
     * @return Most recent element or null
     */
    @SuppressWarnings("unchecked")
    public synchronized T peek() {
        if (size == 0) {
            return null;
        }
        int index = (head - 1 + capacity) % capacity;
        return (T) buffer[index];
    }

    /**
     * Returns the element at the specified age (0 = most recent).
     * 
     * @param age Age of element (0 = newest, size-1 = oldest)
     * @return Element at the specified age, or null if out of range
     */
    @SuppressWarnings("unchecked")
    public synchronized T get(int age) {
        if (age < 0 || age >= size) {
            return null;
        }
        int index = (head - 1 - age + capacity * 2) % capacity;
        return (T) buffer[index];
    }

    /**
     * Returns the current number of elements in the buffer.
     * 
     * @return Number of elements
     */
    public int size() {
        return size;
    }

    /**
     * Returns the maximum capacity of the buffer.
     * 
     * @return Maximum capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns true if the buffer is empty.
     * 
     * @return true if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns true if the buffer is at capacity.
     * 
     * @return true if full
     */
    public boolean isFull() {
        return size == capacity;
    }

    /**
     * Clears all elements from the buffer.
     */
    public synchronized void clear() {
        Arrays.fill(buffer, null);
        head = 0;
        size = 0;
    }

    /**
     * Returns a snapshot copy of all elements as an array.
     * Elements are ordered from oldest to newest.
     * 
     * @return Array copy of elements
     */
    @SuppressWarnings("unchecked")
    public synchronized Object[] toArray() {
        Object[] result = new Object[size];
        for (int i = 0; i < size; i++) {
            int index = (head - size + i + capacity) % capacity;
            result[i] = buffer[index];
        }
        return result;
    }

    /**
     * Returns an iterator over elements from oldest to newest.
     * The iterator is a snapshot and will not reflect changes during iteration.
     * 
     * @return Iterator over elements
     */
    @Override
    public Iterator<T> iterator() {
        return new RingBufferIterator();
    }

    private class RingBufferIterator implements Iterator<T> {
        private final Object[] snapshot;
        private int index;

        RingBufferIterator() {
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
                throw new NoSuchElementException();
            }
            return (T) snapshot[index++];
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("RingBuffer[");
        int currentSize = size;
        for (int i = 0; i < currentSize; i++) {
            if (i > 0) sb.append(", ");
            sb.append(get(currentSize - 1 - i));
        }
        sb.append("]");
        return sb.toString();
    }
}
