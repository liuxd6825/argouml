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

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Per-user configuration for the AI sidecar HTTP client.
 *
 * <p>The settings (endpoint, API key, model, timeout) are persisted
 * as a {@link Properties} file at
 * {@code ${user.home}/.argouml/ai-config.properties}. The class
 * loads lazily on first access via the {@link #getInstance()}
 * singleton, falls back to compiled-in defaults when the file is
 * missing or partial, and lets the caller mutate the in-memory state
 * through typed setters and persist it back via {@link #save()}.
 *
 * <p>Encoding: the file is read and written with
 * {@link Properties#load(InputStream)} / {@link Properties#store},
 * which use ISO-8859-1 by default. The four fields the file holds
 * (URL, bearer token, model name, integer seconds) are ASCII in
 * practice; non-ASCII keys would be escaped as {@code \\}{@code uXXXX}
 * by {@code store}. This matches the Java 1.5-era design of the
 * module and is acceptable for an MVP.
 *
 * <p>Singleton + testability: production code calls
 * {@link #getInstance()}. Tests must NOT call {@code getInstance()}
 * because it would read the real user config and pollute static
 * state across cases; instead, tests construct
 * {@code new SidecarConfig(tempFile)} with an explicit {@link File}.
 */
public class SidecarConfig {

    public static final String DEFAULT_ENDPOINT = "http://127.0.0.1:8765";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_API_KEY = "";
    public static final int DEFAULT_TIMEOUT_SEC = 60;

    private static final String KEY_ENDPOINT = "ai.endpoint";
    private static final String KEY_API_KEY = "ai.apiKey";
    private static final String KEY_MODEL = "ai.model";
    private static final String KEY_TIMEOUT = "ai.timeoutSec";

    private static final SidecarConfig INSTANCE = new SidecarConfig();

    private final File configFile;
    private String endpoint;
    private String apiKey;
    private String model;
    private int timeoutSec;

    /**
     * Default constructor used by the singleton. Reads from
     * {@code ~/.argouml/ai-config.properties}.
     */
    public SidecarConfig() {
        this(new File(System.getProperty("user.home"),
                ".argouml" + File.separator + "ai-config.properties"));
    }

    /**
     * Test-friendly constructor: load from an explicit file. If the
     * file does not exist, defaults are used and {@link #save()}
     * will create it on demand.
     */
    public SidecarConfig(File configFile) {
        this.configFile = configFile;
        load();
    }

    /**
     * @return the process-wide singleton. Constructed lazily on
     *         first call.
     */
    public static SidecarConfig getInstance() {
        return INSTANCE;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    /**
     * Write the four current values back to the underlying file,
     * creating parent directories as needed. {@code ai.apiKey} is
     * always written (even when empty) so users can distinguish
     * "explicitly empty" from "missing key" after a round-trip.
     *
     * @throws IOException if the file cannot be written.
     */
    public void save() throws IOException {
        if (configFile == null) {
            throw new IOException(
                    "no config file is associated with this instance");
        }
        Properties p = new Properties();
        p.setProperty(KEY_ENDPOINT, endpoint == null ? "" : endpoint);
        p.setProperty(KEY_API_KEY, apiKey == null ? "" : apiKey);
        p.setProperty(KEY_MODEL, model == null ? "" : model);
        p.setProperty(KEY_TIMEOUT, Integer.toString(timeoutSec));
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(configFile);
            p.store(out, "ArgoUML AI sidecar configuration");
        } finally {
            closeQuietly(out);
        }
    }

    private void load() {
        endpoint = DEFAULT_ENDPOINT;
        apiKey = DEFAULT_API_KEY;
        model = DEFAULT_MODEL;
        timeoutSec = DEFAULT_TIMEOUT_SEC;
        if (configFile == null || !configFile.exists() || !configFile.isFile()) {
            return;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(configFile);
            Properties p = new Properties();
            p.load(in);
            String v;
            v = p.getProperty(KEY_ENDPOINT);
            if (v != null && v.length() > 0) {
                endpoint = v.trim();
            }
            v = p.getProperty(KEY_API_KEY);
            if (v != null) {
                apiKey = v.trim();
            }
            v = p.getProperty(KEY_MODEL);
            if (v != null && v.length() > 0) {
                model = v.trim();
            }
            v = p.getProperty(KEY_TIMEOUT);
            if (v != null && v.length() > 0) {
                try {
                    timeoutSec = Integer.parseInt(v.trim());
                } catch (NumberFormatException ignore) {
                    // keep default
                }
            }
        } catch (IOException ignore) {
            // file became unreadable between exists() and load();
            // keep defaults
        } finally {
            closeQuietly(in);
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignore) {
            // ignore
        }
    }
}