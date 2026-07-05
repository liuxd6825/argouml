/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.entity;

/**
 * An actor node on a use case diagram.
 *
 * <p>Always carries a uuid (from {@code Model.getFacade().getUUID})
 * and the diagram uuid it was placed on. x/y are read from the
 * actor's {@code Fig} at the time of the service call.</p>
 */
public final class ActorEntity implements ElementEntity {

    private final String uuid;
    private final String name;
    private final String diagramUuid;
    private final int x;
    private final int y;

    public ActorEntity(String uuid, String name, String diagramUuid,
                       int x, int y) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
        this.x = x;
        this.y = y;
    }

    @Override
    public String uuid() { return uuid; }

    @Override
    public String name() { return name; }

    @Override
    public String kind() { return "actor"; }

    @Override
    public String diagramUuid() { return diagramUuid; }

    @Override
    public int x() { return x; }

    @Override
    public int y() { return y; }
}