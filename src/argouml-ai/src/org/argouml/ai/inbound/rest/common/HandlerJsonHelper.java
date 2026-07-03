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

import org.argouml.ai.infrastructure.json.JsonBodyReader;

/**
 * Small helpers for reading typed values out of a JSON body map
 * produced by {@link JsonBodyReader}. Promoted from
 * {@code classdiagram.handlers.JsonFields} so all REST handlers
 * (class, use case, future kinds) can share a single source of
 * truth for JSON coercion.
 *
 * <p>Helpers follow a "default on miss" policy so that callers can
 * write the natural-looking code:</p>
 * <pre>
 *   int x = HandlerJsonHelper.intVal(json.get("x"), 100);
 * </pre>
 * <p>and never have to special-case missing keys.</p>
 */
public final class HandlerJsonHelper {

    private HandlerJsonHelper() {
    }

    /** @return null when {@code o} is null, otherwise
     *  {@code o.toString()}. */
    public static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /** @return null when the field is null, missing, or an empty
     *  string; otherwise the string value. Used for fields where
     *  "present and empty" should be treated the same as "absent"
     *  (e.g. optional stereotype). */
    public static String strEmpty(Object o) {
        String s = str(o);
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** @return the int value, or {@code dflt} when the field is
     *  absent or malformed. */
    public static int intVal(Object o, int dflt) {
        if (o == null) {
            return dflt;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    /** @return null when the field is absent; otherwise the int
     *  value (or {@code dflt} when present but malformed). Used by
     *  PUT handlers so "not present" can be distinguished from
     *  "explicitly zero". */
    public static Integer intOpt(Object o, int dflt) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return Integer.valueOf(((Number) o).intValue());
        }
        try {
            return Integer.valueOf(Integer.parseInt(o.toString()));
        } catch (NumberFormatException e) {
            return Integer.valueOf(dflt);
        }
    }

    public static boolean boolVal(Object o, boolean dflt) {
        if (o == null) {
            return dflt;
        }
        if (o instanceof Boolean) {
            return ((Boolean) o).booleanValue();
        }
        return Boolean.parseBoolean(o.toString());
    }

    /** @return null when the field is absent; otherwise the boolean
     *  value (or {@code dflt} when present but malformed). */
    public static Boolean boolOpt(Object o, boolean dflt) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return Boolean.valueOf(Boolean.parseBoolean(o.toString()));
    }
}
