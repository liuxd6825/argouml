/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * Reads/writes {@link ServerConfig} to/from a {@code .properties}
 * file. A missing or unreadable file yields defaults; malformed
 * numeric values fall back to the per-field defaults.
 */
public class ServerConfigStore {

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_PORT = 8766;
    private static final String DEFAULT_BIND = "127.0.0.1";
    private static final int DEFAULT_TIMEOUT_SEC = 30;
    private static final int DEFAULT_MAX_BODY_BYTES = 1024 * 1024;

    private final File file;

    public ServerConfigStore(File file) {
        this.file = file;
    }

    public ServerConfig load() {
        ServerConfig c = new ServerConfig();
        if (file == null || !file.exists()) {
            return c;
        }
        Properties p = new Properties();
        FileReader r = null;
        try {
            r = new FileReader(file);
            p.load(r);
        } catch (Exception ex) {
            return c;
        } finally {
            if (r != null) {
                try { r.close(); } catch (IOException ignored) { }
            }
        }
        c.enabled = Boolean.parseBoolean(
            p.getProperty("http.enabled", "true"));
        c.port = readInt(p, "http.port", DEFAULT_PORT);
        c.bind = p.getProperty("http.bind", DEFAULT_BIND);
        c.timeoutSec = readInt(p, "http.timeoutSec", DEFAULT_TIMEOUT_SEC);
        c.maxBodyBytes = readInt(p, "http.maxBodyBytes", DEFAULT_MAX_BODY_BYTES);
        return c;
    }

    public void save(ServerConfig c) throws IOException {
        Properties p = new Properties();
        p.setProperty("http.enabled", String.valueOf(c.enabled));
        p.setProperty("http.port", String.valueOf(c.port));
        p.setProperty("http.bind", c.bind == null ? "" : c.bind);
        p.setProperty("http.timeoutSec", String.valueOf(c.timeoutSec));
        p.setProperty("http.maxBodyBytes", String.valueOf(c.maxBodyBytes));
        FileWriter w = new FileWriter(file);
        try {
            p.store(w, "ArgoUML HTTP Server");
        } finally {
            w.close();
        }
    }

    /**
     * Default on-disk config: {@code ~/.argouml/http-server.properties}.
     * The parent directory is created if missing.
     */
    public static File defaultFile() {
        File home = new File(System.getProperty("user.home"));
        File dir = new File(home, ".argouml");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "http-server.properties");
    }

    private static int readInt(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}