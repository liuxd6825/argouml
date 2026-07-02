/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common;

import java.util.Map;

import junit.framework.TestCase;

/**
 * Tests for {@link PathMatcher}.
 */
public class TestPathMatcher extends TestCase {

    public void testSimpleLiteralMatch() {
        Map<String, String> p = PathMatcher.match("/health", "/health");
        assertNotNull(p);
        assertEquals(0, p.size());
    }

    public void testLiteralMismatch() {
        assertNull(PathMatcher.match("/health", "/other"));
    }

    public void testOneVariable() {
        Map<String, String> p = PathMatcher.match("/d/{d}/classes",
                "/d/D1/classes");
        assertNotNull(p);
        assertEquals("D1", p.get("d"));
    }

    public void testTwoVariables() {
        Map<String, String> p = PathMatcher.match("/d/{d}/classes/{c}",
                "/d/D1/classes/Order");
        assertNotNull(p);
        assertEquals("D1", p.get("d"));
        assertEquals("Order", p.get("c"));
    }

    public void testVariableLength() {
        Map<String, String> p = PathMatcher.match("/d/{d}", "/d/D1");
        assertNotNull(p);
        assertEquals("D1", p.get("d"));
    }

    public void testTrailingSlashMismatch() {
        assertNull(PathMatcher.match("/d/{d}", "/d/D1/"));
    }

    public void testNullOrEmptyReturnsNull() {
        assertNull(PathMatcher.match(null, "/x"));
        assertNull(PathMatcher.match("/x", null));
        assertNull(PathMatcher.match("/x", ""));
        assertNull(PathMatcher.match("", "/x"));
    }

    public void testNoMatchOnExtraSegments() {
        assertNull(PathMatcher.match("/d/{d}", "/d/D1/extra"));
    }

    public void testLeadingSlashOptional() {
        // Templates without a leading slash should still match
        // paths with one (and vice versa).
        assertNotNull(PathMatcher.match("d/{d}", "/d/D1"));
        assertNotNull(PathMatcher.match("/d/{d}", "d/D1"));
        assertNotNull(PathMatcher.match("d/{d}", "d/D1"));
    }

    public void testUrlEncodedVariableIsDecoded() {
        Map<String, String> p = PathMatcher.match("/d/{d}",
                "/d/My%20Diagram");
        assertNotNull(p);
        assertEquals("My Diagram", p.get("d"));
    }

    public void testInsertionOrder() {
        Map<String, String> p = PathMatcher.match(
                "/a/{a}/b/{b}/c/{c}",
                "/a/A/b/B/c/C");
        assertNotNull(p);
        // LinkedHashMap should preserve the template order.
        String[] keys = p.keySet().toArray(new String[0]);
        assertEquals("a", keys[0]);
        assertEquals("b", keys[1]);
        assertEquals("c", keys[2]);
    }

    public void testVariableAlone() {
        Map<String, String> p = PathMatcher.match("/{x}", "/foo");
        assertNotNull(p);
        assertEquals("foo", p.get("x"));
    }
}