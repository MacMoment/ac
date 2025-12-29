package com.macmoment.macac;

import com.macmoment.macac.util.RingBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RingBuffer correctness.
 */
class RingBufferTest {
    
    private RingBuffer<Integer> buffer;
    
    @BeforeEach
    void setUp() {
        buffer = new RingBuffer<>(5);
    }
    
    @Test
    void testConstructorValidCapacity() {
        RingBuffer<String> rb = new RingBuffer<>(10);
        assertEquals(10, rb.capacity());
        assertEquals(0, rb.size());
        assertTrue(rb.isEmpty());
        assertFalse(rb.isFull());
    }
    
    @Test
    void testConstructorInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(0));
        assertThrows(IllegalArgumentException.class, () -> new RingBuffer<>(-1));
    }
    
    @Test
    void testPushAndSize() {
        assertEquals(0, buffer.size());
        
        buffer.push(1);
        assertEquals(1, buffer.size());
        
        buffer.push(2);
        buffer.push(3);
        assertEquals(3, buffer.size());
        
        buffer.push(4);
        buffer.push(5);
        assertEquals(5, buffer.size());
        assertTrue(buffer.isFull());
    }
    
    @Test
    void testPushOverflow() {
        // Fill the buffer
        for (int i = 1; i <= 5; i++) {
            buffer.push(i);
        }
        assertEquals(5, buffer.size());
        assertTrue(buffer.isFull());
        
        // Push more (should overwrite oldest)
        buffer.push(6);
        assertEquals(5, buffer.size());
        
        // Oldest (1) should be gone, newest is 6
        assertEquals(Integer.valueOf(6), buffer.peek());
        assertEquals(Integer.valueOf(6), buffer.get(0));
        assertEquals(Integer.valueOf(2), buffer.get(4)); // Oldest now
    }
    
    @Test
    void testPeek() {
        assertNull(buffer.peek());
        
        buffer.push(1);
        assertEquals(Integer.valueOf(1), buffer.peek());
        
        buffer.push(2);
        assertEquals(Integer.valueOf(2), buffer.peek());
        
        buffer.push(3);
        assertEquals(Integer.valueOf(3), buffer.peek());
    }
    
    @Test
    void testGet() {
        // Push 1, 2, 3, 4, 5
        for (int i = 1; i <= 5; i++) {
            buffer.push(i);
        }
        
        // get(0) = most recent = 5
        assertEquals(Integer.valueOf(5), buffer.get(0));
        // get(1) = second most recent = 4
        assertEquals(Integer.valueOf(4), buffer.get(1));
        // get(4) = oldest = 1
        assertEquals(Integer.valueOf(1), buffer.get(4));
        
        // Out of range returns null
        assertNull(buffer.get(-1));
        assertNull(buffer.get(5));
        assertNull(buffer.get(100));
    }
    
    @Test
    void testGetAfterOverflow() {
        // Push 1-7 into buffer of size 5
        for (int i = 1; i <= 7; i++) {
            buffer.push(i);
        }
        
        // Buffer should contain 3, 4, 5, 6, 7
        assertEquals(Integer.valueOf(7), buffer.get(0)); // Most recent
        assertEquals(Integer.valueOf(6), buffer.get(1));
        assertEquals(Integer.valueOf(5), buffer.get(2));
        assertEquals(Integer.valueOf(4), buffer.get(3));
        assertEquals(Integer.valueOf(3), buffer.get(4)); // Oldest
    }
    
    @Test
    void testClear() {
        buffer.push(1);
        buffer.push(2);
        buffer.push(3);
        
        buffer.clear();
        
        assertEquals(0, buffer.size());
        assertTrue(buffer.isEmpty());
        assertNull(buffer.peek());
        assertNull(buffer.get(0));
    }
    
    @Test
    void testToArray() {
        // Empty buffer
        Object[] empty = buffer.toArray();
        assertEquals(0, empty.length);
        
        // Partial fill
        buffer.push(1);
        buffer.push(2);
        buffer.push(3);
        
        Object[] arr = buffer.toArray();
        assertEquals(3, arr.length);
        // Array is oldest to newest
        assertEquals(1, arr[0]);
        assertEquals(2, arr[1]);
        assertEquals(3, arr[2]);
    }
    
    @Test
    void testToArrayAfterOverflow() {
        // Push 1-7 into buffer of size 5
        for (int i = 1; i <= 7; i++) {
            buffer.push(i);
        }
        
        Object[] arr = buffer.toArray();
        assertEquals(5, arr.length);
        // Oldest to newest: 3, 4, 5, 6, 7
        assertEquals(3, arr[0]);
        assertEquals(4, arr[1]);
        assertEquals(5, arr[2]);
        assertEquals(6, arr[3]);
        assertEquals(7, arr[4]);
    }
    
    @Test
    void testIterator() {
        buffer.push(1);
        buffer.push(2);
        buffer.push(3);
        
        List<Integer> collected = new ArrayList<>();
        for (Integer i : buffer) {
            collected.add(i);
        }
        
        // Iterator goes oldest to newest
        assertEquals(List.of(1, 2, 3), collected);
    }
    
    @Test
    void testIteratorAfterOverflow() {
        for (int i = 1; i <= 7; i++) {
            buffer.push(i);
        }
        
        List<Integer> collected = new ArrayList<>();
        for (Integer i : buffer) {
            collected.add(i);
        }
        
        assertEquals(List.of(3, 4, 5, 6, 7), collected);
    }
    
    @Test
    void testIteratorEmpty() {
        Iterator<Integer> it = buffer.iterator();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }
    
    @Test
    void testIteratorSnapshot() {
        buffer.push(1);
        buffer.push(2);
        
        Iterator<Integer> it = buffer.iterator();
        
        // Modify buffer during iteration
        buffer.push(3);
        buffer.push(4);
        
        // Iterator should still see snapshot (1, 2)
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(1), it.next());
        assertTrue(it.hasNext());
        assertEquals(Integer.valueOf(2), it.next());
        assertFalse(it.hasNext());
    }
    
    @Test
    void testNullValues() {
        buffer.push(null);
        buffer.push(1);
        buffer.push(null);
        
        assertEquals(3, buffer.size());
        assertNull(buffer.peek()); // Most recent is null
        assertEquals(Integer.valueOf(1), buffer.get(1));
    }
    
    @Test
    void testSingleCapacity() {
        RingBuffer<String> single = new RingBuffer<>(1);
        
        assertTrue(single.isEmpty());
        
        single.push("a");
        assertEquals(1, single.size());
        assertTrue(single.isFull());
        assertEquals("a", single.peek());
        
        single.push("b");
        assertEquals(1, single.size());
        assertEquals("b", single.peek());
        
        single.push("c");
        assertEquals("c", single.peek());
    }
    
    @Test
    void testToString() {
        buffer.push(1);
        buffer.push(2);
        buffer.push(3);
        
        String str = buffer.toString();
        assertTrue(str.contains("RingBuffer"));
        assertTrue(str.contains("1"));
        assertTrue(str.contains("2"));
        assertTrue(str.contains("3"));
    }
}
