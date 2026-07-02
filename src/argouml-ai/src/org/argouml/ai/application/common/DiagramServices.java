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

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.domain.common.ModelKind;

/**
 * Process-wide singleton access to the {@link DiagramServiceRegistry}
 * and to the {@link ClassDiagramService} specifically.
 *
 * <p>The MVP registers exactly one diagram service: the class-diagram
 * service. Future diagram kinds will be registered here alongside
 * {@code ClassDiagramService} when their {@code application.<kind>}
 * sub-package and {@code domain.<kind>} sub-package land.</p>
 *
 * <p>Initialization happens in a {@code static} block so the registry
 * is populated the first time any caller references the class; no
 * external bootstrap step is required.</p>
 *
 * <p>Thread safety: relies on {@link DiagramServiceRegistry}'s
 * single-thread-at-startup contract. The class is effectively
 * immutable after class-loading.</p>
 */
public final class DiagramServices {

    private static final DiagramServiceRegistry REG = new DiagramServiceRegistry();

    static {
        REG.register(ModelKind.CLASS, new ClassDiagramService());
    }

    private DiagramServices() {
    }

    public static DiagramServiceRegistry registry() {
        return REG;
    }

    /**
     * Convenience accessor for the class-diagram service. Equivalent
     * to {@code registry().forKind(ModelKind.CLASS).get()} but
     * avoids the {@link java.util.Optional} dance at call sites and
     * fails fast (NPE) when the registration has been removed, which
     * is the signal the caller wants at startup.
     */
    public static ClassDiagramService classSvc() {
        return (ClassDiagramService) REG.forKind(ModelKind.CLASS).get();
    }
}