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
 * A classifier-role node on a sequence diagram. Server auto-creates
 * a paired {@link SequenceLifelineEntity} when this role is created;
 * its uuid is returned in the entity.
 */
public final class SequenceClassifierRoleEntity implements ElementEntity {

    private final String uuid;
    private final String name;
    private final String baseUuid;
    private final String lifelineUuid;
    private final String diagramUuid;
    private final int x;
    private final int y;

    public SequenceClassifierRoleEntity(String uuid, String name,
            String baseUuid, String lifelineUuid, String diagramUuid,
            int x, int y) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.baseUuid = baseUuid == null ? "" : baseUuid;
        this.lifelineUuid = lifelineUuid == null ? "" : lifelineUuid;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
        this.x = x;
        this.y = y;
    }

    @Override public String uuid() { return uuid; }
    @Override public String name() { return name; }
    @Override public String kind() { return "classifierRole"; }
    @Override public String diagramUuid() { return diagramUuid; }
    @Override public int x() { return x; }
    @Override public int y() { return y; }
    public String baseUuid() { return baseUuid; }
    public String lifelineUuid() { return lifelineUuid; }
}