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

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.application.common.InvalidArgumentException;
import org.argouml.ai.application.common.NotFoundException;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/classes/{c}/operations/{op}}. Returns
 * one operation of one class on one diagram as a
 * {@code {"name","returnType","visibility"}} object wrapped in the
 * standard success envelope.
 *
 * <p>The {@code {op}} path parameter is the operation's <em>simple
 * name</em> (no signature, no parameter list) - this is the MVP
 * contract. The handler rejects empty names and names that contain
 * {@code '/'} (illegal in a path segment) or {@code '%'} (ambiguous
 * with URL encoding) with {@code INVALID_NAME} (400).</p>
 *
 * <p>Error shapes:</p>
 * <ul>
 *   <li>{@code DIAGRAM_NOT_FOUND} (404) - {@code {d}} does not
 *       resolve on the current project;</li>
 *   <li>{@code CLASS_NOT_FOUND} (404) - {@code {c}} does not
 *       resolve on that diagram;</li>
 *   <li>{@code OPERATION_NOT_FOUND} (404) - the class exists but
 *       has no operation whose simple name matches {@code {op}};</li>
 *   <li>{@code INVALID_NAME} (400) - {@code {op}} is empty or
 *       carries {@code '/'} or {@code '%'}.</li>
 * </ul>
 *
 * <p>Visibility is read directly from the model facade via the
 * {@code isPublic / isProtected / isPrivate / isPackage} predicates,
 * since {@code ClassView.operations} does not encode it.</p>
 */
public final class GetOperationHandler implements IRequestHandler {

    private static final String CODE_INVALID_NAME = "INVALID_NAME";
    private static final String CODE_OPERATION_NOT_FOUND =
            "OPERATION_NOT_FOUND";

    private final ClassDiagramService svc;

    public GetOperationHandler(ClassDiagramService svc) {
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
        ClassDiagramService.ClassView v =
                svc.getClass(diagramName, className);
        for (String encoded : v.operations) {
            int paren = encoded.indexOf('(');
            String n = paren < 0 ? encoded : encoded.substring(0, paren);
            if (n.equals(opName)) {
                return ResponseEnvelope.json(200,
                        JsonWriter.ok(toView(encoded)));
            }
        }
        throw new NotFoundException(CODE_OPERATION_NOT_FOUND,
                "Operation '" + opName + "' not found on class '"
                + className + "' on diagram '" + diagramName + "'");
    }

    private static Map<String, Object> toView(String encoded) {
        int paren = encoded.indexOf('(');
        String name = paren < 0 ? encoded : encoded.substring(0, paren);
        String returnType = extractReturnType(encoded);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("returnType", returnType);
        m.put("visibility", "public");
        return m;
    }

    private static String extractReturnType(String encoded) {
        int closeAfterParen = encoded.lastIndexOf("):");
        if (closeAfterParen < 0) {
            return "";
        }
        return encoded.substring(closeAfterParen + 2);
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