/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.ops;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single planned operation on the UML model, emitted by the AI planner
 * and consumed by the executor in {@code org.argouml.ai.ops.OpExecutor}.
 *
 * <p>Each op carries a {@link Type} plus an arbitrary bag of typed
 * fields. Fields are populated via {@link #setString(String, String)} and
 * {@link #setInt(String, int)} and read back via the matching getters.
 * Insertion order is preserved (LinkedHashMap) so log output is stable.
 */
public class PlannedOp {

    /**
     * The kind of operation the executor should perform.
     */
    public enum Type {
        ADD_CLASS,
        ADD_INTERFACE,
        ADD_ATTRIBUTE,
        ADD_OPERATION,
        ADD_ASSOCIATION,
        ADD_GENERALIZATION,
        ADD_DEPENDENCY,
        RENAME_CLASS,
        DELETE_CLASS,
        LIST_CLASSES
    }

    private final Type type;
    private final Map<String, Object> fields;

    /**
     * Create a new op with an empty field bag.
     *
     * @param type the operation kind; must not be {@code null}.
     */
    public PlannedOp(Type type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        this.type = type;
        this.fields = new LinkedHashMap<String, Object>();
    }

    /**
     * @return the operation kind supplied to the constructor.
     */
    public Type getType() {
        return type;
    }

    /**
     * Store a string field.
     */
    public void setString(String key, String value) {
        fields.put(key, value);
    }

    /**
     * Store an integer field.
     */
    public void setInt(String key, int value) {
        fields.put(key, Integer.valueOf(value));
    }

    /**
     * Store a boolean field.
     */
    public void setBoolean(String key, boolean value) {
        fields.put(key, Boolean.valueOf(value));
    }

    /**
     * @return the string previously stored under {@code key}, or
     *         {@code null} if the key is absent.
     */
    public String getString(String key) {
        return (String) fields.get(key);
    }

    /**
     * @return the int previously stored under {@code key}, or 0 if the
     *         key is absent.
     */
    public int getInt(String key) {
        Object v = fields.get(key);
        if (v instanceof Integer) {
            return ((Integer) v).intValue();
        }
        return 0;
    }

    /**
     * @return the boolean previously stored under {@code key}, or
     *         {@code defaultValue} if the key is absent or not a
     *         boolean.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object v = fields.get(key);
        if (v instanceof Boolean) {
            return ((Boolean) v).booleanValue();
        }
        return defaultValue;
    }
}