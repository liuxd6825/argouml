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

import java.util.Iterator;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Right-click / popup menu action: jump from a UseCase to the
 * ArgoDiagram referenced by its {@code representedDiagram}
 * tagged value. Visible only when the tag is set; the parent
 * {@code UseCaseContextPopupFactory} filters out null results
 * before they reach the menu.
 *
 * <p>Extends {@link AbstractActionNavigate} so the
 * TargetListener plumbing (auto enable/disable on selection
 * change) is inherited.</p>
 *
 * @author mkl
 */
public final class ActionNavigateRepresentedDiagram
        extends AbstractActionNavigate {

    private static final long serialVersionUID = 1L;

    private static final String TAG_NAME = "representedDiagram";

    public ActionNavigateRepresentedDiagram() {
        super("menu.popup.jump-to-represented-diagram", true);
    }

    @Override
    protected Object navigateTo(Object source) {
        return lookupRepresentedDiagram(source);
    }

    /**
     * Static helper shared with
     * {@code UseCaseContextPopupFactory}.
     * Returns the matching ArgoDiagram or null when the tag is
     * missing, empty, or its UUID doesn't match any diagram in
     * the current project.
     */
    public static ArgoDiagram lookupRepresentedDiagram(Object useCase) {
        if (useCase == null || !Model.getFacade().isAUseCase(useCase)) {
            return null;
        }
        String uuid = readTag(useCase);
        if (uuid == null || uuid.isEmpty()) {
            return null;
        }
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) {
                continue;
            }
            ArgoDiagram ad = (ArgoDiagram) d;
            if (uuid.equals(ad.getName())) {
                return ad;
            }
            Object ns = ad.getNamespace();
            if (ns != null && uuid.equals(facade.getUUID(ns))) {
                return ad;
            }
        }
        return null;
    }

    private static String readTag(Object useCase) {
        try {
            Facade facade = Model.getFacade();
            Iterator tvs = facade.getTaggedValues(useCase);
            if (tvs == null) {
                return "";
            }
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if (TAG_NAME.equals(facade.getName(tv))) {
                    Object v = facade.getValue(tv);
                    return v == null ? "" : v.toString();
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "";
    }
}
