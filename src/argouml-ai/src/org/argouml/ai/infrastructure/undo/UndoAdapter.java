/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.undo;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;

/**
 * Thin wrapper around {@code org.argouml.kernel.UndoManager} that
 * gracefully no-ops when there is no current project.
 *
 * <p>Note: that UndoManager only exposes {@code startInteraction};
 * the interaction boundary is implicit at the next
 * {@code startInteraction} call. See {@code UndoScope} for the
 * higher-level try-with-resources pattern that also captures
 * compensating Runnables.</p>
 */
public final class UndoAdapter {

    private UndoAdapter() { }

    /**
     * Begin a named undo interaction on the current project's
     * {@code org.argouml.kernel.UndoManager}. No-op if there is no
     * current project or the manager is absent.
     */
    public static void begin(String label) {
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p != null && p.getUndoManager() != null) {
            p.getUndoManager().startInteraction(label);
        }
    }
}