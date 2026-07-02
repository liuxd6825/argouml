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

import junit.framework.TestCase;

/**
 * Tests for {@link ServerConfigStore}.
 */
public class TestServerConfigStore extends TestCase {

    public void testDefaultsWhenFileMissing() {
        File tmp = new File(
            System.getProperty("java.io.tmpdir"),
            "argouml-http-test-missing.properties");
        tmp.delete();
        assertFalse("precondition: tmp file must not exist", tmp.exists());
        ServerConfigStore s = new ServerConfigStore(tmp);
        ServerConfig c = s.load();
        assertTrue(c.enabled);
        assertEquals(8766, c.port);
        assertEquals("127.0.0.1", c.bind);
        assertEquals(30, c.timeoutSec);
        assertEquals(1024 * 1024, c.maxBodyBytes);
    }

    public void testPersistAndReload() throws Exception {
        File tmp = File.createTempFile("argouml-http", ".properties");
        tmp.deleteOnExit();
        ServerConfigStore s = new ServerConfigStore(tmp);
        ServerConfig c = s.load();
        c.port = 9999;
        c.bind = "0.0.0.0";
        c.timeoutSec = 60;
        c.maxBodyBytes = 12345;
        c.enabled = false;
        s.save(c);
        ServerConfig d = s.load();
        assertEquals(9999, d.port);
        assertEquals("0.0.0.0", d.bind);
        assertEquals(60, d.timeoutSec);
        assertEquals(12345, d.maxBodyBytes);
        assertFalse(d.enabled);
    }

    public void testCorruptValuesFallBackToDefaults() throws Exception {
        File tmp = File.createTempFile("argouml-http", ".properties");
        tmp.deleteOnExit();
        java.io.FileWriter w = new java.io.FileWriter(tmp);
        try {
            w.write("http.port=not-a-number\n");
            w.write("http.timeoutSec=also-bad\n");
            w.write("http.maxBodyBytes=garbage\n");
        } finally {
            w.close();
        }
        ServerConfig c = new ServerConfigStore(tmp).load();
        assertEquals(8766, c.port);
        assertEquals(30, c.timeoutSec);
        assertEquals(1024 * 1024, c.maxBodyBytes);
    }

    public void testDefaultFilePathEndsWithHttpServerProperties() {
        File f = ServerConfigStore.defaultFile();
        assertEquals("http-server.properties", f.getName());
        assertTrue("default path should end in .properties",
            f.getName().endsWith(".properties"));
        assertEquals(".argouml", f.getParentFile().getName());
    }
}