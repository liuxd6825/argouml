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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;

/**
 * Handler for {@code GET /project/packages}. Returns a JSON array
 * of package views, sorted by qualified name.
 */
public final class ListPackagesHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public ListPackagesHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        List<ClassDiagramService.PackageView> pkgs = svc.listPackages();
        List<Object> out = new ArrayList<Object>();
        for (ClassDiagramService.PackageView pv : pkgs) {
            Map<String, Object> v = new LinkedHashMap<String, Object>();
            v.put("name", pv.name);
            v.put("qualifiedName", pv.qualifiedName);
            v.put("parent", pv.parent);
            v.put("classCount", pv.classCount);
            out.add(v);
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}
