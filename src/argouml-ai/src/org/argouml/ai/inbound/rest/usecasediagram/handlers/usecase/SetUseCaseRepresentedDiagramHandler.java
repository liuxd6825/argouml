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
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for
 * {@code PUT /d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagram}.
 * Body: {@code {"diagramUuid":"..."}} (any ArgoDiagram UUID; empty
 * string clears the link). Returns 200 with the updated
 * {@link UsecaseUseCaseEntity} or 404 USECASE_NOT_FOUND.
 */
public final class SetUseCaseRepresentedDiagramHandler
        implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public SetUseCaseRepresentedDiagramHandler(UseCaseDiagramService svc) {
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
        String name = pathParams == null ? null : pathParams.get("u");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase name required"));
        }
        Map<String, Object> json;
        try {
            json = JsonBodyReader.readMap(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    ex.getMessage()));
        }
        Object diagramUuidObj =
                json == null ? null : json.get("diagramUuid");
        String diagramUuid = diagramUuidObj == null
                ? "" : diagramUuidObj.toString();
        UsecaseUseCaseEntity v =
                svc.setUseCaseRepresentedDiagram(diagram, name, diagramUuid);
        return ResponseEnvelope.json(200, JsonWriter.ok(EntityJson.toMap(v)));
    }
}