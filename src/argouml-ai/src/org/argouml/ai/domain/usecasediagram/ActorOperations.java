/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.usecasediagram;

import org.argouml.ai.domain.common.AbstractDiagramElementOperations;
import org.argouml.model.CoreHelper;
import org.argouml.model.Model;
import org.argouml.model.UseCasesFactory;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * UML Actor creation / mutation against the model layer and the
 * use-case diagram's graph model. Pure functions; same architectural
 * boundary as {@code ClassOperations} (no HTTP, no AI, no UI).
 *
 * <p>Inherits build / findByName / delete / setPosition from
 * {@link AbstractDiagramElementOperations}; supplies only the
 * use-case-specific factory and type discriminator. The static
 * {@code findByName} / {@code setPosition} / {@code delete} /
 * {@code build} wrappers preserve the pre-refactor call sites in
 * tests and the service layer.</p>
 */
public final class ActorOperations
        extends AbstractDiagramElementOperations<Object> {

    @Override
    protected Object buildImpl(ArgoDiagram diagram, String name) {
        UseCasesFactory uf = Model.getUseCasesFactory();
        CoreHelper ch = Model.getCoreHelper();
        Object actor = uf.createActor();
        ch.setName(actor, name);
        ch.addOwnedElement(diagram.getNamespace(), actor);
        return actor;
    }

    @Override
    protected boolean isTargetType(Object node) {
        return Model.getFacade().isAActor(node);
    }
}

