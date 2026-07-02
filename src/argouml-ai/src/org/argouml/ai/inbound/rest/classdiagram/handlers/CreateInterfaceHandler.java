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

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.model.Model;

/**
 * Handler for {@code POST /d/{d}/interfaces}. Body shape:
 * <pre>
 *   { "name":"ICloneable", "x":100, "y":100,
 *     "stereotype":null }
 * </pre>
 *
 * <p>Unlike {@code createClass}, the service returns the raw
 * interface model element (an {@code Object}) rather than a typed
 * {@code ClassElement}; the handler resolves the name back via the
 * model facade so the response shape stays
 * {@code {name:"ICloneable"}}.</p>
 */
public final class CreateInterfaceHandler implements IRequestHandler {

    private static final int DEFAULT_X = 100;
    private static final int DEFAULT_Y = 100;

    private final ClassDiagramService svc;

    public CreateInterfaceHandler(ClassDiagramService svc) {
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
        Map<String, Object> json = JsonBodyReader.readMap(body);
        String name = JsonFields.strEmpty(json.get("name"));
        int x = JsonFields.intVal(json.get("x"), DEFAULT_X);
        int y = JsonFields.intVal(json.get("y"), DEFAULT_Y);
        String stereotype = JsonFields.strEmpty(json.get("stereotype"));

        Object iface = svc.createInterface(
                diagramName, name, x, y, stereotype);
        String resolvedName = (String) Model.getFacade().getName(iface);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", resolvedName == null ? name : resolvedName);
        return ResponseEnvelope.json(201, JsonWriter.ok(v));
    }
}