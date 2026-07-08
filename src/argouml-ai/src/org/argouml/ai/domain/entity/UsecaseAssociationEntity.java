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
 * An actor-to-use-case association edge on a use case diagram.
 *
 * <p>{@link #name()} returns {@code null} — relationships do not
 * have a natural human-facing name; clients identify them via the
 * composite {@link #id()} ({@code "actor|usecase"}) or the
 * ArgoUML {@link #uuid()}.</p>
 */
public final class UsecaseAssociationEntity implements Identified {

    private final String uuid;
    private final String id;
    private final String actorUuid;
    private final String actorName;
    private final String usecaseUuid;
    private final String usecaseName;
    private final String diagramUuid;

    public UsecaseAssociationEntity(String uuid, String id,
                             String actorUuid, String actorName,
                             String usecaseUuid, String usecaseName,
                             String diagramUuid) {
        this.uuid = uuid == null ? "" : uuid;
        this.id = id;
        this.actorUuid = actorUuid == null ? "" : actorUuid;
        this.actorName = actorName;
        this.usecaseUuid = usecaseUuid == null ? "" : usecaseUuid;
        this.usecaseName = usecaseName;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
    }

    @Override
    public String uuid() { return uuid; }

    @Override
    public String name() { return null; }

    @Override
    public String kind() { return "association"; }

    public String id() { return id; }

    public String actorUuid() { return actorUuid; }

    public String actorName() { return actorName; }

    public String usecaseUuid() { return usecaseUuid; }

    public String usecaseName() { return usecaseName; }

    public String diagramUuid() { return diagramUuid; }
}