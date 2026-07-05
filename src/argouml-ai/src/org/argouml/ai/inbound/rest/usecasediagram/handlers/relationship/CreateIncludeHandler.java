/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.usecasediagram.handlers.relationship;

import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.IncludeEntity;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /d/{d}/usecasediagram/includes}.
 * Body: {@code {"base": "...", "inclusion": "..."}}.
 * Returns 201 with the new {@link IncludeEntity} (entity
 * contains {@code uuid, name (null), kind="include", id,
 * baseUuid, baseName, inclusionUuid, inclusionName, diagramUuid}).
 */
public final class CreateIncludeHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public CreateIncludeHandler(UseCaseDiagramService svc) {
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
                    ex.getMessage()));
        }
        String base = strField(json, "base");
        String inclusion = strField(json, "inclusion");
        if (base == null || inclusion == null) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Both 'base' and 'inclusion' fields are required"));
        }
        IncludeEntity result =
                svc.createInclude(diagram, base, inclusion);
        return ResponseEnvelope.json(201,
                JsonWriter.ok(EntityJson.toMap(result)));
    }

    private static String strField(Map<String, Object> json, String name) {
        if (json == null) {
            return null;
        }
        Object v = json.get(name);
        return v == null ? null : v.toString();
    }
}