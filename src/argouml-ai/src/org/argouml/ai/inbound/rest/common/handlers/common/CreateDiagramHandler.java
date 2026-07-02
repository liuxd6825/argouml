/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common.handlers.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /project/diagrams}. Body shape:
 * <pre>{ "name": "MyDiagram" }</pre>
 * (no kind in MVP — the registered service determines the kind;
 * only CLASS is wired in MVP via the {@code ClassDiagramService}).
 *
 * <p>Returns 201 + the new diagram's name and kind on success.
 * 400 on missing/empty name or bad JSON body; 409 if a diagram
 * with the same name already exists.</p>
 */
public final class CreateDiagramHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public CreateDiagramHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        Map<String, Object> json;
        try {
            json = JsonBodyReader.readMap(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    ex.getMessage() == null ? "malformed JSON body" : ex.getMessage()));
        }
        if (json == null || json.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    "Request body must be a JSON object"));
        }
        String name = json.get("name") == null ? null : json.get("name").toString();
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Field 'name' is required and must be non-empty"));
        }
        ClassDiagramService.DiagramHandle h = svc.createDiagram(name);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", h.name);
        // Match ListDiagramsHandler's convention: derive the kind from
        // the ArgoDiagram's concrete class simple name
        // (UMLClassDiagram -> "class"). Don't use ModelKind.wireValue()
        // here — that returns "classdiagram" and would diverge from the
        // existing list response shape.
        v.put("kind", simpleKindOf(h));
        return ResponseEnvelope.json(201, JsonWriter.ok(v));
    }

    private static String simpleKindOf(ClassDiagramService.DiagramHandle h) {
        if (h == null) {
            return "unknown";
        }
        // The handle carries the ArgoDiagram reference (via the
        // service). We don't expose the ArgoDiagram to the REST
        // layer, so the kind is derived from the canonical class
        // simple name with the "UML" prefix and "Diagram" suffix
        // stripped. Class diagram is the only kind in MVP so we
        // hardcode "class" for the create response (consistent with
        // the list handler's output for the same diagram).
        // Future diagram kinds: switch on the ArgoDiagram class.
        return "class";
    }
}
