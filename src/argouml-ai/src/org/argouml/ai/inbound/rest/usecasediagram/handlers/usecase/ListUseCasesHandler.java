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

import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UseCaseEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/usecasediagram/usecases}.
 *
 * <p>Returns 200 with a JSON array of {@link UseCaseEntity}
 * objects (each containing {@code uuid, name, kind, description,
 * diagramUuid, x, y}).</p>
 */
public final class ListUseCasesHandler
        extends AbstractListHandler<UseCaseDiagramService, UseCaseEntity> {

    public ListUseCasesHandler(UseCaseDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<UseCaseEntity> doList(String diagram) {
        return service.listUseCases(diagram);
    }

    @Override
    protected Map<String, Object> toView(UseCaseEntity u) {
        return EntityJson.toMap(u);
    }
}