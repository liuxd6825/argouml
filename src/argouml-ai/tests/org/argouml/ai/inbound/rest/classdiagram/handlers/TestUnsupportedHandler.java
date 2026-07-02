/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.argouml.ai.application.common.UnsupportedException;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;

/**
 * Tests for {@link UnsupportedHandler}, the fallback handler that
 * the dispatcher uses when a {@code /d/...} URL matches the
 * diagram-scoped shape but the diagram kind is not yet supported
 * by the MVP. The handler always throws
 * {@link UnsupportedException} (code {@code UNSUPPORTED_DIAGRAM_KIND},
 * HTTP 501); the dispatcher maps that to a JSON error envelope
 * which the AI client can render to the user.
 */
public class TestUnsupportedHandler extends TestCase {

    public void testHandleThrowsUnsupportedException() {
        UnsupportedHandler h = new UnsupportedHandler();
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "FutureDiagram");
        try {
            h.handle(pp, new HashMap<String, String>(), "");
            fail("expected UnsupportedException");
        } catch (UnsupportedException expected) {
            assertEquals("UNSUPPORTED_DIAGRAM_KIND", expected.code());
            assertEquals(501, expected.httpStatus());
        }
    }

    public void testHandleWithEmptyPathParamsStillThrows() {
        UnsupportedHandler h = new UnsupportedHandler();
        try {
            h.handle(new HashMap<String, String>(),
                    new HashMap<String, String>(), "");
            fail("expected UnsupportedException for empty path params");
        } catch (UnsupportedException expected) {
            assertEquals("UNSUPPORTED_DIAGRAM_KIND", expected.code());
            assertEquals(501, expected.httpStatus());
        }
    }

    public void testHandleIgnoresBodyAndQueryParams() {
        UnsupportedHandler h = new UnsupportedHandler();
        Map<String, String> pp = new HashMap<String, String>();
        pp.put("d", "Whatever");
        Map<String, String> qp = new HashMap<String, String>();
        qp.put("ignored", "yes");
        String body = "{\"also\":\"ignored\"}";
        try {
            ResponseEnvelope env = h.handle(pp, qp, body);
            fail("expected UnsupportedException, got envelope status="
                    + (env == null ? "null" : String.valueOf(env.status)));
        } catch (UnsupportedException expected) {
            assertEquals("UNSUPPORTED_DIAGRAM_KIND", expected.code());
        }
    }
}
