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
 * A lifeline node on a sequence diagram, paired with a
 * {@link SequenceClassifierRoleEntity}. The classifier-role uuid is
 * returned for cross-referencing.
 */
public final class SequenceLifelineEntity implements ElementEntity {

    private final String uuid;
    private final String name;
    private final String classifierRoleUuid;
    private final boolean active;
    private final String diagramUuid;
    private final int x;
    private final int y;

    public SequenceLifelineEntity(String uuid, String name,
            String classifierRoleUuid, boolean active,
            String diagramUuid, int x, int y) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.classifierRoleUuid = classifierRoleUuid == null ? "" : classifierRoleUuid;
        this.active = active;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
        this.x = x;
        this.y = y;
    }

    @Override public String uuid() { return uuid; }
    @Override public String name() { return name; }
    @Override public String kind() { return "lifeline"; }
    @Override public String diagramUuid() { return diagramUuid; }
    @Override public int x() { return x; }
    @Override public int y() { return y; }
    public String classifierRoleUuid() { return classifierRoleUuid; }
    public boolean active() { return active; }
}