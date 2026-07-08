/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.lifeline;

import java.util.Map;

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceLifelineEntity;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/sequencediagram/lifelines/{uuid}}.
 *
 * <p>Looks up a lifeline by its ArgoUML UUID (xmi.id). This is
 * the safe way to address an element when multiple lifelines
 * may share a name in the same namespace. Returns 200 with the
 * full {@link SequenceLifelineEntity}, or 404 LIFELINE_NOT_FOUND.</p>
 *
 * <p>The {@code {uuid}} path segment occupies the bare slot in
 * the lifeline sub-route; name lookups use {@code /by-name/{name}}.
 * A request whose path segment is empty yields 400 INVALID_NAME.</p>
 */
public final class GetLifelineByUuidHandler implements IRequestHandler {

    private final SequenceDiagramService svc;

    public GetLifelineByUuidHandler(SequenceDiagramService svc) {
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
                    "Lifeline uuid required in path"));
        }
        SequenceLifelineEntity v = svc.findLifelineByUuid(diagram, uuid);
        return ResponseEnvelope.json(200, JsonWriter.ok(EntityJson.toMap(v)));
    }
}