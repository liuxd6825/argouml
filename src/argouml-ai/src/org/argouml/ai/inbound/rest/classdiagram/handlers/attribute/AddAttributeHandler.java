/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.attribute;

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.model.Model;

/**
 * Handler for {@code POST /d/{d}/classes/{c}/attributes}. Body shape:
 * <pre>
 *   { "name":"id", "type":"long", "visibility":"private" }
 * </pre>
 *
 * <p>{@code name} is required (the service throws {@code INVALID_NAME}
 * 400 on null/empty); {@code type} and {@code visibility} are
 * optional - a missing/empty {@code type} yields an untyped attribute
 * (the renderer displays {@code attr : Untyped}), a missing/empty
 * {@code visibility} leaves the UML default in place. Unknown
 * {@code visibility} strings surface from the model layer as an
 * {@code IllegalArgumentException}, mapped to 500 by the
 * dispatcher.</p>
 *
 * <p>Returns 201 with the new attribute's resolved name (echoed
 * back from the model facade so the client sees what actually got
 * created) and the {@code type} value supplied by the caller, so
 * the client can immediately follow up with a {@code GET} if it
 * needs the canonical model state.</p>
 */
public final class AddAttributeHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public AddAttributeHandler(ClassDiagramService svc) {
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
        String attrName = strEmpty(json.get("name"));
        String typeName = strEmpty(json.get("type"));
        String visibility = strEmpty(json.get("visibility"));
        Object attr = svc.addAttribute(
                diagramName, className, attrName, typeName, visibility);
        String resolvedName = (String) Model.getFacade().getName(attr);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", resolvedName == null ? attrName : resolvedName);
        v.put("type", typeName);
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