/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UsecaseUseCaseEntity;
import org.argouml.ai.inbound.rest.common.HandlerJsonHelper;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /d/{d}/usecasediagram/usecases}.
 *
 * <p>Body shape:
 * <pre>
 *   { "name": "Login", "description": "...", "x": 200, "y": 100 }
 * </pre>
 *
 * <p>Returns 201 with the full {@link UsecaseUseCaseEntity} on success
 * (entity contains {@code uuid, name, kind, description,
 * diagramUuid, x, y}).</p>
 *
 * <p>Description persistence to the ArgoUML model layer is
 * best-effort; the entity returned at create time carries the
 * value the caller supplied, but a subsequent find/get may yield
 * {@code ""}.</p>
 */
public final class CreateUseCaseHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public CreateUseCaseHandler(UseCaseDiagramService svc) {
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
        String desc = json.get("description") == null
                ? null : json.get("description").toString();
        int x = HandlerJsonHelper.intVal(json.get("x"), 0);
        int y = HandlerJsonHelper.intVal(json.get("y"), 0);
        UsecaseUseCaseEntity v = svc.createUseCase(diagram, name, desc, x, y);
        return ResponseEnvelope.json(201,
                JsonWriter.ok(EntityJson.toMap(v)));
    }
}