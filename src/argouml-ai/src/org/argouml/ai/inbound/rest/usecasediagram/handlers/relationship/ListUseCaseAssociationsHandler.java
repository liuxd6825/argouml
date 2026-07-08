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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UsecaseAssociationEntity;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/usecasediagram/associations}.
 *
 * <p>Returns 200 with a JSON array of {@link UsecaseAssociationEntity}
 * entries. Each entry carries {@code uuid, name (null),
 * kind="association", id, actorUuid, actorName, usecaseUuid,
 * usecaseName, diagramUuid}.</p>
 */
public final class ListUseCaseAssociationsHandler implements IRequestHandler {

    private final UseCaseDiagramService svc;

    public ListUseCaseAssociationsHandler(UseCaseDiagramService svc) {
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
        List<UsecaseAssociationEntity> rows = svc.listAssociations(diagram);
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(
                rows.size());
        for (UsecaseAssociationEntity r : rows) {
            out.add(EntityJson.toMap(r));
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}