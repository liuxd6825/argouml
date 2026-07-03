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

import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Shared helpers for the per-kind service classes
 * ({@code ClassDiagramService}, {@code UseCaseDiagramService}, ...).
 *
 * <p>Promoted from inline copies in the original
 * {@code ClassDiagramService} so future kinds (state, sequence,
 * activity) can share a single source of truth for diagram
 * resolution and name validation.</p>
 */
public final class AbstractDiagramServiceHelper {

    private AbstractDiagramServiceHelper() {
    }

    /**
     * Resolve a diagram by display name. The service-layer convention
     * is to throw {@link NotFoundException} with code
     * {@code DIAGRAM_NOT_FOUND} on miss; the dispatcher maps that to
     * HTTP 404.
     */
    public static ArgoDiagram requireDiagram(String name) {
        try {
            return DiagramLocator.byName(name);
        } catch (DiagramLocator.DiagramNotFoundException ex) {
            throw new NotFoundException("DIAGRAM_NOT_FOUND", ex.getMessage());
        }
    }

    /**
     * Validate that {@code name} is non-null and non-empty. Service
     * classes call this from every entry point that takes a name
     * (create, find, delete, move) so all error envelopes are
     * uniform.
     */
    public static void requireNonEmptyName(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException("INVALID_NAME",
                    "Name must not be empty");
        }
    }
}
