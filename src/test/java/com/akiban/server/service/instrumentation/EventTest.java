package com.akiban.server.service.instrumentation;

import com.akiban.util.Tap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EventTest {
    
    @Test
    public void testSimpleUse() {
        /* construct event that is disabled by default */
        Event first = new EventImpl("test", 1);
        assertEquals(false, first.isEnabled());
        assertEquals(0, first.getLastDuration());
        first.start();
        first.stop();
        /* event should not be timed if not enabled */
        assertEquals(0, first.getLastDuration());
        first.enable();
        assertEquals(true, first.isEnabled());
        first.start();
        first.stop();
        assertTrue(first.getLastDuration() > 0);
        first.reset();
        assertEquals(0, first.getLastDuration());
        /* construct event that is enabled by default */
        Event second = new EventImpl("test", 2, true);
        assertEquals(true, second.isEnabled());
        assertEquals(0, second.getTotalTime());
        second.start();
        first.start();
        first.stop();
        second.stop();
        assertTrue(first.getTotalTime() > 0);
        assertTrue(second.getTotalTime() > 0);
        assertTrue(second.getTotalTime() > first.getTotalTime());
    }
    
    @Test
    public void testEmptyName() {
        Event first = new EventImpl(null, 1, true);
        first.start();
        first.stop();
        assertTrue(first.getTotalTime() > 0);
    }

}
