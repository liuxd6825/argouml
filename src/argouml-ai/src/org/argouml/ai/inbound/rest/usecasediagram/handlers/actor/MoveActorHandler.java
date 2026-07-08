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

import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UsecaseActorEntity;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code PUT /d/{d}/usecasediagram/actors/by-name/{a}}.
 * Body: {@code {"x": int, "y": int}}. Returns 200 with the moved
 * {@link UsecaseActorEntity} (entity reflects the new x/y).
 */
public final class MoveActorHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public MoveActorHandler(UseCaseDiagramService svc) {
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
        String name = pathParams == null ? null : pathParams.get("a");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Actor name required"));
        }
        Map<String, Object> json;
        try {
            json = JsonBodyReader.readMap(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    ex.getMessage()));
        }
        Object xo = json == null ? null : json.get("x");
        Object yo = json == null ? null : json.get("y");
        if (xo == null || yo == null) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    "Both 'x' and 'y' are required"));
        }
        int x, y;
        try {
            x = Integer.parseInt(xo.toString());
            y = Integer.parseInt(yo.toString());
        } catch (NumberFormatException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    "x and y must be integers"));
        }
        UsecaseActorEntity v = svc.setActorPosition(diagram, name, x, y);
        return ResponseEnvelope.json(200, JsonWriter.ok(EntityJson.toMap(v)));
    }
}