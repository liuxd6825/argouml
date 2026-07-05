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

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractDeleteHandler;

/**
 * Handler for {@code DELETE /d/{d}/usecasediagram/usecases/by-name/{u}}.
 *
 * <p>Path parameter key is {@code "u"} (use case name). Returns
 * 204 on success, 404 USECASE_NOT_FOUND when the use case doesn't
 * exist.</p>
 */
public final class DeleteUseCaseByNameHandler
        extends AbstractDeleteHandler<UseCaseDiagramService> {

    public DeleteUseCaseByNameHandler(UseCaseDiagramService svc) {
        super(svc);
    }

    @Override
    protected String idPathKey() {
        return "u";
    }

    @Override
    protected void doDelete(String diagram, String name) {
        service.deleteUseCaseByName(diagram, name);
    }
}