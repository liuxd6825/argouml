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

import java.util.ArrayList;
import java.util.List;

import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

public final class ActionNavigateRepresentedDiagram extends AbstractActionNavigate {
    private static final long serialVersionUID = 1L;

    public ActionNavigateRepresentedDiagram() {
        super("menu.popup.jump-to-represented-diagram", true);
    }

    @Override
    protected Object navigateTo(Object source) {
        List<ArgoDiagram> all = lookupAllRepresentedDiagrams(source);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Static: walk every ArgoDiagram and return the ones whose
     * name or namespace UUID matches any uuid in the use case's
     * link list. Insertion order follows the link list.
     */
    public static List<ArgoDiagram> lookupAllRepresentedDiagrams(Object useCase) {
        List<ArgoDiagram> result = new ArrayList<ArgoDiagram>();
        if (useCase == null || !Model.getFacade().isAUseCase(useCase)) {
            return result;
        }
        List<String> uuids = UseCaseOperations.getRepresentedDiagrams(useCase);
        if (uuids.isEmpty()) return result;
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) return result;
        Facade facade = Model.getFacade();
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;
            if (matches(ad, uuids, facade)) {
                result.add(ad);
            }
        }
        return result;
    }

    /** Legacy single-diagram helper - returns the first match or null. */
    public static ArgoDiagram lookupRepresentedDiagram(Object useCase) {
        List<ArgoDiagram> all = lookupAllRepresentedDiagrams(useCase);
        return all.isEmpty() ? null : all.get(0);
    }

    private static boolean matches(ArgoDiagram ad, List<String> uuids, Facade facade) {
        for (String uuid : uuids) {
            if (uuid.equals(ad.getName())) return true;
            Object ns = ad.getNamespace();
            if (ns != null && uuid.equals(facade.getUUID(ns))) return true;
        }
        return false;
    }
}