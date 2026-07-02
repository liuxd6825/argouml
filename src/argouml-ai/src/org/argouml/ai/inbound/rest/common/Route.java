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

import java.util.Map;

/**
 * A single routing entry: HTTP method, URL template, and the handler
 * that should service matching requests. Templates are matched by
 * {@link PathMatcher#match(String, String)}; a route that does not
 * match returns {@code null} and the router moves on.
 */
public final class Route {

    public final Method method;
    public final String template;
    public final IRequestHandler handler;

    public Route(Method m, String t, IRequestHandler h) {
        this.method = m;
        this.template = t;
        this.handler = h;
    }

    /**
     * @param path an incoming request path
     * @return the captured path-parameter map, or {@code null} if the
     *         template does not match the path
     */
    public Map<String, String> match(String path) {
        return PathMatcher.match(template, path);
    }
}