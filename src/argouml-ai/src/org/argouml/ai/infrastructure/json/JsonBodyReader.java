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
 * Parses a request body string into a {@code Map<String,Object>}.
 * Empty/null bodies yield an empty map; malformed input throws
 * {@link IllegalArgumentException}; non-object input (e.g. a JSON
 * array at the top level) also throws.
 */
public final class JsonBodyReader {

    private JsonBodyReader() { }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String body) {
        if (body == null || body.isEmpty()) {
            return new LinkedHashMap<String, Object>();
        }
        Object o;
        try {
            o = JsonMini.parse(body);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("malformed JSON body", ex);
        }
        if (o == null) {
            return new LinkedHashMap<String, Object>();
        }
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("body is not a JSON object");
        }
        return (Map<String, Object>) o;
    }
}