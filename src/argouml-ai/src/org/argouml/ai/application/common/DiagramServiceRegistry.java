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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.argouml.ai.domain.common.ModelKind;

/**
 * Lookup table: {@link ModelKind} to {@link DiagramService}.
 *
 * <p>Replacing an existing registration is allowed and last-wins;
 * per the MVP the bootstrap registers each kind exactly once so no
 * replacement is expected. Thread-safety note: the underlying
 * {@link HashMap} is not synchronised; registrations happen at
 * startup on the main thread and lookups are read-only thereafter.</p>
 */
public final class DiagramServiceRegistry {

    private final Map<ModelKind, DiagramService> services =
            new HashMap<ModelKind, DiagramService>();

    public void register(ModelKind kind, DiagramService svc) {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        services.put(kind, svc);
    }

    public Optional<DiagramService> forKind(ModelKind kind) {
        return Optional.ofNullable(services.get(kind));
    }

    public boolean isRegistered(ModelKind kind) {
        return services.containsKey(kind);
    }
}
