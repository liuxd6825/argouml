/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.http;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD;

/**
 * Thin lifecycle wrapper around {@link NanoHTTPD}.
 *
 * <p>NanoHTTPD's API distinguishes between construction (which only
 * records host/port) and bind (which happens on {@link #start()}).
 * Passing port {@code 0} tells the OS to pick an ephemeral port;
 * after {@link #start()} returns, callers should consult
 * {@link #boundPort()} to find the actual port chosen.</p>
 *
 * <p>This adapter exists so the rest of the application does not need
 * to know about NanoHTTPD's checked {@link IOException}, and so the
 * "let the OS pick a free port" idiom is testable from JUnit 3.</p>
 *
 * <p>Thread safety: not synchronized. Construct one instance per
 * server, call {@link #start()} once, {@link #stop()} once.</p>
 */
public final class NanoHttpAdapter {

    private final NanoHTTPD server;

    /**
     * @param server the NanoHTTPD instance to drive. Must not be null;
     *     its host/port must already be configured via the NanoHTTPD
     *     constructor.
     */
    public NanoHttpAdapter(NanoHTTPD server) {
        if (server == null) {
            throw new IllegalArgumentException("server must not be null");
        }
        this.server = server;
    }

    /**
     * Bind the server socket and start accepting requests.
     *
     * <p>If the bind fails (port in use, permission denied, etc.)
     * NanoHTTPD throws {@link IOException}; this method wraps that
     * in an unchecked {@link RuntimeException} so callers do not need
     * to declare a checked exception.</p>
     */
    public void start() {
        try {
            server.start();
        } catch (IOException ex) {
            throw new RuntimeException("failed to start HTTP server", ex);
        }
    }

    /**
     * Stop accepting new requests and close the listening socket.
     * Best-effort: any RuntimeException thrown by NanoHTTPD's stop()
     * is swallowed so callers (e.g. shutdown hooks) can proceed.
     */
    public void stop() {
        try {
            server.stop();
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    /**
     * @return the actual port the server is listening on. When the
     *     adapter was constructed with port {@code 0}, this is the
     *     ephemeral port the OS picked; otherwise it equals the port
     *     passed to the NanoHTTPD constructor. Returns 0 before
     *     {@link #start()} has been called.
     */
    public int boundPort() {
        return server.getListeningPort();
    }
}