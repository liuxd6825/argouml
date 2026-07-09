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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A use case node on a use case diagram.
 *
 * <p>{@link #description()} and {@link #representedDiagramUuids()}
 * are best-effort: the service stores them on the model via
 * ArgoUML's tagged-value mechanism, but the MDR backend in this
 * build does not round-trip values reliably. The create-time
 * value is preserved in the entity returned by the create
 * call, but a subsequent find/get may yield an empty list
 * for {@code representedDiagramUuids()}.</p>
 */
public final class UsecaseUseCaseEntity implements ElementEntity {

    private final String uuid;
    private final String name;
    private final String description;
    private final List<String> representedDiagramUuids;
    private final String diagramUuid;
    private final int x;
    private final int y;

    public UsecaseUseCaseEntity(String uuid, String name, String description,
                         List<String> representedDiagramUuids,
                         String diagramUuid, int x, int y) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.description = description == null ? "" : description;
        this.representedDiagramUuids = representedDiagramUuids == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<String>(representedDiagramUuids));
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

    /** UUIDs of every ArgoDiagram this UseCase is linked to. */
    public List<String> representedDiagramUuids() { return representedDiagramUuids; }

    @Override
    public String diagramUuid() { return diagramUuid; }

    @Override
    public int x() { return x; }

    @Override
    public int y() { return y; }
}
