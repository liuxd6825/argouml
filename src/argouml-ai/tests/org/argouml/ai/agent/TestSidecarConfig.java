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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

import junit.framework.TestCase;

/**
 * Tests {@link SidecarConfig}, which persists per-user AI settings
 * (endpoint, API key, model, timeout) to
 * {@code ~/.argouml/ai-config.properties}.
 *
 * <p>Tests always construct a fresh {@code SidecarConfig(tempFile)} so
 * the real user config is never touched and tests stay isolated. They
 * MUST NOT call {@link SidecarConfig#getInstance()}.
 */
public class TestSidecarConfig extends TestCase {

    private File tmp;

    protected void setUp() throws IOException {
        // Start each test with no file present so the "missing file"
        // semantics are explicit per-test.
        tmp = File.createTempFile("argouml-ai-config-", ".properties");
        tmp.delete();
    }

    protected void tearDown() {
        if (tmp != null && tmp.exists()) {
            tmp.delete();
        }
    }

    public void testDefaultsWhenFileMissing() {
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals("http://127.0.0.1:8765", c.getEndpoint());
        assertEquals("", c.getApiKey());
        assertEquals("gpt-4o-mini", c.getModel());
        assertEquals(60, c.getTimeoutSec());
    }

    public void testDefaultsWhenFileEmpty() throws IOException {
        // Touch the file so it exists but is empty.
        new FileWriter(tmp).close();
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals("http://127.0.0.1:8765", c.getEndpoint());
        assertEquals("", c.getApiKey());
        assertEquals("gpt-4o-mini", c.getModel());
        assertEquals(60, c.getTimeoutSec());
    }

    public void testLoadFromFile() throws IOException {
        Properties p = new Properties();
        p.setProperty("ai.endpoint", "http://localhost:9000");
        p.setProperty("ai.apiKey", "sk-abc");
        p.setProperty("ai.model", "gpt-4");
        p.setProperty("ai.timeoutSec", "120");
        writeProps(p);
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals("http://localhost:9000", c.getEndpoint());
        assertEquals("sk-abc", c.getApiKey());
        assertEquals("gpt-4", c.getModel());
        assertEquals(120, c.getTimeoutSec());
    }

    public void testSaveRoundtrip() throws IOException {
        SidecarConfig a = new SidecarConfig(tmp);
        a.setEndpoint("http://example.org:1234");
        a.setApiKey("sk-test");
        a.setModel("claude-3");
        a.setTimeoutSec(45);
        a.save();
        SidecarConfig b = new SidecarConfig(tmp);
        assertEquals("http://example.org:1234", b.getEndpoint());
        assertEquals("sk-test", b.getApiKey());
        assertEquals("claude-3", b.getModel());
        assertEquals(45, b.getTimeoutSec());
    }

    public void testIgnoresUnknownKeys() throws IOException {
        Properties p = new Properties();
        p.setProperty("ai.endpoint", "http://localhost:1111");
        p.setProperty("unknown.key", "garbage");
        p.setProperty("something.else", "xyz");
        writeProps(p);
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals("http://localhost:1111", c.getEndpoint());
        // Other fields keep defaults when unknown keys are present.
        assertEquals("gpt-4o-mini", c.getModel());
        assertEquals("", c.getApiKey());
        assertEquals(60, c.getTimeoutSec());
    }

    public void testPartialProperties() throws IOException {
        Properties p = new Properties();
        p.setProperty("ai.endpoint", "http://localhost:2222");
        writeProps(p);
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals("http://localhost:2222", c.getEndpoint());
        assertEquals("", c.getApiKey());
        assertEquals("gpt-4o-mini", c.getModel());
        assertEquals(60, c.getTimeoutSec());
    }

    public void testInvalidTimeoutFallsBackToDefault() throws IOException {
        Properties p = new Properties();
        p.setProperty("ai.timeoutSec", "not-a-number");
        writeProps(p);
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals(60, c.getTimeoutSec());
    }

    public void testNegativeTimeoutIsAccepted() throws IOException {
        Properties p = new Properties();
        p.setProperty("ai.timeoutSec", "-5");
        writeProps(p);
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals(-5, c.getTimeoutSec());
    }

    public void testSettersMutateInMemoryWithoutAutoSave() {
        SidecarConfig c = new SidecarConfig(tmp);
        c.setEndpoint("http://a");
        c.setApiKey("k");
        c.setModel("m");
        c.setTimeoutSec(10);
        assertEquals("http://a", c.getEndpoint());
        assertEquals("k", c.getApiKey());
        assertEquals("m", c.getModel());
        assertEquals(10, c.getTimeoutSec());
        // No auto-save: file should still be absent.
        assertFalse(tmp.exists());
    }

    public void testSaveCreatesFile() throws IOException {
        SidecarConfig c = new SidecarConfig(tmp);
        assertFalse(tmp.exists());
        c.save();
        assertTrue(tmp.exists());
    }

    public void testSaveOverwritesExistingValues() throws IOException {
        Properties p = new Properties();
        p.setProperty("ai.endpoint", "http://old");
        writeProps(p);
        SidecarConfig c = new SidecarConfig(tmp);
        assertEquals("http://old", c.getEndpoint());
        c.setEndpoint("http://new");
        c.save();
        SidecarConfig reloaded = new SidecarConfig(tmp);
        assertEquals("http://new", reloaded.getEndpoint());
    }

    public void testSavePersistsAllFourFields() throws IOException {
        SidecarConfig c = new SidecarConfig(tmp);
        c.setEndpoint("http://x");
        c.setApiKey("k");
        c.setModel("m");
        c.setTimeoutSec(7);
        c.save();
        // Read back via raw Properties to confirm every key was written.
        Properties p = new Properties();
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(tmp);
            p.load(in);
        } finally {
            if (in != null) {
                in.close();
            }
        }
        assertEquals("http://x", p.getProperty("ai.endpoint"));
        assertEquals("k",        p.getProperty("ai.apiKey"));
        assertEquals("m",        p.getProperty("ai.model"));
        assertEquals("7",        p.getProperty("ai.timeoutSec"));
    }

    public void testApiKeyDefaultsToEmptyNotNull() {
        SidecarConfig c = new SidecarConfig(tmp);
        assertNotNull(c.getApiKey());
        assertEquals("", c.getApiKey());
    }

    private void writeProps(Properties p) throws IOException {
        Writer w = null;
        try {
            w = new FileWriter(tmp);
            p.store(w, "test");
        } finally {
            if (w != null) {
                w.close();
            }
        }
    }
}