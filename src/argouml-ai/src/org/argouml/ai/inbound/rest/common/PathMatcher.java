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

import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny URL-template matcher. Accepts templates of the shape
 * {@code /d/{d}/classes/{c}} and a concrete path; returns either
 * {@code null} on no-match or a {@link LinkedHashMap} (insertion
 * ordered) of captured segment values keyed by template variable
 * name. The matching rules are deliberately strict:
 *
 * <ul>
 *   <li>leading slashes are tolerated on both sides (stripped);</li>
 *   <li>segment counts must match exactly - no trailing-slash
 *       forgiveness;</li>
 *   <li>variables are {@code "{name}"} single segments, not
 *       wildcards or globs;</li>
 *   <li>empty templates or paths, and {@code null} arguments, all
 *       produce {@code null} (no match);</li>
 *   <li>captured values are URL-decoded with UTF-8 so callers do not
 *       have to think about percent-encoded segments.</li>
 * </ul>
 *
 * <p>The class is stateless. All methods are static and the single
 * public entry point is {@link #match(String, String)}.</p>
 */
public final class PathMatcher {

    private PathMatcher() {
    }

    /**
     * @param template the route template, e.g. {@code "/d/{d}/classes"}.
     *                 May or may not start with {@code '/'}.
     * @param path the incoming request path, e.g. {@code "/d/D1/classes"}.
     *             May or may not start with {@code '/'}.
     * @return a (possibly empty) ordered map of variable bindings, or
     *         {@code null} if {@code template} and {@code path} do not
     *         match segment-for-segment.
     */
    public static Map<String, String> match(String template, String path) {
        if (template == null || path == null) {
            return null;
        }
        if (template.isEmpty() || path.isEmpty()) {
            return null;
        }
        String[] tParts = split(template);
        String[] pParts = split(path);
        if (tParts.length != pParts.length) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<String, String>();
        for (int i = 0; i < tParts.length; i++) {
            String t = tParts[i];
            String p = pParts[i];
            if (isVariable(t)) {
                params.put(stripBraces(t), decode(p));
            } else if (!t.equals(p)) {
                return null;
            }
        }
        return params;
    }

    private static boolean isVariable(String s) {
        return s.length() >= 2 && s.startsWith("{") && s.endsWith("}");
    }

    private static String stripBraces(String s) {
        return s.substring(1, s.length() - 1);
    }

    private static String[] split(String s) {
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return new String[0];
        }
        // split("/") with limit = -1 keeps trailing empty strings,
        // which lets us treat "/d/D1/" as having an extra trailing
        // segment and refuse the match.
        return s.split("/", -1);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception ex) {
            return s;
        }
    }
}