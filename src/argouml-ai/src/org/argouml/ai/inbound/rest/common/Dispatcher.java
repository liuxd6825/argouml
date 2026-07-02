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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;

import org.argouml.ai.application.common.DiagramServiceException;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.thread.EdtDispatcher;

/**
 * Bridges NanoHTTPD's request model and the {@link Router}. Concretely:
 * <ol>
 *   <li>extracts (method, path) and (query-params, body) from the
 *       NanoHTTPD session;</li>
 *   <li>asks the {@link Router} for a matching route;</li>
 *   <li>dispatches the matched handler on the Swing EDT via
 *       {@link EdtDispatcher#toEdt(Callable)} so handlers may touch
 *       ArgoUML model state without threading violations;</li>
 *   <li>translates exceptions into JSON envelopes:
 *       {@link DiagramServiceException} maps to its declared HTTP
 *       status (e.g. 400/404/409/501), anything else to 500.</li>
 * </ol>
 *
 * <p>NanoHTTPD 2.3.1 has no {@code IHandler} interface - instead,
 * {@link NanoHTTPD} is abstract and subclasses override
 * {@link NanoHTTPD#serve(NanoHTTPD.IHTTPSession)}. This class is
 * intentionally not a {@code NanoHTTPD} subclass so the dispatcher
 * stays a small plain object that the NanoHTTPD wrapper (Task 26)
 * can delegate to.</p>
 */
public final class Dispatcher {

    private final Router router;

    public Dispatcher(Router router) {
        if (router == null) {
            throw new IllegalArgumentException("router");
        }
        this.router = router;
    }

    /**
     * NanoHTTPD entry point. Called by the NanoHTTPD adapter for
     * every incoming HTTP request. Never throws; all exceptions are
     * caught and surfaced as JSON 5xx responses.
     */
    public Response serve(final NanoHTTPD.IHTTPSession session) {
        final String method = session.getMethod().name();
        final String path = session.getUri();
        try {
            Response env = EdtDispatcher.toEdt(new Callable<Response>() {
                public Response call() throws Exception {
                    return serveOnEdt(method, path, session);
                }
            });
            return env;
        } catch (ExecutionException ex) {
            // EdtDispatcher wraps the task exception in an
            // ExecutionException; the real cause is what handlers
            // raised. Unwrap before applying the HTTP mapping.
            Throwable cause = ex.getCause();
            if (cause instanceof DiagramServiceException) {
                DiagramServiceException dse =
                        (DiagramServiceException) cause;
                return jsonResponse(dse.httpStatus(),
                        JsonError.of(dse.code(), dse.getMessage()));
            }
            if (cause instanceof IllegalArgumentException) {
                // JSON body parse errors and handler-level validation
                // surface as IllegalArgumentException; map to 400
                // INVALID_BODY so clients can distinguish client-side
                // mistakes from server-side bugs.
                return jsonResponse(400, JsonError.of("INVALID_BODY",
                        cause == null ? ex.getMessage() : cause.getMessage()));
            }
            return jsonResponse(500,
                    JsonError.of("INTERNAL_ERROR",
                            cause == null ? ex.getMessage()
                                    : cause.getMessage()));
        } catch (DiagramServiceException dse) {
            return jsonResponse(dse.httpStatus(),
                    JsonError.of(dse.code(), dse.getMessage()));
        } catch (IllegalArgumentException ex) {
            // Pre-EDT exceptions (e.g., from path / body parsing) also
            // surface as IAE. Same 400 mapping as above.
            return jsonResponse(400, JsonError.of("INVALID_BODY", ex.getMessage()));
        } catch (Throwable t) {
            return jsonResponse(500,
                    JsonError.of("INTERNAL_ERROR", t.getMessage()));
        }
    }

