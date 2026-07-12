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
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.util.ItemUID;

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
     * Walk every ArgoDiagram and return the ones whose ItemUID
     * (per-diagram stable UUID) matches any uuid in the use case's
     * link list. Insertion order follows the link list.
     *
     * <p>Earlier versions compared against the namespace UUID, but
     * every ArgoDiagram in a project shares the same namespace, so
     * every diagram in the project would match — the popup ended up
     * showing every diagram plus the current one. ItemUID is the
     * ArgoUML-internal per-diagram identifier (java.rmi.server.UID
     * derived) so the lookup precisely isolates the linked diagram.</p>
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
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;
            if (matches(ad, uuids)) {
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

    /**
     * True iff the diagram's {@link ItemUID} string appears in the
     * use case's link list. Replaces the previous namespace-UUID
     * comparison that matched every diagram in the project.
     */
    private static boolean matches(ArgoDiagram ad, List<String> uuids) {
        if (uuids.isEmpty()) return false;
        String myId = null;
        try {
            ItemUID uid = ad.getItemUID();
            if (uid != null) myId = uid.toString();
        } catch (RuntimeException ignored) {
            return false;
        }
        if (myId == null) return false;
        return uuids.contains(myId);
    }
}