/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.usecasediagram;

import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;
import org.tigris.gef.graph.MutableGraphModel;

/**
 * UML Include relationship: UseCase A "includes" UseCase B.
 * Mapped to ArgoUML's {@code MInclude} model element.
 */
public final class IncludeOperations {

    private IncludeOperations() {
    }

    /**
     * Build an Include between two use cases on the diagram.
     *
     * @param base      the base (including) use case
     * @param inclusion the included use case
     * @return the new MInclude model element, or {@code null} if
     *         the factory refused (e.g. types are wrong)
     */
    public static Object build(Object base, Object inclusion) {
        if (base == null || inclusion == null) {
            throw new IllegalArgumentException(
                    "include requires non-null base and inclusion");
        }
        return Model.getUseCasesFactory().buildInclude(base, inclusion);
    }

    public static Object find(ArgoDiagram diagram, Object base,
                              Object inclusion) {
        if (base == null || inclusion == null) {
            return null;
        }
        return Model.getUseCasesHelper().getIncludes(base, inclusion);
    }

    /**
     * Find an Include element on the diagram by its UUID
     * (xmi.id). The Include/Extend operations do not extend
     * {@code AbstractDiagramElementOperations} (their model
     * representation is a plain MInclude, not a diagram node), so
     * we scan the diagram's graph model edges directly.
     */
    public static Object findByUuid(ArgoDiagram diagram, String uuid) {
        if (uuid == null || uuid.isEmpty() || diagram == null) {
            return null;
        }
        org.argouml.model.Facade facade = Model.getFacade();
        org.tigris.gef.graph.GraphModel gm = diagram.getGraphModel();
        if (gm == null) {
            return null;
        }
        java.util.Collection edges = gm.getEdges();
        if (edges == null) {
            return null;
        }
        for (Object edge : edges) {
            if (facade.isAInclude(edge)
                    && uuid.equals(facade.getUUID(edge))) {
                return edge;
            }
        }
        return null;
    }

    public static void delete(Object include) {
        if (include == null) {
            return;
        }
        try {
            Model.getUmlFactory().delete(include);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }
}
