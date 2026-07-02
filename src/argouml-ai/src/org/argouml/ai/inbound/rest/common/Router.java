/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Ordered, linear-scan router. Routes are tried in registration
 * order; the first (method, template) match wins. Linear scan is
 * fine for the MVP (under fifty routes) and keeps the
 * implementation tiny and testable.
 *
 * <p>Method names are case-insensitive on input (the wire may use
 * {@code get} or {@code GET} indifferently) but the registered
 * {@link Method} enum constants are the canonical names.</p>
 */
public final class Router {

    private final List<Route> routes = new ArrayList<Route>();

    /**
     * Register a new route. Re-registering the same (method, template)
     * twice yields two entries; first-match-wins decides.
     */
    public void add(Method method, String template, IRequestHandler handler) {
        if (method == null) {
            throw new IllegalArgumentException("method");
        }
        if (template == null) {
            throw new IllegalArgumentException("template");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler");
        }
        routes.add(new Route(method, template, handler));
    }

    /**
     * Look up a (method, path) pair.
     *
     * @param method the wire method string, case-insensitive
     * @param path the request path
     * @return a {@link Resolved} holding the matched handler and its
     *         captured path parameters, or {@code null} if no route
     *         matches
     */
    public Resolved resolve(String method, String path) {
        if (method == null) {
            return null;
        }
        Method m;
        try {
            m = Method.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
        for (Route r : routes) {
            if (r.method != m) {
                continue;
            }
            Map<String, String> params = r.match(path);
            if (params != null) {
                return new Resolved(r.handler, params);
            }
        }
        return null;
    }

    public List<Route> routes() {
        return Collections.unmodifiableList(routes);
    }

    /**
     * Successful resolution: holds the matched handler and the
     * ordered map of path parameters captured by the template.
     */
    public static final class Resolved {

        public final IRequestHandler handler;
        public final Map<String, String> pathParams;

        public Resolved(IRequestHandler h, Map<String, String> p) {
            this.handler = h;
            this.pathParams = p;
        }
    }
}