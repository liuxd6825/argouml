/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.message;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceMessageEntity;
import org.argouml.ai.inbound.rest.common.HandlerJsonHelper;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code POST /d/{d}/sequencediagram/messages}.
 *
 * <p>Body shape:
 * <pre>
 *   {
 *     "name": "doIt",
 *     "messageType": "syncCall",
 *     "fromUuid": "...",
 *     "toUuid": "...",
 *     "actionSignature": "() : void",
 *     "activation": false
 *   }
 * </pre>
 *
 * <p>{@code name}, {@code messageType}, {@code fromUuid} and
 * {@code toUuid} are required. {@code messageType} must be one
 * of {@code syncCall}, {@code asyncCall}, {@code asyncSignal},
 * {@code reply}, {@code create}, {@code delete} (the values that
 * {@link org.argouml.ai.domain.sequencediagram.MessageOperations}
 * knows how to materialise).
 * {@code activation} defaults to {@code false}; {@code actionSignature}
 * defaults to {@code ""}. Returns 201 with the new
 * {@link SequenceMessageEntity}, or 400 / 404 errors as
 * appropriate.</p>
 */
public final class CreateMessageHandler implements IRequestHandler {

    private static final Set<String> VALID_MESSAGE_TYPES =
            new HashSet<String>(Arrays.asList(
                    "syncCall",
                    "asyncCall",
                    "asyncSignal",
                    "reply",
                    "create",
                    "delete"));

    private final SequenceDiagramService svc;

    public CreateMessageHandler(SequenceDiagramService svc) {
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
        String messageType = json.get("messageType") == null
                ? null : json.get("messageType").toString();
        if (messageType == null || messageType.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Field 'messageType' is required and must be non-empty"));
        }
        if (!VALID_MESSAGE_TYPES.contains(messageType)) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_MESSAGE_TYPE",
                    "messageType must be one of " + VALID_MESSAGE_TYPES));
        }
        String fromUuid = json.get("fromUuid") == null
                ? null : json.get("fromUuid").toString();
        if (fromUuid == null || fromUuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Field 'fromUuid' is required and must be non-empty"));
        }
        String toUuid = json.get("toUuid") == null
                ? null : json.get("toUuid").toString();
        if (toUuid == null || toUuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "Field 'toUuid' is required and must be non-empty"));
        }
        String actionSignature = json.get("actionSignature") == null
                ? "" : json.get("actionSignature").toString();
        boolean activation = HandlerJsonHelper.boolVal(
                json.get("activation"), false);
        SequenceMessageEntity v = svc.createMessage(diagram, name,
                actionSignature, messageType, activation, fromUuid, toUuid);
        return ResponseEnvelope.json(201,
                JsonWriter.ok(EntityJson.toMap(v)));
    }
}