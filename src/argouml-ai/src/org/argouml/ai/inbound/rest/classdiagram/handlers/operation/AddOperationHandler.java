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
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.model.Model;

/**
 * Handler for {@code POST /d/{d}/classes/{c}/operations}. Body shape:
 * <pre>
 *   { "name":"save", "returnType":"void", "visibility":"public" }
 * </pre>
 *
 * <p>{@code name} is required (the service throws {@code INVALID_NAME}
 * 400 on null/empty). {@code returnType} and {@code visibility} are
 * optional - a missing/empty {@code returnType} yields an operation
 * whose return type defaults to {@code void} (per
 * {@code OperationOperations.build}), and a missing/empty
 * {@code visibility} leaves the UML default in place (public).
 * Unknown {@code visibility} strings surface from the model layer as
 * an {@code IllegalArgumentException}, mapped to 500 by the
 * dispatcher.</p>
 *
 * <p>Returns 201 with the new operation's resolved name (echoed back
 * from the model facade so the client sees what actually got
 * created), the {@code returnType} value supplied by the caller, and
 * the {@code visibility} value supplied by the caller. Clients can
 * follow up with {@code GET .../operations/{name}} to confirm the
 * canonical model state.</p>
 */
public final class AddOperationHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public AddOperationHandler(ClassDiagramService svc) {
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
        Map<String, Object> json = JsonBodyReader.readMap(body);
        String opName = strEmpty(json.get("name"));
        String returnType = strEmpty(json.get("returnType"));
        String visibility = strEmpty(json.get("visibility"));
        Object op = svc.addOperation(
                diagramName, className, opName, returnType, visibility);
        String resolvedName = (String) Model.getFacade().getName(op);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", resolvedName == null ? opName : resolvedName);
        v.put("returnType", returnType == null ? "" : returnType);
        v.put("visibility", visibility == null ? "public" : visibility);
        return ResponseEnvelope.json(201, JsonWriter.ok(v));
    }

    private static String strEmpty(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString();
        return (s.isEmpty()) ? null : s;
    }
}