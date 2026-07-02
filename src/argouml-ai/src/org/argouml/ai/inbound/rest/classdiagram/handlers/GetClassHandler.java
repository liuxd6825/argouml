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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/classes/{c}}. Returns one
 * {@link ClassDiagramService.ClassView} wrapped in the standard
 * success envelope. Throws {@code CLASS_NOT_FOUND} (mapped to 404)
 * when the class is absent.
 */
public final class GetClassHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public GetClassHandler(ClassDiagramService svc) {
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
        ClassDiagramService.ClassView v =
                svc.getClass(diagramName, className);
        return ResponseEnvelope.json(200, JsonWriter.ok(toView(v)));
    }

    private static Map<String, Object> toView(
            ClassDiagramService.ClassView v) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", v.name);
        m.put("isAbstract", Boolean.valueOf(v.isAbstract));
        m.put("stereotypeNames",
                new ArrayList<String>(v.stereotypeNames));
        m.put("attributes", new ArrayList<String>(v.attributes));
        m.put("operations", new ArrayList<String>(v.operations));
        m.put("x", Integer.valueOf(v.x));
        m.put("y", Integer.valueOf(v.y));
        return m;
    }
}