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

import java.util.List;
import java.util.Map;

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceMessageEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/sequencediagram/messages}.
 *
 * <p>Returns 200 with a JSON array of {@link
 * SequenceMessageEntity} objects (each containing
 * {@code uuid, name, actionSignature, messageType,
 * sequenceNumber, activation, fromUuid, toUuid, diagramUuid,
 * x, y}). Messages are sorted by {@code sequenceNumber}.</p>
 */
public final class ListMessagesHandler
        extends AbstractListHandler<SequenceDiagramService, SequenceMessageEntity> {

    public ListMessagesHandler(SequenceDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<SequenceMessageEntity> doList(String diagram) {
        return service.listMessages(diagram);
    }

    @Override
    protected Map<String, Object> toView(SequenceMessageEntity a) {
        return EntityJson.toMap(a);
    }
}