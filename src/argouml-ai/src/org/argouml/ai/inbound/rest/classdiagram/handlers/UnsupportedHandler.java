/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers;

import java.util.Map;

import org.argouml.ai.application.common.UnsupportedException;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;

/**
 * Fallback handler for routes that match the diagram-scoped URL
 * shape ({@code /d/.../...}) but belong to a diagram kind the MVP
 * does not support (e.g. a future use-case diagram request
 * before the use-case handlers ship). Always throws
 * {@link UnsupportedException}, which the dispatcher maps to
 * 501 + {@code UNSUPPORTED_DIAGRAM_KIND}.
 *
 * <p>Register this handler LAST for any diagram kind the dispatcher
 * recognises as "shape valid, kind unknown" so the route resolves
 * but the body tells the client the feature is not yet built.</p>
 */
public final class UnsupportedHandler implements IRequestHandler {

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagramName = pathParams == null ? null : pathParams.get("d");
        throw new UnsupportedException("UNSUPPORTED_DIAGRAM_KIND",
                "REST editing for diagram kind on '" + diagramName
                + "' is not supported in this build");
    }
}