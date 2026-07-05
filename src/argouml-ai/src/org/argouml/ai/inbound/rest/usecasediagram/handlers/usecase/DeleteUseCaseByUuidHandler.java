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
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;

/**
 * Handler for {@code DELETE /d/{d}/usecasediagram/usecases/{uuid}}.
 *
 * <p>Deletes a use case by its ArgoUML UUID. Returns 204 on
 * success, 404 USECASE_NOT_FOUND when the uuid doesn't match any
 * use case on the named diagram.</p>
 */
public final class DeleteUseCaseByUuidHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public DeleteUseCaseByUuidHandler(UseCaseDiagramService svc) {
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
        svc.deleteUseCaseByUuid(diagram, uuid);
        return ResponseEnvelope.json(204, "");
    }
}