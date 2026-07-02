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

/**
 * The three things the dispatcher needs to turn a handler result
 * into a wire response: HTTP status, MIME type, and the serialized
 * body string. Handlers produce this; the dispatcher is the only
 * place that knows how to materialise it into a
 * {@link fi.iki.elonen.NanoHTTPD.Response}.
 *
 * <p>POJO with public final fields - no getters, no builders, no
 * validation. The factory {@link #json(int, String)} is the common
 * shape for success and error envelopes alike (both are JSON).</p>
 */
public final class ResponseEnvelope {

    public final int status;
    public final String contentType;
    public final String body;

    public ResponseEnvelope(int status, String contentType, String body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body;
    }

    /**
     * Convenience constructor for JSON responses.
     */
    public static ResponseEnvelope json(int status, String body) {
        return new ResponseEnvelope(status,
                "application/json; charset=utf-8", body);
    }
}