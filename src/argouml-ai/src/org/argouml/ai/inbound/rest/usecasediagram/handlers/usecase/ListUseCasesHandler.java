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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;

/**
 * Handler for {@code GET /d/{d}/usecasediagram/usecases}.
 */
public final class ListUseCasesHandler
        extends AbstractListHandler<UseCaseDiagramService,
                                   UseCaseDiagramService.UseCaseView> {

    public ListUseCasesHandler(UseCaseDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<UseCaseDiagramService.UseCaseView> doList(String diagram) {
        return service.listUseCases(diagram);
    }

    @Override
    protected Map<String, Object> toView(
            UseCaseDiagramService.UseCaseView u) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", u.name);
        m.put("description", u.description);
        m.put("x", u.x);
        m.put("y", u.y);
        return m;
    }
}
