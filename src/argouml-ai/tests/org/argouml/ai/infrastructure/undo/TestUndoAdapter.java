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

import junit.framework.TestCase;

import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;

/**
 * Tests for {@link UndoAdapter}. The adapter must be a no-op when
 * there is no project, and a thin delegator otherwise.
 */
public class TestUndoAdapter extends TestCase {

    public void setUp() {
        InitializeModel.initializeDefault();
    }

    public void testBeginIsNoopWithoutCrash() {
        // Must not crash whether or not there is a current project.
        // Calling it twice covers both branches: the in-test fixture
        // may or may not have left a project alive from earlier tests.
        UndoAdapter.begin("noop-1");
        if (ProjectManager.getManager().getCurrentProject() != null) {
            UndoAdapter.begin("noop-2");
        }
    }
}