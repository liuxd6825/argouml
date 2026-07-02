/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.ops;

import java.util.List;

import org.tigris.gef.undo.Memento;

/**
 * A {@link Memento} that aggregates the entire {@link OpExecutor#apply}
 * batch into a single undoable unit. One Ctrl+Z reverts every
 * successful op in the batch (in reverse order), and one Ctrl+Y
 * re-applies them in forward order.
 *
 * <p>The {@link OpExecutor} builds one {@code AiBatchMemento} per
 * {@code apply()} call by collecting a {@link Runnable} for every op
 * that performs the inverse mutation (undo) and a matching {@link Runnable}
 * for the forward mutation (redo). On undo the {@code undos} list runs
 * last-to-first so that dependent elements are removed before their
 * dependencies (mirroring the forward order which built dependencies
 * first). On redo the {@code redos} list runs first-to-last.
 *
 * <p>The class is package-private because it is an implementation
 * detail of {@link OpExecutor}; nothing outside this package needs
 * to reference it. {@link OpExecutor} pushes it to the GEF
 * {@link org.tigris.gef.undo.UndoManager#getInstance()}, which (via
 * {@code DiagramUndoManager}) forwards it to the current project's
 * {@code org.argouml.kernel.UndoManager} as a single undoable entry.
 */
final class AiBatchMemento extends Memento {

    private final List<Runnable> undos;
    private final List<Runnable> redos;

    /**
     * @param undos the per-op inverse mutations, captured in forward
     *              (apply) order; undone last-to-first.
     * @param redos the per-op forward mutations, captured in forward
     *              (apply) order; redone first-to-last.
     */
    AiBatchMemento(List<Runnable> undos, List<Runnable> redos) {
        this.undos = undos;
        this.redos = redos;
    }

    @Override
    public void undo() {
        for (int i = undos.size() - 1; i >= 0; i--) {
            undos.get(i).run();
        }
    }

    @Override
    public void redo() {
        for (int i = 0; i < redos.size(); i++) {
            redos.get(i).run();
        }
    }

    @Override
    public void dispose() {
    }

    @Override
    public String toString() {
        return "AiBatchMemento(" + (undos == null ? 0 : undos.size())
                + " ops)";
    }
}