/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.sequencediagram.handlers.role;

import java.util.List;
import java.util.Map;

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.domain.entity.SequenceClassifierRoleEntity;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /d/{d}/sequencediagram/roles}.
 *
 * <p>Returns 200 with a JSON array of {@link
 * SequenceClassifierRoleEntity} objects (each containing
 * {@code uuid, name, baseUuid, lifelineUuid, diagramUuid, x, y}).</p>
 */
public final class ListRolesHandler
        extends AbstractListHandler<SequenceDiagramService, SequenceClassifierRoleEntity> {

    public ListRolesHandler(SequenceDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<SequenceClassifierRoleEntity> doList(String diagram) {
        return service.listRoles(diagram);
    }

    @Override
    protected Map<String, Object> toView(SequenceClassifierRoleEntity a) {
        return EntityJson.toMap(a);
    }
}