/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.domain.common;

import java.util.List;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Resolves a diagram by display name in the current project. The
 * single point of truth for "name -&gt; ArgoDiagram" so the
 * application layer never touches the kernel API directly. Misses
 * surface as the nested {@link DiagramNotFoundException} which the
 * REST adapter maps to HTTP 404.
 */
public final class DiagramLocator {

    private DiagramLocator() {
    }

    /**
     * Look up a diagram by its display name on the current project.
     *
     * @param name the diagram name; must be non-null and non-empty
     * @return the matching {@link ArgoDiagram}
     * @throws DiagramNotFoundException if no current project is set,
     *         the name is null/empty, or no diagram with that name
     *         exists in the current project
     */
    public static ArgoDiagram byName(String name) {
        if (name == null || name.isEmpty()) {
            throw new DiagramNotFoundException("(null or empty)");
        }
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p == null) {
            throw new DiagramNotFoundException("(no current project)");
        }
        List<ArgoDiagram> diagrams = p.getDiagramList();
        for (ArgoDiagram ad : diagrams) {
            if (name.equals(ad.getName())) {
                return ad;
            }
        }
        throw new DiagramNotFoundException(name);
    }

    /**
     * @return the display name of {@code d}, or {@code null} if
     *         {@code d} is null
     */
    public static String nameOf(ArgoDiagram d) {
        return d == null ? null : d.getName();
    }

    /**
     * Thrown when a named diagram cannot be resolved against the
     * current project. Callers map this to a 404 response.
     */
    @SuppressWarnings("serial")
    public static class DiagramNotFoundException extends RuntimeException {
        public DiagramNotFoundException(String name) {
            super("diagram not found: " + name);
        }
    }
}
