/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.json;

import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Tests for the JSON envelope helpers built on {@link JsonMini}.
 */
public class TestJsonEnvelope extends TestCase {

    public void testOkEnvelopeContainsOkTrueAndData() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("x", 1);
        String s = JsonWriter.ok(data);
        assertTrue(s, s.contains("\"ok\":true"));
        assertTrue(s, s.contains("\"data\":{"));
        assertTrue(s, s.contains("\"x\":1"));
    }

    public void testOkEnvelopePrimitiveData() {
        String s = JsonWriter.ok("hello");
        assertTrue(s, s.contains("\"ok\":true"));
        assertTrue(s, s.contains("\"data\":\"hello\""));
    }

    public void testErrorEnvelopeContainsCodeAndMessage() {
        String s = JsonError.of("BAD_NAME", "bad name");
        assertTrue(s, s.contains("\"ok\":false"));
        assertTrue(s, s.contains("\"code\":\"BAD_NAME\""));
        assertTrue(s, s.contains("\"message\":\"bad name\""));
        assertTrue(s, s.contains("\"error\":{"));
    }

    public void testReadMapParsesBody() {
        String body = "{\"name\":\"Foo\",\"x\":1}";
        Map<String, Object> m = JsonBodyReader.readMap(body);
        assertNotNull(m);
        assertEquals("Foo", m.get("name"));
        assertEquals(1, m.get("x"));
    }

    public void testReadMapEmptyReturnsEmptyMap() {
        assertTrue(JsonBodyReader.readMap(null).isEmpty());
        assertTrue(JsonBodyReader.readMap("").isEmpty());
    }

    public void testReadMapMalformedThrows() {
        try {
            JsonBodyReader.readMap("{not json");
            fail("expected IllegalArgumentException for malformed body");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testReadMapNonObjectThrows() {
        try {
            JsonBodyReader.readMap("[1,2,3]");
            fail("expected IllegalArgumentException for non-object body");
        } catch (IllegalArgumentException expected) {
        }
    }
}