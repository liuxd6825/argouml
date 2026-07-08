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
 * A use case node on a use case diagram.
 *
 * <p>{@link #description()} is best-effort: the service stores it
 * on the model via ArgoUML's tagged-value mechanism, but the MDR
 * backend in this build does not round-trip the value on every
 * read. The create-time value is preserved in the entity returned
 * by the create call, but a subsequent find/get may yield {@code ""}.</p>
 */
public final class UsecaseUseCaseEntity implements ElementEntity {

    private final String uuid;
    private final String name;
    private final String description;
    private final String diagramUuid;
    private final int x;
    private final int y;

    public UsecaseUseCaseEntity(String uuid, String name, String description,
                         String diagramUuid, int x, int y) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.description = description == null ? "" : description;
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
        this.x = x;
        this.y = y;
    }

    @Override
    public String uuid() { return uuid; }

    @Override
    public String name() { return name; }

    @Override
    public String kind() { return "usecase"; }

    public String description() { return description; }

    @Override
    public String diagramUuid() { return diagramUuid; }

    @Override
    public int x() { return x; }

    @Override
    public int y() { return y; }
}