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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;

/**
 * Handler for {@code GET /d/{d}/usecasediagram/actors}.
 */
public final class ListActorsHandler
        extends AbstractListHandler<UseCaseDiagramService,
                                   UseCaseDiagramService.ActorView> {

    public ListActorsHandler(UseCaseDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<UseCaseDiagramService.ActorView> doList(String diagram) {
        return service.listActors(diagram);
    }

    @Override
    protected Map<String, Object> toView(
            UseCaseDiagramService.ActorView a) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", a.name);
        m.put("x", a.x);
        m.put("y", a.y);
        return m;
    }
}
