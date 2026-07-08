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
 * A use case diagram entity.
 *
 * <p>{@link #kind()} returns {@code "usecasediagram"}; the
 * shorter {@code "usecase"} form is used by the
 * {@code /project/diagrams} endpoint's per-entry
 * {@code kind} field for client-side discrimination. The two
 * are not equivalent: this entity is returned by entity-shaped
 * endpoints, the latter is returned by the project-listing
 * endpoint.</p>
 */
public final class UseCaseDiagramEntity implements DiagramEntity {

    private final String uuid;
    private final String name;
    private final String namespace;

    public UseCaseDiagramEntity(String uuid, String name, String namespace) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.namespace = namespace == null ? "" : namespace;
    }

    @Override
    public String uuid() { return uuid; }

    @Override
    public String name() { return name; }

    @Override
    public String kind() { return "usecasediagram"; }

    @Override
    public String namespace() { return namespace; }
}