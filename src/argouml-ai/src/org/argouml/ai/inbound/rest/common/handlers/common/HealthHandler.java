/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.common.handlers.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;

/**
 * Liveness probe. Always returns 200 + an envelope whose {@code data}
 * object contains at least {@code ok:true}, {@code enabled:true} and
 * a {@code project} field (the current project's name, or
 * {@code null} when no project is open). The endpoint intentionally
 * does not depend on any service registry - it must answer even
 * before subsystems are fully wired, which makes it a reliable
 * readiness probe for the smoke-test step in the plan.
 */
public final class HealthHandler implements IRequestHandler {

    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("ok", Boolean.TRUE);
        data.put("enabled", Boolean.TRUE);
        Project p = currentProjectOrNull();
        data.put("project", p == null ? null : p.getName());
        return ResponseEnvelope.json(200, JsonWriter.ok(data));
    }

    private static Project currentProjectOrNull() {
        try {
            Project p = ProjectManager.getManager().getCurrentProject();
            return p;
        } catch (RuntimeException ignored) {
            // ProjectManager is a static singleton; if the kernel
            // isn't initialised yet (e.g. running in isolation), the
            // accessor throws. Surface as "no project" rather than
            // a 500.
            return null;
        }
    }
}