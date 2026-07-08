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
 * Handler for {@code PUT /d/{d}/sequencediagram/roles/by-name/{n}}.
 * Body: {@code {"x": int, "y": int}}. Returns 200 with the moved
 * {@link SequenceClassifierRoleEntity} (entity reflects the new
 * x/y).
 */
public final class MoveRoleHandler implements IRequestHandler {

    private final SequenceDiagramService svc;

    public MoveRoleHandler(SequenceDiagramService svc) {
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
        String name = pathParams == null ? null : pathParams.get("n");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "ClassifierRole name required"));
        }
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
        Integer x = HandlerJsonHelper.intOpt(json.get("x"), 0);
        Integer y = HandlerJsonHelper.intOpt(json.get("y"), 0);
        if (x == null || y == null) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    "Both 'x' and 'y' are required"));
        }
        SequenceClassifierRoleEntity v =
                svc.setRolePosition(diagram, name, x, y);
        return ResponseEnvelope.json(200, JsonWriter.ok(EntityJson.toMap(v)));
    }
}