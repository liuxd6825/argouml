/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.role;

import java.util.Map;

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceClassifierRoleEntity;
import org.argouml.ai.inbound.rest.common.HandlerJsonHelper;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /d/{d}/sequencediagram/roles}.
 *
 * <p>Body shape:
 * <pre>
 *   { "name": "User", "baseUuid": "...", "x": 100, "y": 60 }
 * </pre>
 *
 * <p>Returns 201 with the full {@link SequenceClassifierRoleEntity}
 * on success (entity contains {@code uuid, name, baseUuid,
 * lifelineUuid, diagramUuid, x, y}). 400 INVALID_NAME /
 * INVALID_BODY on bad input, 409 DUPLICATE_ROLE on name
 * collision, 404 DIAGRAM_NOT_FOUND / ROLE_NOT_FOUND, 400
 * UNSUPPORTED_DIAGRAM_TYPE when the named diagram is not a
 * sequence diagram.</p>
 */
public final class CreateRoleHandler implements IRequestHandler {

    private final SequenceDiagramService svc;

    public CreateRoleHandler(SequenceDiagramService svc) {
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
        String baseUuid = json.get("baseUuid") == null
                ? null : json.get("baseUuid").toString();
        int x = HandlerJsonHelper.intVal(json.get("x"), 0);
        int y = HandlerJsonHelper.intVal(json.get("y"), 0);
        SequenceClassifierRoleEntity v =
                svc.createRole(diagram, name, baseUuid, x, y);
        return ResponseEnvelope.json(201,
                JsonWriter.ok(EntityJson.toMap(v)));
    }
}