    private Response serveOnEdt(String method, String path,
                                NanoHTTPD.IHTTPSession session)
            throws Exception {
        Router.Resolved r = router.resolve(method, path);
        if (r == null) {
            return jsonResponse(404,
                    JsonError.of("ROUTE_NOT_FOUND",
                            "No route for " + method + " " + path));
        }
        Map<String, String> queryParams = flattenQuery(session.getParameters());
        String body = readBody(session);
        ResponseEnvelope env = r.handler.handle(r.pathParams, queryParams,
                body);
        return toNanoResponse(env);
    }

    private static Map<String, String> flattenQuery(
            Map<String, List<String>> parms) {
        Map<String, String> out = new HashMap<String, String>();
        if (parms == null) {
            return out;
        }
        for (Map.Entry<String, List<String>> e : parms.entrySet()) {
            List<String> v = e.getValue();
            if (v != null && !v.isEmpty()) {
                out.put(e.getKey(), v.get(0));
            }
        }
        return out;
    }

    /**
     * Read the request body for {@code application/json} requests.
     * For other content types (or no body at all) we return an empty
     * string. The implementation reads directly from
     * {@link NanoHTTPD.IHTTPSession#getInputStream()} because
     * NanoHTTPD 2.3.1's {@code parseBody} helper only populates its
     * files map for urlencoded / multipart payloads; a raw JSON body
     * must be read off the input stream.
     */
    private static String readBody(NanoHTTPD.IHTTPSession session)
            throws java.io.IOException {
        Map<String, String> headers = session.getHeaders();
        if (headers == null) {
            return "";
        }
        String ctype = headers.get("content-type");
        if (ctype == null) {
            return "";
        }
        if (!ctype.toLowerCase().contains("application/json")) {
            return "";
        }
        InputStream in = session.getInputStream();
        if (in == null) {
            return "";
        }
        long contentLength = -1L;
        String cl = headers.get("content-length");
        if (cl != null) {
            try {
                contentLength = Long.parseLong(cl);
            } catch (NumberFormatException ignored) {
                contentLength = -1L;
            }
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[1024];
            long total = 0;
            while (true) {
                int want = tmp.length;
                if (contentLength >= 0) {
                    long remaining = contentLength - total;
                    if (remaining <= 0) {
                        break;
                    }
                    if (remaining < want) {
                        want = (int) remaining;
                    }
                }
                int n = in.read(tmp, 0, want);
                if (n < 0) {
                    break;
                }
                if (n == 0) {
                    continue;
                }
                buf.write(tmp, 0, n);
                total += n;
                if (contentLength >= 0 && total >= contentLength) {
                    break;
                }
            }
            return buf.toString("UTF-8");
        } catch (java.io.IOException ignored) {
            // best-effort; do not rethrow since the request body is
            // optional and a partial read is acceptable for JSON.
            return "";
        }
        // NOTE: do NOT close `in`. NanoHTTPD wraps the socket's input
        // stream; closing it closes the socket, which kills the
        // response we're about to send. NanoHTTPD owns the stream
        // and cleans it up at the end of the request lifecycle.
    }

    private static Response toNanoResponse(ResponseEnvelope env) {
        String body = env.body == null ? "" : env.body;
        return NanoHTTPD.newFixedLengthResponse(
                statusOf(env.status), env.contentType, body);
    }

    private static Response jsonResponse(int status, String body) {
        return NanoHTTPD.newFixedLengthResponse(statusOf(status),
                "application/json; charset=utf-8", body);
    }

    /**
     * Convert a numeric HTTP status into NanoHTTPD's IStatus. Falls
     * back to {@link NanoHTTPD.Response.Status#INTERNAL_ERROR} when
     * the code is not one of the published enum constants (e.g.
     * 499 from an upstream proxy or a custom application code).
     */
    private static NanoHTTPD.Response.Status statusOf(int code) {
        NanoHTTPD.Response.Status s =
                NanoHTTPD.Response.Status.lookup(code);
        if (s != null) {
            return s;
        }
        return NanoHTTPD.Response.Status.INTERNAL_ERROR;
    }
}