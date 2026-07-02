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

/**
 * Application-layer business exception. Carries a machine-readable
 * UPPER_SNAKE code and the HTTP status the caller should return.
 *
 * <p>Subtypes are caught by the REST dispatcher and translated to
 * JSON error bodies. The exception itself is unchecked so that it
 * can propagate out of {@code domain} operations without forcing
 * every helper to declare it.</p>
 */
public abstract class DiagramServiceException extends RuntimeException {

    private final String code;

    protected DiagramServiceException(String code, String msg) {
        super(msg);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public abstract int httpStatus();
}
