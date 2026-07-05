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
 * Base interface for any object exposed by the argouml-ai REST API.
 *
 * <p>Every API-visible entity carries an ArgoUML UUID (xmi.id) and a
 * human-facing {@code name} (which may be duplicated within a
 * namespace). Callers should use {@link #uuid()} as the stable
 * identifier; {@link #name()} is for display only.</p>
 *
 * <p>{@link #kind()} returns a short discriminator string suitable for
 * the wire format ({@code "actor"}, {@code "usecase"},
 * {@code "association"}, {@code "class"}, {@code "usecasediagram"},
 * etc.) so clients can switch on type without sniffing other fields.</p>
 *
 * <p>All implementors must be immutable: the entity is a snapshot
 * of model state at the time of the service call. The model itself
 * continues to mutate; a subsequent {@code findBy*} returns a new
 * entity instance with fresh field values.</p>
 */
public interface Identified {

    /** ArgoUML UUID (xmi.id). May be {@code ""} when the model
     *  backend has not assigned one yet. Never {@code null}. */
    String uuid();

    /** Human-facing display name. May be duplicated within a
     *  namespace. May be {@code null} for relationship entities
     *  that have no natural name. */
    String name();

    /** Short wire-format discriminator (e.g. {@code "actor"},
     *  {@code "usecase"}, {@code "association"},
     *  {@code "usecasediagram"}). Never {@code null}. */
    String kind();
}