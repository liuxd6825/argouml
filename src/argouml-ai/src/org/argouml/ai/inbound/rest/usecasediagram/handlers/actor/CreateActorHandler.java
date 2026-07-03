/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.usecasediagram.handlers.actor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.HandlerJsonHelper;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /d/{d}/usecasediagram/actors}.
 *
 * <p>Body shape:
 * <pre>
 *   { "name": "User", "x": 100, "y": 60 }
 * </pre>
 *
 * <p>Returns 201 with the new actor's name on success. Error codes:
 * 400 INVALID_NAME (empty name), 409 DUPLICATE_ACTOR (already exists),
 * 404 DIAGRAM_NOT_FOUND, 400 UNSUPPORTED_DIAGRAM_TYPE (non-use-case
 * diagram).</p>
 */
public final class CreateActorHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public CreateActorHandler(UseCaseDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
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
        int x = HandlerJsonHelper.intVal(json.get("x"), 0);
        int y = HandlerJsonHelper.intVal(json.get("y"), 0);
        UseCaseDiagramService.ActorView v = svc.createActor(diagram, name, x, y);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("name", v.name);
        out.put("x", v.x);
        out.put("y", v.y);
        return ResponseEnvelope.json(201, JsonWriter.ok(out));
    }
}
