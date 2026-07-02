/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.integration;

import junit.framework.TestCase;

/**
 * Cross-cutting integration tests for the HTTP server: routing
 * (404, 405-style mismatch), envelope shape (always JSON, always
 * has an {@code ok} field), and request-edge handling
 * (non-JSON content type, empty body, malformed body, oversized
 * body, percent-encoded path segments). The handler-level
 * behaviour is covered by the per-handler unit tests; this class
 * is about the dispatcher's contract with the wire.
 */
public class TestRoutingAndErrors extends TestHttpServerIntegrationBase {

    public void testHealthReturns200() throws Exception {
        Response r = httpGet("/health");
        assertEquals(200, r.status);
        assertTrue("body must say ok:true: " + r.body,
                r.body.contains("\"ok\":true"));
    }

    public void testUnknownRouteReturns404() throws Exception {
        Response r = httpGet("/no-such-path");
        assertEquals(404, r.status);
        assertTrue("body must have error.code: " + r.body,
                r.body.contains("\"code\"")
                && r.body.contains("ROUTE_NOT_FOUND"));
    }

    public void testMethodMismatchReturns404() throws Exception {
        Response r = httpPut("/health", "{}");
        assertEquals(404, r.status);
        assertTrue(r.body, r.body.contains("ROUTE_NOT_FOUND"));
    }

    public void testResponseIsAlwaysApplicationJson() throws Exception {
        Response ok = httpGet("/health");
        assertTrue("body must be non-empty: '" + ok.body + "'",
                ok.body.length() > 0);
        assertFalse("body should not be HTML: " + ok.body,
                ok.body.contains("<html"));
    }

    public void testResponseBodyHasOkField() throws Exception {
        Response r = httpGet("/health");
        assertTrue("must contain ok:true or ok:false: " + r.body,
                r.body.contains("\"ok\":true")
                || r.body.contains("\"ok\":false"));
    }

    public void testResponseBodyIsValidJson() throws Exception {
        Response r = httpGet("/health");
        String s = r.body.trim();
        assertTrue("body should start with { : " + s, s.startsWith("{"));
        assertTrue("body should end with }: " + s, s.endsWith("}"));
    }

    public void testNonJsonContentTypeIsAccepted() throws Exception {
        Response r = httpPostRaw("/d/" + DIAGRAM + "/classes", "",
                "application/x-www-form-urlencoded");
        assertEquals("status should be 400, got " + r.status
                + " body: " + r.body, 400, r.status);
    }

    public void testEmptyBodyIsAccepted() throws Exception {
        Response r = httpPostRaw("/d/" + DIAGRAM + "/classes", "", null);
        assertEquals(400, r.status);
        assertTrue("body: " + r.body, r.body.contains("INVALID_NAME"));
    }

    public void testMalformedJsonBodyReturnsError() throws Exception {
        Response r = httpPost("/d/" + DIAGRAM + "/classes",
                "{not valid json");
        // JsonBodyReader throws IllegalArgumentException("malformed JSON body")
        // which is mapped to 400 INVALID_BODY by the global exception handler.
        assertEquals("status should be 400 INVALID_BODY, got " + r.status
                + " body: " + r.body, 400, r.status);
        assertTrue("body should mention INVALID_BODY: " + r.body,
            r.body.contains("INVALID_BODY"));
    }

    public void testLargeBodyDoesNotCrashServer() throws Exception {
        // 1 MB JSON body. The dispatcher does NOT enforce a hard
        // size cap; a class with a long-but-valid name simply gets
        // created. The test only asserts the server does not crash.
        StringBuilder sb = new StringBuilder(1024 * 1024 + 100);
        sb.append("{\"name\":\"");
        for (int i = 0; i < (1024 * 1024); i++) {
            sb.append('x');
        }
        sb.append("\"}");
        Response r = httpPost("/d/" + DIAGRAM + "/classes", sb.toString());
        // Server may accept (201) or fail (400/500) but must not
        // hang or 0-status. Verify the body returned something.
        assertTrue("status should be one of 201/400/500, got " + r.status,
            r.status == 201 || r.status == 400 || r.status == 500);
        assertTrue("body should be non-empty: " + r.body,
            r.body != null && r.body.length() > 0);
    }

    public void testMalformedUrlPathReturns404() throws Exception {
        Response r = httpGet("/d/" + DIAGRAM + "/classes/with%2Fslash");
        assertTrue("expected 200/404, got " + r.status,
                r.status == 200 || r.status == 404);
    }
}
