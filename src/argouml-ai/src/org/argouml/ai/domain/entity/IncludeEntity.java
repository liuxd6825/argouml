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
 * A use-case include edge ({@code base «include» inclusion}).
 *
 * <p>{@link #name()} returns {@code null}; clients use
 * {@link #id()} ({@code "base|inclusion"}) or {@link #uuid()}.</p>
 */
public final class IncludeEntity implements Identified {

    private final String uuid;
    private final String id;
    private final String baseUuid;
    private final String baseName;
    private final String inclusionUuid;
    private final String inclusionName;
    private final String diagramUuid;

    public IncludeEntity(String uuid, String id,
                         String baseUuid, String baseName,
                         String inclusionUuid, String inclusionName,
                         String diagramUuid) {
        this.uuid = uuid == null ? "" : uuid;
        this.id = id;
        this.baseUuid = baseUuid == null ? "" : baseUuid;
        this.baseName = baseName;
        this.inclusionUuid = inclusionUuid == null ? "" : inclusionUuid;
        this.inclusionName = inclusionName;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
    }

    @Override
    public String uuid() { return uuid; }

    @Override
    public String name() { return null; }

    @Override
    public String kind() { return "include"; }

    public String id() { return id; }

    public String baseUuid() { return baseUuid; }

    public String baseName() { return baseName; }

    public String inclusionUuid() { return inclusionUuid; }

    public String inclusionName() { return inclusionName; }

    public String diagramUuid() { return diagramUuid; }
}