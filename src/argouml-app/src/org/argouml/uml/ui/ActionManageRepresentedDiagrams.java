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

import org.argouml.i18n.Translator;
import org.argouml.ui.UseCaseManageRepresentedDiagramsDialog;

/**
 * Opens the multi-link management dialog for a UseCase. Wired
 * to the use case via lookup at action-time (TargetManager).
 *
 * <p>The action label is localized via {@link Translator#localize}
 * with the key {@code button.manage-rep-diagrams} (en: "Manage
 * Represented Diagrams..."; zh: "管理关联图..."). This is the same
 * pattern used by {@link ActionNavigateRepresentedDiagram} (via
 * {@code AbstractActionNavigate}) and avoids the previous bug of
 * a hardcoded English literal being shown in non-English locales.</p>
 */
public final class ActionManageRepresentedDiagrams extends AbstractAction {
    private static final long serialVersionUID = 1L;

    private static final String LOCALIZATION_KEY = "button.manage-rep-diagrams";

    public ActionManageRepresentedDiagrams() {
        super(Translator.localize(LOCALIZATION_KEY));
        putValue(javax.swing.Action.SHORT_DESCRIPTION,
                Translator.localize(LOCALIZATION_KEY));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object useCase = org.argouml.ui.targetmanager.TargetManager.getInstance()
                .getModelTarget();
        if (useCase == null) return;
        new UseCaseManageRepresentedDiagramsDialog(useCase).setVisible(true);
    }
}