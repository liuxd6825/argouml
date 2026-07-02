/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import junit.framework.TestCase;

/**
 * Tests for {@link EdtDispatcher}.
 */
public class TestEdtDispatcher extends TestCase {

    public void testRunsOnEdtFromWorkerThread() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return;
        }
        final AtomicReference<Boolean> onEdt = new AtomicReference<Boolean>();
        Boolean result = EdtDispatcher.toEdt(new Callable<Boolean>() {
            public Boolean call() {
                onEdt.set(SwingUtilities.isEventDispatchThread());
                return Boolean.TRUE;
            }
        });
        assertNotNull(result);
        assertTrue("task should run on EDT", onEdt.get().booleanValue());
    }

    public void testReentrantOk() throws Exception {
        // Calling from EDT should not deadlock; the inline branch is hit.
        Object marker = EdtDispatcher.toEdt(new Callable<Object>() {
            public Object call() { return "ok"; }
        });
        assertEquals("ok", marker);
    }
}