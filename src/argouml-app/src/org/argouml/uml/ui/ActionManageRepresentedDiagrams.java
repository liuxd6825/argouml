/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.uml.ui;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import org.argouml.ui.UseCaseManageRepresentedDiagramsDialog;

/**
 * Opens the multi-link management dialog for a UseCase. Wired
 * to the use case via lookup at action-time (TargetManager).
 */
public final class ActionManageRepresentedDiagrams extends AbstractAction {
    private static final long serialVersionUID = 1L;

    public ActionManageRepresentedDiagrams() {
        super("Manage...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object useCase = org.argouml.ui.targetmanager.TargetManager.getInstance()
                .getModelTarget();
        if (useCase == null) return;
        new UseCaseManageRepresentedDiagramsDialog(useCase).setVisible(true);
    }
}