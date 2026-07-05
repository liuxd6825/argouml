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
 * Entity representing an ArgoDiagram (class diagram, use case
 * diagram, sequence diagram, etc.).
 *
 * <p>{@link #namespace()} is the MDR/NetBeans MOF id of the
 * containing model package (typically {@code Model$Impl}).
 * {@link #kind()} returns the diagram-kind discriminator
 * ({@code "usecasediagram"}, {@code "classdiagram"}, ...) so the
 * client can drive downstream behaviour without consulting the
 * full namespace string.</p>
 */
public interface DiagramEntity extends Identified {

    /** ArgoUML namespace (MOF id of the containing model). May be
     *  empty when the model has not yet been persisted. */
    String namespace();
}