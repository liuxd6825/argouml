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
 * Serialises the standard error envelope:
 * {@code {"ok":false,"error":{"code":"...","message":"..."}}}.
 */
public final class JsonError {

    private JsonError() { }

    public static String of(String code, String message) {
        Map<String, Object> err = new LinkedHashMap<String, Object>();
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> env = new LinkedHashMap<String, Object>();
        env.put("ok", Boolean.FALSE);
        env.put("error", err);
        return JsonMini.stringify(env);
    }
}