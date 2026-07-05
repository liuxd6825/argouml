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
import org.argouml.ai.domain.entity.UseCaseEntity;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/usecasediagram/usecases/{uuid}}.
 *
 * <p>Looks up a use case by ArgoUML UUID. Returns 200 with the
 * full {@link UseCaseEntity}, or 404 USECASE_NOT_FOUND.</p>
 */
public final class GetUseCaseByUuidHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public GetUseCaseByUuidHandler(UseCaseDiagramService svc) {
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
        String uuid = pathParams == null ? null : pathParams.get("uuid");
        if (uuid == null || uuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase uuid required in path"));
        }
        UseCaseEntity v = svc.findUseCaseByUuid(diagram, uuid);
        return ResponseEnvelope.json(200, JsonWriter.ok(EntityJson.toMap(v)));
    }
}