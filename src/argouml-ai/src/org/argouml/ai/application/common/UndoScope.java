/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.application.common;

import java.util.ArrayList;
import java.util.List;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;

/**
 * Try-with-resources transaction wrapper around the project's
 * {@link org.argouml.kernel.UndoManager} plus a list of compensating
 * {@link Runnable}s.
 *
 * <p>On close the scope commits unless {@link #markRollback()} was
 * called. On rollback the registered undo Runnables run in reverse
 * order (best-effort). A second close is a no-op so the scope is
 * safe to use in nested try-with-resources blocks.</p>
 *
 * <p><b>Note on {@code endInteraction}.</b> The project UndoManager
 * interface ({@code org.argouml.kernel.UndoManager}) only exposes
 * {@code startInteraction(String)}; the actual interaction boundary
 * is implicit in the next {@code startInteraction} call (see
 * {@code DefaultUndoManager#addCommand}). This wrapper therefore
 * opens a labeled interaction on construction and leaves the
 * closing to the next interaction; compensating Runnables carry the
 * rollback semantics explicitly.</p>
 */
public final class UndoScope implements AutoCloseable {

    private final String label;
    private final List<Runnable> undos = new ArrayList<Runnable>();
    private boolean rollback;
    private boolean closed;

    private UndoScope(String label) {
        this.label = label;
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p != null && p.getUndoManager() != null) {
            p.getUndoManager().startInteraction(label);
        }
    }

    public static UndoScope open(String label) {
        return new UndoScope(label);
    }

    public void recordUndo(Runnable r) {
        if (r != null) {
            undos.add(r);
        }
    }

    public void markRollback() {
        this.rollback = true;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        Project p = ProjectManager.getManager().getCurrentProject();
        if (rollback) {
            for (int i = undos.size() - 1; i >= 0; i--) {
                try {
                    undos.get(i).run();
                } catch (RuntimeException ignored) {
                    // best-effort: one failed compensation must not
                    // stop the rest from running.
                }
            }
        }
        // No endInteraction() exists on org.argouml.kernel.UndoManager.
        // The labeled interaction started in the constructor is closed
        // implicitly by the next startInteraction() call, which carries
        // the project's normal transaction semantics.
        if (p == null || p.getUndoManager() == null) {
            return;
        }
    }
}
