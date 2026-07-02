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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Minimal HTTP client for the local AI sidecar. Wraps
 * {@link HttpURLConnection} (no third-party HTTP deps), posts the
 * {@link AiRequest} JSON body to
 * {@code <endpoint>/v1/chat/completions} and parses the response
 * with {@link AiResponse#fromJson(String)}.
 *
 * <p>Header policy:
 * <ul>
 *   <li>{@code Content-Type: application/json} is always set.</li>
 *   <li>{@code Authorization: Bearer <apiKey>} is set only when
 *       {@link SidecarConfig#getApiKey()} returns a non-empty
 *       string. This lets the user run a local sidecar without
 *       auth during development.</li>
 * </ul>
 *
 * <p>Error policy:
 * <ul>
 *   <li>Any non-2xx response is reported as {@link IOException} whose
 *       message includes the status code and a (truncated) body so
 *       log output is useful when the sidecar returns an error
 *       page.</li>
 *   <li>Malformed JSON (e.g. truncated stream, syntax error) is
 *       reported as {@link IOException} with the raw body attached
 *       so the user can see what the sidecar actually sent.</li>
 *   <li>Network/timeout errors propagate as-is from
 *       {@link HttpURLConnection}.</li>
 * </ul>
 *
 * <p>Instance lifecycle: not a singleton. Production code constructs
 * {@code new AiClient(SidecarConfig.getInstance())}; tests construct
 * {@code new AiClient(new SidecarConfig(tempFile))} so the real user
 * config is never touched. The class is not threadsafe; the calling
 * layer (Task 10 chat loop) is expected to own one instance per
 * conversation.
 */
public class AiClient {

    private static final int MAX_ERROR_BODY_BYTES = 2048;
    private static final String CHARSET = "UTF-8";

    private final SidecarConfig config;

    /**
     * @param config the sidecar settings (endpoint, API key, model,
     *               timeout). Must not be {@code null}. The instance
     *               is read on every {@link #send(AiRequest)} call so
     *               live edits to the config are honoured.
     */
    public AiClient(SidecarConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    /**
     * POST the request body to the sidecar's chat-completions
     * endpoint and return the parsed response.
     *
     * @param req the request to send; must not be {@code null}.
     * @return the parsed assistant turn, never {@code null}.
     * @throws IOException on any non-2xx status, malformed JSON,
     *         network failure, or timeout.
     */
    public AiResponse send(AiRequest req) throws IOException {
        if (req == null) {
            throw new IllegalArgumentException("req must not be null");
        }
        String urlStr = config.getEndpoint();
        if (!urlStr.endsWith("/")) {
            urlStr = urlStr + "/";
        }
        urlStr = urlStr + "v1/chat/completions";

        String body = req.toJson();
        byte[] bodyBytes = body.getBytes(CHARSET);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(config.getTimeoutSec() * 1000);
            conn.setReadTimeout(config.getTimeoutSec() * 1000);
            conn.setRequestProperty("Content-Type", "application/json");
            String apiKey = config.getApiKey();
            if (apiKey != null && apiKey.length() > 0) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            OutputStream out = conn.getOutputStream();
            try {
                out.write(bodyBytes);
                out.flush();
            } finally {
                out.close();
            }

            int code = conn.getResponseCode();
            if (code < 200 || code > 299) {
                String errBody = readBodyOrEmpty(conn.getErrorStream());
                throw new IOException("HTTP " + code + " from "
                        + urlStr + ": " + truncate(errBody));
            }

            String respBody = readBodyOrEmpty(conn.getInputStream());
            try {
                return AiResponse.fromJson(respBody);
            } catch (IllegalArgumentException ex) {
                throw new IOException("malformed JSON from " + urlStr
                        + " (status " + code + "): "
                        + truncate(respBody) + " -- "
                        + ex.getMessage());
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String readBodyOrEmpty(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int n;
            while ((n = in.read(chunk)) >= 0) {
                buf.write(chunk, 0, n);
            }
            return buf.toString(CHARSET);
        } finally {
            try {
                in.close();
            } catch (IOException ignore) {
                // ignore
            }
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_ERROR_BODY_BYTES) {
            return s;
        }
        return s.substring(0, MAX_ERROR_BODY_BYTES) + "...[truncated]";
    }
}
