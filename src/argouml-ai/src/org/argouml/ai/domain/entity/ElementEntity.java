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
 * Entity representing a node placed on a diagram: Actor, UseCase,
 * Class, Interface, Attribute, Operation, etc.
 *
 * <p>Adds geometry ({@link #x()}, {@link #y()}) and a back-reference
 * ({@link #diagramUuid()}) to the parent {@link DiagramEntity}.</p>
 *
 * <p>Note: x/y are in ArgoUML diagram coordinates, NOT screen
 * pixels. They reflect the position of the node's {@code Fig} on
 * the canvas.</p>
 */
public interface ElementEntity extends Identified {

    /** UUID of the {@link DiagramEntity} that owns this node. */
    String diagramUuid();

    /** Canvas x coordinate. May be 0 for lazily-realised figs. */
    int x();

    /** Canvas y coordinate. May be 0 for lazily-realised figs. */
    int y();
}