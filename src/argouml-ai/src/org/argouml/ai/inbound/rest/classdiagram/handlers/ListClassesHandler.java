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
 * Handler for {@code GET /d/{d}/classes}. Returns the read shape
 * ({@link ClassDiagramService.ClassView}) for every class on the
 * named diagram. The response is wrapped in the standard
 * {@code {ok:true, data:[...]}} envelope.
 *
 * <p>Read-only; does not open an {@code UndoScope} on the model.</p>
 */
public final class ListClassesHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public ListClassesHandler(ClassDiagramService svc) {
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
        List<ClassDiagramService.ClassView> views =
                svc.listClasses(diagramName);
        List<Map<String, Object>> out =
                new ArrayList<Map<String, Object>>(views.size());
        for (ClassDiagramService.ClassView v : views) {
            out.add(toView(v));
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
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