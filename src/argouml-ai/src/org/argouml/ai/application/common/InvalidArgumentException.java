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
 * Thrown when an inbound request is syntactically valid but violates
 * a domain precondition (unknown name, bad type, malformed id).
 */
public class InvalidArgumentException extends DiagramServiceException {

    public InvalidArgumentException(String code, String msg) {
        super(code, msg);
    }

    @Override
    public int httpStatus() {
        return 400;
    }
}
