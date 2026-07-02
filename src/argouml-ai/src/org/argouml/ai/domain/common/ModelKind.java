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

/**
 * Enumeration of UML diagram kinds the server understands. The wire
 * value is the lowercase path segment used in REST URLs (for example
 * {@code /api/v1/diagrams/classdiagram/MyClass}). YAGNI: only CLASS
 * is implemented; other kinds (usecase, sequence, activity, state,
 * deployment) slot in alongside the {@code domain.<kind>} and
 * {@code application.<kind>} diagram-service registrations.
 */
public enum ModelKind {
    CLASS("classdiagram");

    private final String wire;

    ModelKind(String wire) {
        this.wire = wire;
    }

    public String wireValue() {
        return wire;
    }

    public static ModelKind fromWireValue(String s) {
        if (s == null) {
            throw new IllegalArgumentException("wire value is null");
        }
        for (ModelKind k : values()) {
            if (k.wire.equals(s)) {
                return k;
            }
        }
        throw new IllegalArgumentException(
                "Unknown diagram kind: '" + s + "'");
    }
}
