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
import java.util.concurrent.FutureTask;

import javax.swing.SwingUtilities;

/**
 * Single-direction dispatcher from worker threads (NanoHTTPD) onto
 * the Swing EDT.
 *
 * <p>{@link #toEdt(Callable)} blocks until the task completes on the
 * EDT, propagating any exception. If already on the EDT, runs inline
 * (no deadlock).</p>
 */
public final class EdtDispatcher {

    private EdtDispatcher() { }

    public static <T> T toEdt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.call();
        }
        FutureTask<T> ft = new FutureTask<T>(task);
        SwingUtilities.invokeAndWait(ft);
        return ft.get();
    }
}