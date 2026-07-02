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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import junit.framework.TestCase;

import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;

/**
 * Tests for {@link Dispatcher}. Uses an inline mock {@link NanoHTTPD.IHTTPSession}
 * rather than spinning up a real server - we want to exercise the
 * (method, path) -> handler -> ResponseEnvelope -> NanoHTTPD.Response
 * translation, not NanoHTTPD's HTTP plumbing.
 */
public class TestDispatcher extends TestCase {

    public void testRejectsNullRouter() {
        try {
            new Dispatcher(null);
            fail("expected IllegalArgumentException for null router");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testResolveUnknownRouteReturns404() {
        Router r = new Router();
        r.add(Method.GET, "/health", new EchoHandler(200, "ok"));
        Dispatcher d = new Dispatcher(r);
        Response resp = d.serve(new FakeSession(NanoHTTPD.Method.GET, "/nope",
                new HashMap<String, List<String>>(),
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])));
        assertEquals(404, resp.getStatus().getRequestStatus());
        String body = readBody(resp);
        assertTrue("body should mention ROUTE_NOT_FOUND: " + body,
                body.contains("ROUTE_NOT_FOUND"));
    }

    public void testDispatchesToHandler() {
        Router r = new Router();
        r.add(Method.GET, "/health", new EchoHandler(200, "alive"));
        Dispatcher d = new Dispatcher(r);
        Response resp = d.serve(new FakeSession(NanoHTTPD.Method.GET, "/health",
                new HashMap<String, List<String>>(),
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])));
        assertEquals(200, resp.getStatus().getRequestStatus());
        assertEquals("alive", readBody(resp));
    }

    public void testDiagramServiceExceptionMapsToHttpStatus() {
        Router r = new Router();
        r.add(Method.GET, "/d/{d}",
                new ThrowingHandler(new NotFoundException("DIAGRAM_NOT_FOUND",
                        "nope")));
        Dispatcher d = new Dispatcher(r);
        Response resp = d.serve(new FakeSession(NanoHTTPD.Method.GET, "/d/D1",
                new HashMap<String, List<String>>(),
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])));
        assertEquals(404, resp.getStatus().getRequestStatus());
        String body = readBody(resp);
        assertTrue("body should contain DIAGRAM_NOT_FOUND: " + body,
                body.contains("DIAGRAM_NOT_FOUND"));
    }

    public void testInvalidArgumentExceptionMapsTo400() {
        Router r = new Router();
        r.add(Method.POST, "/d/{d}/classes",
                new ThrowingHandler(new InvalidArgumentException("BAD", "x")));
        Dispatcher d = new Dispatcher(r);
        Response resp = d.serve(new FakeSession(NanoHTTPD.Method.POST, "/d/D1/classes",
                new HashMap<String, List<String>>(),
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])));
        assertEquals(400, resp.getStatus().getRequestStatus());
    }

    public void testRuntimeExceptionMapsTo500() {
        Router r = new Router();
        r.add(Method.GET, "/x", new ThrowingHandler(
                new RuntimeException("kaboom")));
        Dispatcher d = new Dispatcher(r);
        Response resp = d.serve(new FakeSession(NanoHTTPD.Method.GET, "/x",
                new HashMap<String, List<String>>(),
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])));
        assertEquals(500, resp.getStatus().getRequestStatus());
        String body = readBody(resp);
        assertTrue("body should mention INTERNAL_ERROR: " + body,
                body.contains("INTERNAL_ERROR"));
    }

    public void testJsonBodyIsReadFromInputStream() {
        Router r = new Router();
        final List<String> seen = new ArrayList<String>();
        r.add(Method.POST, "/echo", new IRequestHandler() {
            public ResponseEnvelope handle(Map<String, String> pathParams,
                                           Map<String, String> queryParams,
                                           String body) {
                seen.add(body);
                return ResponseEnvelope.json(200, "got:" + body);
            }
        });
        Dispatcher d = new Dispatcher(r);
        String payload = "{\"x\":1}";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("content-type", "application/json");
        Response resp = d.serve(new FakeSession(NanoHTTPD.Method.POST, "/echo",
                new HashMap<String, List<String>>(),
                headers, new ByteArrayInputStream(payload.getBytes())));
        assertEquals(200, resp.getStatus().getRequestStatus());
        assertEquals("got:" + payload, readBody(resp));
        assertEquals(1, seen.size());
        assertEquals(payload, seen.get(0));
    }

    public void testNonJsonBodyReturnsEmptyBody() {
        Router r = new Router();
        final List<String> seen = new ArrayList<String>();
        r.add(Method.POST, "/echo", new IRequestHandler() {
            public ResponseEnvelope handle(Map<String, String> pathParams,
                                           Map<String, String> queryParams,
                                           String body) {
                seen.add(body);
                return ResponseEnvelope.json(200, "ok");
            }
        });
        Dispatcher d = new Dispatcher(r);
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("content-type", "application/x-www-form-urlencoded");
        Response resp = d.serve(new FakeSession(NanoHTTPD.Method.POST, "/echo",
                new HashMap<String, List<String>>(),
                headers, new ByteArrayInputStream("a=1".getBytes())));
        assertEquals(200, resp.getStatus().getRequestStatus());
        // Non-JSON content-type -> body should be the empty string
        // so handlers don't accidentally parse url-encoded input.
        assertEquals("", seen.get(0));
    }

    public void testPathParamsPropagateToHandler() {
        Router r = new Router();
        final List<Map<String, String>> captured =
                new ArrayList<Map<String, String>>();
        r.add(Method.GET, "/d/{d}", new IRequestHandler() {
            public ResponseEnvelope handle(Map<String, String> pathParams,
                                           Map<String, String> queryParams,
                                           String body) {
                captured.add(new HashMap<String, String>(pathParams));
                return ResponseEnvelope.json(200, "ok");
            }
        });
        Dispatcher d = new Dispatcher(r);
        d.serve(new FakeSession(NanoHTTPD.Method.GET, "/d/MyDiagram",
                new HashMap<String, List<String>>(),
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])));
        assertEquals(1, captured.size());
        assertEquals("MyDiagram", captured.get(0).get("d"));
    }

    public void testQueryParamsPropagateToHandler() {
        Router r = new Router();
        final List<Map<String, String>> captured =
                new ArrayList<Map<String, String>>();
        r.add(Method.GET, "/x", new IRequestHandler() {
            public ResponseEnvelope handle(Map<String, String> pathParams,
                                           Map<String, String> queryParams,
                                           String body) {
                captured.add(new HashMap<String, String>(queryParams));
                return ResponseEnvelope.json(200, "ok");
            }
        });
        Map<String, List<String>> params = new HashMap<String, List<String>>();
        List<String> vals = new ArrayList<String>();
        vals.add("hello");
        params.put("q", vals);
        Dispatcher d = new Dispatcher(r);
        d.serve(new FakeSession(NanoHTTPD.Method.GET, "/x", params,
                new HashMap<String, String>(), new ByteArrayInputStream(new byte[0])));
        assertEquals(1, captured.size());
        assertEquals("hello", captured.get(0).get("q"));
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static String readBody(Response r) {
        try {
            InputStream in = r.getData();
            if (in == null) {
                return "";
            }
            java.io.ByteArrayOutputStream buf =
                    new java.io.ByteArrayOutputStream();
            byte[] tmp = new byte[256];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            in.close();
            return new String(buf.toByteArray(), "UTF-8");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // -----------------------------------------------------------------
    // Test doubles
    // -----------------------------------------------------------------

    private static final class EchoHandler implements IRequestHandler {

        final int status;
        final String body;

        EchoHandler(int status, String body) {
            this.status = status;
            this.body = body;
        }

        public ResponseEnvelope handle(Map<String, String> pathParams,
                                       Map<String, String> queryParams,
                                       String body) {
            return ResponseEnvelope.json(status, this.body);
        }
    }

    private static final class ThrowingHandler implements IRequestHandler {

        final RuntimeException ex;

        ThrowingHandler(RuntimeException ex) {
            this.ex = ex;
        }

        public ResponseEnvelope handle(Map<String, String> pathParams,
                                       Map<String, String> queryParams,
                                       String body) {
            if (ex instanceof DiagramServiceException) {
                throw (DiagramServiceException) ex;
            }
            throw ex;
        }
    }

    /**
     * Minimal stand-in for NanoHTTPD's HTTPSession. Returns the
     * values supplied at construction time; all methods throw on
     * use paths the dispatcher does not actually exercise.
     */
    private static final class FakeSession
            implements NanoHTTPD.IHTTPSession {

        private final NanoHTTPD.Method method;
        private final String uri;
        private final Map<String, List<String>> parameters;
        private final Map<String, String> headers;
        private final InputStream inputStream;

        FakeSession(NanoHTTPD.Method method, String uri,
                    Map<String, List<String>> parameters,
                    Map<String, String> headers,
                    InputStream inputStream) {
            this.method = method;
            this.uri = uri;
            this.parameters = parameters;
            this.headers = headers;
            this.inputStream = inputStream;
        }

        public void execute() { /* unused */ }
        public NanoHTTPD.CookieHandler getCookies() { return null; }
        public Map<String, String> getHeaders() { return headers; }
        public InputStream getInputStream() { return inputStream; }
        public NanoHTTPD.Method getMethod() { return method; }
        public Map<String, String> getParms() {
            Map<String, String> flat = new HashMap<String, String>();
            if (parameters != null) {
                for (Map.Entry<String, List<String>> e
                        : parameters.entrySet()) {
                    List<String> v = e.getValue();
                    if (v != null && !v.isEmpty()) {
                        flat.put(e.getKey(), v.get(0));
                    }
                }
            }
            return flat;
        }
        public Map<String, List<String>> getParameters() {
            return parameters;
        }
        public String getQueryParameterString() { return ""; }
        public String getUri() { return uri; }
        public void parseBody(Map<String, String> files) { /* unused */ }
        public String getRemoteIpAddress() { return "127.0.0.1"; }
        public String getRemoteHostName() { return "localhost"; }
    }
}