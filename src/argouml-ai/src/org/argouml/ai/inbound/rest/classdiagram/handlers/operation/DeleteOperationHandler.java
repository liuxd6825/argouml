/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.operation;

import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;

/**
 * Handler for {@code DELETE /d/{d}/classes/{c}/operations/{op}}.
 * Returns 204 with an empty body on success.
 *
 * <p>The {@code {op}} path parameter is the operation's simple name
 * (no signature). Names containing {@code '/'} (illegal in a path
 * segment) or {@code '%'} (ambiguous with URL encoding) yield
 * {@code INVALID_NAME} (400).</p>
 *
 * <p>Errors:</p>
 * <ul>
 *   <li>{@code INVALID_NAME} (400) - empty or illegal character
 *       ({@code '/'} or {@code '%'}) in the captured {@code {op}};</li>
 *   <li>{@code DIAGRAM_NOT_FOUND} (404);</li>
 *   <li>{@code CLASS_NOT_FOUND} (404) - re-used by the service both
 *       for a missing class AND for a missing operation on an
 *       existing class. The dedicated {@code OPERATION_NOT_FOUND}
 *       code exists in the read path (see {@link GetOperationHandler})
 *       but the delete path keeps the service's existing behaviour
 *       to mirror {@code DeleteAttributeHandler}.</li>
 * </ul>
 */
public final class DeleteOperationHandler implements IRequestHandler {

    private static final String CODE_INVALID_NAME = "INVALID_NAME";

    private final ClassDiagramService svc;

    public DeleteOperationHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagramName = pathParams == null ? null : pathParams.get("d");
        String className = pathParams == null ? null : pathParams.get("c");
        String opName = pathParams == null ? null : pathParams.get("op");
        requireValidName(opName);
        svc.deleteOperation(diagramName, className, opName);
        return ResponseEnvelope.json(204, "");
    }

    private static void requireValidName(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Operation name must not be empty");
        }
        if (name.indexOf('/') >= 0 || name.indexOf('%') >= 0) {
            throw new InvalidArgumentException(CODE_INVALID_NAME,
                    "Operation name must not contain '/' or '%'");
        }
    }
}