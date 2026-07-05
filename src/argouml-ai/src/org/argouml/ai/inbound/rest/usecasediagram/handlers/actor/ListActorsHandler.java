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

import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.ActorEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/usecasediagram/actors}.
 *
 * <p>Returns 200 with a JSON array of {@link ActorEntity} objects
 * (each containing {@code uuid, name, kind, diagramUuid, x, y}).</p>
 */
public final class ListActorsHandler
        extends AbstractListHandler<UseCaseDiagramService, ActorEntity> {

    public ListActorsHandler(UseCaseDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<ActorEntity> doList(String diagram) {
        return service.listActors(diagram);
    }

    @Override
    protected Map<String, Object> toView(ActorEntity a) {
        return EntityJson.toMap(a);
    }
}