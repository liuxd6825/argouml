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
 * Thrown when a requested feature is recognised but not implemented
 * in the current build (for example, editing a UML construct that
 * the MVP only renders).
 */
public class UnsupportedException extends DiagramServiceException {

    public UnsupportedException(String code, String msg) {
        super(code, msg);
    }

    @Override
    public int httpStatus() {
        return 501;
    }
}
