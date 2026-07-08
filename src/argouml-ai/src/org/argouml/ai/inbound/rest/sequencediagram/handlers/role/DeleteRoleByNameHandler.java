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

import org.argouml.ai.application.sequencediagram.SequenceDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractDeleteHandler;

/**
 * Handler for {@code DELETE /d/{d}/sequencediagram/roles/by-name/{n}}.
 *
 * <p>Path parameter key is {@code "n"} (role name). Returns 204
 * on success, 404 ROLE_NOT_FOUND when the role doesn't exist.</p>
 */
public final class DeleteRoleByNameHandler
        extends AbstractDeleteHandler<SequenceDiagramService> {

    public DeleteRoleByNameHandler(SequenceDiagramService svc) {
        super(svc);
    }

    @Override
    protected String idPathKey() {
        return "n";
    }

    @Override
    protected void doDelete(String diagram, String name) {
        service.deleteRoleByName(diagram, name);
    }
}