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
import org.argouml.ai.inbound.rest.common.HandlerJsonHelper;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /d/{d}/sequencediagram/lifelines}.
 *
 * <p>Body shape:
 * <pre>
 *   { "classifierRoleUuid": "...", "name": "LifelineName" }
 * </pre>
 *
 * <p>{@code classifierRoleUuid} is required and must be the
 * uuid of a {@link SequenceClassifierRoleEntity} already on
 * the diagram. {@code name} is required and must be non-empty.
 * Returns 201 with the new {@link SequenceLifelineEntity}.
 * 400 MISSING_CLASSIFIER_ROLE / INVALID_NAME on bad input,
 * 404 ROLE_NOT_FOUND when the classifier-role uuid does not
 * resolve, 409 DUPLICATE_LIFELINE on name collision.</p>
 */
public final class CreateLifelineHandler implements IRequestHandler {

    private final SequenceDiagramService svc;

    public CreateLifelineHandler(SequenceDiagramService svc) {
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
        String classifierRoleUuid = json.get("classifierRoleUuid") == null
                ? null : json.get("classifierRoleUuid").toString();
        if (classifierRoleUuid == null || classifierRoleUuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of(
                    "MISSING_CLASSIFIER_ROLE",
                    "Field 'classifierRoleUuid' is required"));
        }
        String name = json.get("name") == null ? null : json.get("name").toString();
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Field 'name' is required and must be non-empty"));
        }
        SequenceLifelineEntity v = svc.createLifeline(diagram,
                classifierRoleUuid, name);
        return ResponseEnvelope.json(201,
                JsonWriter.ok(EntityJson.toMap(v)));
    }
}