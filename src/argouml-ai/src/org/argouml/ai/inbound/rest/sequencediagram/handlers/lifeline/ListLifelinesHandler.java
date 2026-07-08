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

import java.util.List;
import java.util.Map;

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceLifelineEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/sequencediagram/lifelines}.
 *
 * <p>Returns 200 with a JSON array of {@link
 * SequenceLifelineEntity} objects (each containing
 * {@code uuid, name, classifierRoleUuid, active, diagramUuid,
 * x, y}).</p>
 */
public final class ListLifelinesHandler
        extends AbstractListHandler<SequenceDiagramService, SequenceLifelineEntity> {

    public ListLifelinesHandler(SequenceDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<SequenceLifelineEntity> doList(String diagram) {
        return service.listLifelines(diagram);
    }

    @Override
    protected Map<String, Object> toView(SequenceLifelineEntity a) {
        return EntityJson.toMap(a);
    }
}