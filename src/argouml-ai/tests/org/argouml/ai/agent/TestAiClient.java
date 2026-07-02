/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.TestCase;

import org.argouml.ai.tools.ClassDiagramTools;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Tests {@link AiClient} against a JDK-built-in
 * {@link com.sun.net.httpserver.HttpServer} bound to
 * {@code 127.0.0.1:0} (ephemeral port). Each test gets a fresh
 * server in {@link #setUp()} and shuts it down in
 * {@link #tearDown()} so cases are fully isolated.
 *
 * <p>Tests construct their own {@link SidecarConfig} from a temp
 * properties file so the real user config is never touched; they
 * must NOT call {@link SidecarConfig#getInstance()}.
 */
public class TestAiClient extends TestCase {

    private static final String CANNED_RESPONSE_BODY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":null,\"tool_calls\":[{\"function\":{"
            + "\"name\":\"add_class\","
            + "\"arguments\":\"{\\\"name\\\":\\\"Order\\\",\\\"x\\\":200,\\\"y\\\":100}\""
            + "}}]}}]}";

    private HttpServer server;
    private int port;
    private List<String> receivedBodies;
    private List<Map<String, List<String>>> receivedHeaders;
    private int responseStatus;
    private String responseBody;

    protected void setUp() throws Exception {
        super.setUp();
        receivedBodies = new ArrayList<String>();
        receivedHeaders = new ArrayList<Map<String, List<String>>>();
        responseStatus = 200;
        responseBody = CANNED_RESPONSE_BODY;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/v1/chat/completions", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                byte[] body = readAll(ex.getRequestBody());
                synchronized (receivedBodies) {
                    receivedBodies.add(new String(body, "UTF-8"));
                    receivedHeaders.add(Collections.unmodifiableMap(
                            ex.getRequestHeaders()));
                }
                byte[] resp = responseBody.getBytes("UTF-8");
                ex.sendResponseHeaders(responseStatus, resp.length);
                ex.getResponseBody().write(resp);
                ex.close();
            }
        });
        server.start();
    }

    protected void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        super.tearDown();
    }

    public void testSendReturnsParsedResponse() throws Exception {
        SidecarConfig config = newConfig("test-key");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel(config.getModel());
        req.addMessage("user", "add a class");
        req.setTools(ClassDiagramTools.all());

        AiResponse resp = client.send(req);

        assertNotNull(resp);
        assertEquals(1, resp.getToolCalls().size());
        assertEquals("add_class", resp.getToolCalls().get(0).getName());

        synchronized (receivedBodies) {
            assertEquals(1, receivedBodies.size());
            String body = receivedBodies.get(0);
            assertTrue("body should contain model, was: " + body,
                    body.contains("\"model\":\"test-model\""));
            assertTrue("body should contain user role, was: " + body,
                    body.contains("\"role\":\"user\""));
            assertTrue("body should contain add_class tool, was: " + body,
                    body.contains("\"name\":\"add_class\""));
        }
    }

    public void testSendPostSendsContentTypeJson() throws Exception {
        SidecarConfig config = newConfig("k");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");

        client.send(req);

        synchronized (receivedHeaders) {
            assertEquals(1, receivedHeaders.size());
            List<String> ct = receivedHeaders.get(0).get("Content-type");
            if (ct == null) {
                ct = receivedHeaders.get(0).get("Content-Type");
            }
            assertNotNull("Content-Type header missing", ct);
            assertTrue("Content-Type should be JSON, was: " + ct.get(0),
                    ct.get(0).toLowerCase().contains("application/json"));
        }
    }

    public void testSendIncludesAuthHeader() throws Exception {
        SidecarConfig config = newConfig("test-key");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");

        client.send(req);

        synchronized (receivedHeaders) {
            List<String> auth = receivedHeaders.get(0).get("Authorization");
            assertNotNull("Authorization header missing", auth);
            assertEquals("Bearer test-key", auth.get(0));
        }
    }

    public void testSendOmitsAuthHeaderWhenApiKeyEmpty() throws Exception {
        SidecarConfig config = newConfig("");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");

        client.send(req);

        synchronized (receivedHeaders) {
            List<String> auth = receivedHeaders.get(0).get("Authorization");
            assertNull("Authorization header should be absent", auth);
        }
    }

    public void testSendThrowsOn4xx() throws Exception {
        responseStatus = 400;
        responseBody = "{\"error\":\"bad request\"}";

        SidecarConfig config = newConfig("k");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");

        try {
            client.send(req);
            fail("expected IOException for HTTP 400");
        } catch (IOException expected) {
            String msg = expected.getMessage();
            assertNotNull(msg);
            assertTrue("message should mention 400, was: " + msg,
                    msg.contains("400"));
            assertTrue("message should include body, was: " + msg,
                    msg.contains("bad request"));
        }
    }

    public void testSendThrowsOn5xx() throws Exception {
        responseStatus = 500;
        responseBody = "internal server error";

        SidecarConfig config = newConfig("k");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");

        try {
            client.send(req);
            fail("expected IOException for HTTP 500");
        } catch (IOException expected) {
            String msg = expected.getMessage();
            assertNotNull(msg);
            assertTrue("message should mention 500, was: " + msg,
                    msg.contains("500"));
            assertTrue("message should include body, was: " + msg,
                    msg.contains("internal server error"));
        }
    }

    public void testSendThrowsOnMalformedJson() throws Exception {
        responseStatus = 200;
        responseBody = "{not valid json}";

        SidecarConfig config = newConfig("k");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "x");

        try {
            client.send(req);
            fail("expected IOException for malformed JSON");
        } catch (IOException expected) {
            String msg = expected.getMessage();
            assertNotNull(msg);
            assertTrue("message should reference malformed body, was: " + msg,
                    msg.contains("not valid json"));
        }
    }

    public void testSendPostsJsonBodyMatchingRequest() throws Exception {
        SidecarConfig config = newConfig("k");
        AiClient client = new AiClient(config);

        AiRequest req = new AiRequest();
        req.setModel("m");
        req.addMessage("user", "hello");
        req.addMessage("assistant", "hi");

        client.send(req);

        synchronized (receivedBodies) {
            assertEquals(1, receivedBodies.size());
            String body = receivedBodies.get(0);
            assertTrue("body should contain model, was: " + body,
                    body.contains("\"model\":\"m\""));
            assertTrue("body should contain first user message, was: " + body,
                    body.contains("\"content\":\"hello\""));
            assertTrue("body should contain assistant message, was: " + body,
                    body.contains("\"content\":\"hi\""));
        }
    }

    private SidecarConfig newConfig(String apiKey) throws IOException {
        File tmp = File.createTempFile("argouml-ai-client-", ".properties");
        tmp.deleteOnExit();
        Properties p = new Properties();
        p.setProperty("ai.endpoint", "http://127.0.0.1:" + port);
        p.setProperty("ai.apiKey", apiKey);
        p.setProperty("ai.model", "test-model");
        p.setProperty("ai.timeoutSec", "10");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            p.store(out, "test");
        } finally {
            out.close();
        }
        SidecarConfig cfg = new SidecarConfig(tmp);
        // The temp file is no longer needed once the config has loaded.
        tmp.delete();
        return cfg;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) >= 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }
}
