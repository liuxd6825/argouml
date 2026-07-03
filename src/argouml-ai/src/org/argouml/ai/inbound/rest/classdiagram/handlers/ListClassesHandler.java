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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.inbound.rest.common.handlers.AbstractListHandler;

/**
 * Handler for {@code GET /d/{d}/classes}. Inherits the diagram
 * resolution + JSON envelope machinery from
 * {@link AbstractListHandler}; supplies the per-kind service
 * call and the {@code ClassView} → wire-map translation.
 */
public final class ListClassesHandler
        extends AbstractListHandler<ClassDiagramService,
                                   ClassDiagramService.ClassView> {

    public ListClassesHandler(ClassDiagramService svc) {
        super(svc);
    }

    @Override
    protected List<ClassDiagramService.ClassView> doList(String diagram) {
        return service.listClasses(diagram);
    }

    @Override
    protected Map<String, Object> toView(
            ClassDiagramService.ClassView v) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", v.name);
        m.put("isAbstract", Boolean.valueOf(v.isAbstract));
        m.put("stereotypeNames",
                new java.util.ArrayList<String>(v.stereotypeNames));
        m.put("attributes", new java.util.ArrayList<String>(v.attributes));
        m.put("operations", new java.util.ArrayList<String>(v.operations));
        m.put("x", Integer.valueOf(v.x));
        m.put("y", Integer.valueOf(v.y));
        return m;
    }
}
