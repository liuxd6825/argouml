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
 * <pre>
 *   {
 *     "name": "MyDiagram",
 *     "kind": "classdiagram"    // optional, default: "classdiagram"
 *   }
 * </pre>
 * Supported {@code kind} values (see {@link org.argouml.ai.domain.common.ModelKind#wireValue()}):
 * <ul>
 *   <li>{@code "classdiagram"} — UML class diagram (default)</li>
 *   <li>{@code "usecasediagram"} — UML use-case diagram</li>
 * </ul>
 *
 * <p>Returns 201 + the new diagram's name and short kind
 * (e.g. {@code "class"}, {@code "usecase"}) on success.
 * 400 on missing/empty name, bad JSON body, or unknown kind;
 * 409 if a diagram with the same name already exists.</p>
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
        // Parse optional `kind` (default: classdiagram). Throws on
        // unknown wire values so the dispatcher maps to INVALID_NAME
        // 400 with a clear message.
        org.argouml.ai.domain.common.ModelKind mk;
        Object kindRaw = json.get("kind");
        if (kindRaw == null || kindRaw.toString().isEmpty()) {
            mk = org.argouml.ai.domain.common.ModelKind.CLASS;
        } else {
            try {
                mk = org.argouml.ai.domain.common.ModelKind
                        .fromWireValue(kindRaw.toString());
            } catch (IllegalArgumentException ex) {
                return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                        "Unknown diagram kind '" + kindRaw + "'; "
                        + "supported: classdiagram, usecasediagram, sequencediagram"));
            }
        }
        ClassDiagramService.DiagramHandle h = svc.createDiagram(name, mk);
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("name", h.name);
        v.put("kind", h.kind);
        return ResponseEnvelope.json(201, JsonWriter.ok(v));
    }
}
