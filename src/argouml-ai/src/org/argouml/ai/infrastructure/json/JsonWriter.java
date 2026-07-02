/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serialises the standard success envelope:
 * {@code {"ok":true,"data":<data>}}.
 */
public final class JsonWriter {

    private JsonWriter() { }

    public static String ok(Object data) {
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("ok", Boolean.TRUE);
        env.put("data", data);
        return JsonMini.stringify(env);
    }
}