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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

import fi.iki.elonen.NanoHTTPD;
import junit.framework.TestCase;

/**
 * Tests for {@link NanoHttpAdapter}.
 *
 * <p>Uses an ephemeral port (port=0) so the OS picks a free port and
 * the test is robust against other processes holding 8080 etc.
 * Java 8 compatible: avoids {@code InputStream.readAllBytes()} which
 * only exists on JDK 9+.</p>
 */
public class TestNanoHttpAdapter extends TestCase {

    public void testStartAndStopOnEphemeralPort() throws Exception {
        NanoHTTPD server = new EchoServer("127.0.0.1", 0);
        NanoHttpAdapter adapter = new NanoHttpAdapter(server);
        adapter.start();
        try {
            int bound = adapter.boundPort();
            assertTrue("must bind to ephemeral port > 0, was " + bound,
                bound > 0);
            URL u = URI.create("http://127.0.0.1:" + bound + "/").toURL();
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(2000);
            c.setReadTimeout(2000);
            assertEquals(200, c.getResponseCode());
            byte[] body = readAll(c.getInputStream(), 16);
            assertEquals("pong", new String(body));
        } finally {
            adapter.stop();
        }
    }

    public void testStartThrowsIfBindFails() {
        NanoHTTPD first = new EchoServer("127.0.0.1", 0);
        NanoHttpAdapter a1 = new NanoHttpAdapter(first);
        a1.start();
        try {
            int port = a1.boundPort();
            NanoHTTPD second = new EchoServer("127.0.0.1", port);
            NanoHttpAdapter a2 = new NanoHttpAdapter(second);
            try {
                a2.start();
                fail("expected bind failure on already-bound port " + port);
            } catch (RuntimeException expected) {
                // NanoHTTPD throws IOException on bind failure;
                // adapter wraps that in RuntimeException
            } finally {
                a2.stop();
            }
        } finally {
            a1.stop();
        }
    }

    public void testNullServerRejected() {
        try {
            new NanoHttpAdapter(null);
            fail("expected IllegalArgumentException for null server");
        } catch (IllegalArgumentException expected) {
        }
    }

    /**
     * Read up to {@code max} bytes from the stream into a fresh array.
     * Returns a shorter array if the stream ends early.
     */
    private static byte[] readAll(InputStream in, int max) throws Exception {
        byte[] buf = new byte[max];
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total == buf.length ? buf : Arrays.copyOf(buf, total);
    }

    /** Minimal server that replies "pong" to every request. */
    private static final class EchoServer extends NanoHTTPD {
        EchoServer(String host, int port) {
            super(host, port);
        }
        @Override
        public Response serve(IHTTPSession session) {
            return newFixedLengthResponse("pong");
        }
    }
}