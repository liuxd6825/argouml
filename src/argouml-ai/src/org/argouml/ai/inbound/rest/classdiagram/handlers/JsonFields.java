/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers;

/**
 * Small package-private helpers for reading typed values out of the
 * JSON body map produced by {@code JsonBodyReader}. The dispatcher
 * already rejects empty / malformed bodies, but every handler in this
 * directory still needs to coerce {@code Object} values into typed
 * primitives - {@code int}, {@code boolean}, and {@code String} -
 * without throwing on a missing or wrong-typed field.
 *
 * <p>Helpers follow a "default on miss" policy so that callers can
 * write the natural-looking code:</p>
 * <pre>
 *   int x = JsonFields.intVal(json.get("x"), 100);
 * </pre>
 * <p>and never have to special-case missing keys.</p>
 */
public final class JsonFields {

    private JsonFields() {
    }

    static String str(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * @return null when the field is null, missing, or an empty
     *         string; otherwise the string value. Used for fields
     *         where "present and empty" should be treated the same
     *         as "absent" (e.g. optional stereotype).
     */
    static String strEmpty(Object o) {
        String s = str(o);
        return (s == null || s.isEmpty()) ? null : s;
    }

    static int intVal(Object o, int dflt) {
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

    /**
     * @return null when the field is absent; otherwise the int value
     *         (or {@code dflt} when present but malformed). Used by
     *         {@code UpdateClassHandler} so "not present" can be
     *         distinguished from "explicitly zero".
     */
    static Integer intOpt(Object o, int dflt) {
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

    static boolean boolVal(Object o, boolean dflt) {
        if (o == null) {
            return dflt;
        }
        if (o instanceof Boolean) {
            return ((Boolean) o).booleanValue();
        }
        return Boolean.parseBoolean(o.toString());
    }

    /**
     * @return null when the field is absent; otherwise the boolean
     *         value (or {@code dflt} when present but malformed).
     *         Used by {@code UpdateClassHandler}.
     */
    static Boolean boolOpt(Object o, boolean dflt) {
        if (o == null) {
            return null;
        }
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        return Boolean.valueOf(Boolean.parseBoolean(o.toString()));
    }
}