/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.inbound.rest.common.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Shared plumbing for {@code GET /d/{d}/<kind>s} list endpoints.
 *
 * <p>Subclasses supply:
 * <ol>
 *   <li>{@link #doList(String)} — call the service to fetch the
 *       raw list of view DTOs (already flat-typed by the
 *       service);</li>
 *   <li>{@link #toView(Object)} — convert one DTO into a
 *       wire-format map (per-kind fields like {@code "x"},
 *       {@code "description"} differ across kinds).</li>
 * </ol>
 *
 * <p>The base class extracts the diagram name from the path,
 * maps the list to wire format, wraps in the standard success
 * envelope, and emits 200. Replaces ~15-20 lines of boilerplate
 * per list handler with a 4-line subclass.</p>
 *
 * @param <S> service type (e.g. {@code ClassDiagramService} or
 *           {@code UseCaseDiagramService})
 * @param <V> view DTO type returned by the service
 */
public abstract class AbstractListHandler<S, V> implements IRequestHandler {

    protected final S service;

    protected AbstractListHandler(S service) {
        if (service == null) {
            throw new IllegalArgumentException("service");
        }
        this.service = service;
    }

    /** Subclass-supplied: list every entity of this kind on
     *  the named diagram. */
    protected abstract java.util.List<V> doList(String diagram);

    /** Subclass-supplied: convert one view DTO into a wire map.
     *  Use a {@link java.util.LinkedHashMap} for stable field
     *  ordering. */
    protected abstract Map<String, Object> toView(V view);

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        List<V> raw = doList(diagram);
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(
                raw == null ? 0 : raw.size());
        if (raw != null) {
            for (V v : raw) {
                out.add(toView(v));
            }
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}
