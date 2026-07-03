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
import org.argouml.model.Facade;
import org.argouml.model.Model;

/**
 * Handler for {@code GET /project/packages/{name}/classes}.
 * Returns the list of classifiers (classes, interfaces, enums,
 * datatypes) recursively under the named package. Empty array
 * if the package has none.
 */
public final class ListPackageClassesHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public ListPackageClassesHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String name = pathParams == null ? null : pathParams.get("name");
        // resolve package by name
        Object pkg = org.argouml.ai.domain.common.PackageOperations
                .findByName(name);
        if (pkg == null) {
            // Return empty list rather than 404; the route matches
            // regardless of whether the package exists. Callers that
            // need a 404 should call GET /project/packages/{name}
            // first to validate.
            return ResponseEnvelope.json(200, JsonWriter.ok(
                    new java.util.ArrayList<Object>()));
        }
        List<Object> cls =
            org.argouml.ai.domain.common.PackageOperations.listClasses(pkg);
        Facade f = Model.getFacade();
        List<Object> out = new ArrayList<Object>();
        for (Object c : cls) {
            Map<String, Object> v = new LinkedHashMap<String, Object>();
            v.put("name", f.getName(c));
            v.put("kind", kindOf(c));
            out.add(v);
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }

    private static String kindOf(Object c) {
        Facade f = Model.getFacade();
        if (f.isAClass(c)) return "class";
        if (f.isAInterface(c)) return "interface";
        if (f.isAEnumeration(c)) return "enumeration";
        if (f.isADataType(c)) return "datatype";
        return "unknown";
    }
}
