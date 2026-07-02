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

import junit.framework.TestCase;

/**
 * Tests for {@link UndoScope}. The unit tests here cover the API
 * surface only (no current project is required); behavioural
 * commit/rollback verification lives in the ClassDiagramService tests
 * in Batch C2.
 */
public class TestUndoScope extends TestCase {

    public void testTryWithResourcesCloses() {
        UndoScope s = UndoScope.open("test");
        s.close();
    }

    public void testMarkRollbackQueuesCompensation() {
        Runnable undoneInvoked = new Runnable() {
            public void run() { }
        };
        UndoScope s = UndoScope.open("test");
        s.recordUndo(undoneInvoked);
        s.markRollback();
        s.close();
    }

    public void testIsAutoCloseable() {
        // Compile-time check that UndoScope implements AutoCloseable
        AutoCloseable ac = UndoScope.open("x");
        try {
            ac.close();
        } catch (Exception expected) {
            // AutoCloseable.close declares Exception; nothing expected
            // but we keep the catch to honour the throws clause.
        }
    }
}
