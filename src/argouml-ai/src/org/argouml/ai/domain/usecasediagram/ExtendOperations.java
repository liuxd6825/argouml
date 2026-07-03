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

/**
 * UML Extend relationship: UseCase A "extends" UseCase B at a
 * named extension point. Mapped to ArgoUML's {@code MExtend}.
 */
public final class ExtendOperations {

    private ExtendOperations() {
    }

    /**
     * Build an Extend between two use cases. A new extension point
     * is created on the base when {@code extensionPoint} is null or
     * empty; otherwise the named point is reused if it exists or
     * created otherwise.
     *
     * @param base            the base (extended) use case
     * @param extension       the extending use case
     * @param extensionPoint  name of the extension point; may be
     *                         null/empty (a default point is then
     *                         created)
     * @return the new MExtend model element
     */
    public static Object build(Object base, Object extension,
                               String extensionPoint) {
        if (base == null || extension == null) {
            throw new IllegalArgumentException(
                    "extend requires non-null base and extension");
        }
        Object point = null;
        if (extensionPoint != null && !extensionPoint.isEmpty()) {
            point = findOrCreateExtensionPoint(base, extensionPoint);
        }
        if (point == null) {
            return Model.getUseCasesFactory()
                    .buildExtend(base, extension);
        }
        return Model.getUseCasesFactory()
                .buildExtend(base, extension, point);
    }

    public static Object find(ArgoDiagram diagram, Object base,
                              Object extension) {
        if (base == null || extension == null) {
            return null;
        }
        return Model.getUseCasesHelper().getExtends(base, extension);
    }

    /**
     * Find an Extend element on the diagram by its UUID
     * (xmi.id). See {@link IncludeOperations#findByUuid} for the
     * rationale.
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
            if (facade.isAExtend(edge)
                    && uuid.equals(facade.getUUID(edge))) {
                return edge;
            }
        }
        return null;
    }

    public static void delete(Object extend) {
        if (extend == null) {
            return;
        }
        try {
            Model.getUmlFactory().delete(extend);
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    private static Object findOrCreateExtensionPoint(
            Object useCase, String name) {
        for (Object ep : Model.getFacade().getExtensionPoints(useCase)) {
            if (name.equals(Model.getFacade().getName(ep))) {
                return ep;
            }
        }
        return Model.getUseCasesFactory()
                .buildExtensionPoint(useCase);
    }
}
