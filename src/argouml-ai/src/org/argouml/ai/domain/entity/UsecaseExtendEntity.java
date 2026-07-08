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
 * A use-case extend edge ({@code base «extend» extension}).
 *
 * <p>{@link #name()} returns {@code null}; clients use
 * {@link #id()} ({@code "base|extension"}) or {@link #uuid()}.</p>
 */
public final class UsecaseExtendEntity implements Identified {

    private final String uuid;
    private final String id;
    private final String baseUuid;
    private final String baseName;
    private final String extensionUuid;
    private final String extensionName;
    private final String extensionPoint;
    private final String diagramUuid;

    public UsecaseExtendEntity(String uuid, String id,
                        String baseUuid, String baseName,
                        String extensionUuid, String extensionName,
                        String extensionPoint, String diagramUuid) {
        this.uuid = uuid == null ? "" : uuid;
        this.id = id;
        this.baseUuid = baseUuid == null ? "" : baseUuid;
        this.baseName = baseName;
        this.extensionUuid = extensionUuid == null ? "" : extensionUuid;
        this.extensionName = extensionName;
        this.extensionPoint = extensionPoint == null ? "" : extensionPoint;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
    }

    @Override
    public String uuid() { return uuid; }

    @Override
    public String name() { return null; }

    @Override
    public String kind() { return "extend"; }

    public String id() { return id; }

    public String baseUuid() { return baseUuid; }

    public String baseName() { return baseName; }

    public String extensionUuid() { return extensionUuid; }

    public String extensionName() { return extensionName; }

    public String extensionPoint() { return extensionPoint; }

    public String diagramUuid() { return diagramUuid; }
}