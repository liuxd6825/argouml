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
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;

/**
 * Handler for {@code DELETE /d/{d}/usecasediagram/actors/{uuid}}.
 *
 * <p>Deletes an actor by its ArgoUML UUID (xmi.id). Returns 204
 * on success, 404 ACTOR_NOT_FOUND when the uuid doesn't match
 * any actor on the named diagram.</p>
 *
 * <p>This route is the bare-slot counterpart to
 * {@code /by-name/{a}}. The path-parameter key is {@code "uuid"}.</p>
 */
public final class DeleteActorByUuidHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public DeleteActorByUuidHandler(UseCaseDiagramService svc) {
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
                    "Actor uuid required in path"));
        }
        svc.deleteActorByUuid(diagram, uuid);
        return ResponseEnvelope.json(204, "");
    }

    /** Identifier for tests; not part of the wire contract. */
    public static String pathParamKey() {
        return "uuid";
    }
}