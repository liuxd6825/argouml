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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Tests for {@link Router}.
 */
public class TestRouter extends TestCase {

    public void testAddAndResolve() {
        Router r = new Router();
        CountingHandler h = new CountingHandler("ok");
        r.add(Method.GET, "/health", h);
        Router.Resolved resolved = r.resolve("GET", "/health");
        assertNotNull(resolved);
        assertSame(h, resolved.handler);
        assertEquals(0, resolved.pathParams.size());
        // Resolve itself does not invoke the handler; the dispatcher
        // does that. Verify by calling through.
        assertEquals(0, h.invocations);
        ResponseEnvelope env = resolved.handler.handle(
                resolved.pathParams,
                new HashMap<String, String>(),
                "");
        assertEquals(1, h.invocations);
        assertEquals("ok", env.body);
    }

    public void testResolveMethodMismatchReturnsNull() {
        Router r = new Router();
        r.add(Method.GET, "/health", new CountingHandler("ok"));
        assertNull("POST must not match a GET route", r.resolve("POST", "/health"));
    }

    public void testResolvePathMismatchReturnsNull() {
        Router r = new Router();
        r.add(Method.GET, "/health", new CountingHandler("ok"));
        assertNull(r.resolve("GET", "/missing"));
    }

    public void testResolvePicksCorrectRouteWhenMultipleRegistered() {
        Router r = new Router();
        CountingHandler h1 = new CountingHandler("a");
        CountingHandler h2 = new CountingHandler("b");
        CountingHandler h3 = new CountingHandler("c");
        r.add(Method.GET, "/a", h1);
        r.add(Method.GET, "/b", h2);
        r.add(Method.GET, "/d/{d}", h3);
        Router.Resolved ra = r.resolve("GET", "/a");
        Router.Resolved rb = r.resolve("GET", "/b");
        Router.Resolved rd = r.resolve("GET", "/d/D1");
        assertSame(h1, ra.handler);
        assertSame(h2, rb.handler);
        assertSame(h3, rd.handler);
        assertEquals("D1", rd.pathParams.get("d"));
    }

    public void testResolveUnknownMethodReturnsNull() {
        Router r = new Router();
        r.add(Method.GET, "/x", new CountingHandler("ok"));
        assertNull("unknown verb should not match",
                r.resolve("PATCH", "/x"));
    }

    public void testResolveIsCaseInsensitive() {
        Router r = new Router();
        CountingHandler h = new CountingHandler("ok");
        r.add(Method.GET, "/x", h);
        assertNotNull(r.resolve("get", "/x"));
        assertNotNull(r.resolve("Get", "/x"));
        assertNotNull(r.resolve("GET", "/x"));
    }

    public void testRoutesReturnsRegisteredInOrder() {
        Router r = new Router();
        CountingHandler h = new CountingHandler("ok");
        r.add(Method.GET, "/a", h);
        r.add(Method.POST, "/b", h);
        r.add(Method.DELETE, "/c", h);
        assertEquals(3, r.routes().size());
        assertEquals(Method.GET, r.routes().get(0).method);
        assertEquals(Method.POST, r.routes().get(1).method);
        assertEquals(Method.DELETE, r.routes().get(2).method);
    }

    public void testAddNullArgsRejected() {
        Router r = new Router();
        CountingHandler h = new CountingHandler("ok");
        try {
            r.add(null, "/x", h);
            fail("expected IllegalArgumentException for null method");
        } catch (IllegalArgumentException expected) {
        }
        try {
            r.add(Method.GET, null, h);
            fail("expected IllegalArgumentException for null template");
        } catch (IllegalArgumentException expected) {
        }
        try {
            r.add(Method.GET, "/x", null);
            fail("expected IllegalArgumentException for null handler");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testFirstMatchWins() {
        Router r = new Router();
        CountingHandler first = new CountingHandler("first");
        CountingHandler second = new CountingHandler("second");
        r.add(Method.GET, "/d/{d}", first);
        r.add(Method.GET, "/d/{d}", second);
        Router.Resolved resolved = r.resolve("GET", "/d/D1");
        assertSame(first, resolved.handler);
        assertEquals("D1", resolved.pathParams.get("d"));
    }

    /**
     * Lightweight test handler that records call counts and echoes a
     * fixed payload. Avoids coupling the router tests to any of the
     * real handler implementations.
     */
    private static final class CountingHandler implements IRequestHandler {

        final String payload;
        int invocations;

        CountingHandler(String payload) {
            this.payload = payload;
        }

        public ResponseEnvelope handle(Map<String, String> pathParams,
                                       Map<String, String> queryParams,
                                       String body) {
            invocations++;
            return ResponseEnvelope.json(200, payload);
        }
    }
}