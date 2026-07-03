/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.inbound.rest.classdiagram.handlers.operation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.domain.classdiagram.ClassOperations;
import org.argouml.ai.domain.common.DiagramLocator;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonWriter;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Handler for {@code GET /d/{d}/classes/{c}/operations}. Returns one
 * entry per operation on the named class, shaped as
 * {@code {"name":"<simpleName>","returnType":"<typeOrVoid>",
 * "visibility":"public|protected|private|package"}}.
 *
 * <p>The {@code name} and {@code returnType} are taken from the
 * {@link ClassDiagramService.ClassView#operations} strings
 * (encoded as {@code "name(params):returnType"} by
 * {@code ClassDiagramService.formatOperation}). The {@code visibility}
 * is read directly off the model facade because {@code ClassView}
 * does not expose it; the handler walks
 * {@code Facade.getOperations(classElement)} in parallel and pairs
 * each op's name with its visibility kind via the {@code isPublic /
 * isProtected / isPrivate / isPackage} predicates. This duplicates a
 * small amount of model walking that the service could lift into
 * {@code ClassView} later - for the MVP the extra lookup is
 * acceptable since operation counts per class stay small.</p>
 *
 * <p>Read-only; does not open an {@code UndoScope} on the model.</p>
 */
public final class ListOperationsHandler implements IRequestHandler {

    private final ClassDiagramService svc;

    public ListOperationsHandler(ClassDiagramService svc) {
        if (svc == null) {
            throw new IllegalArgumentException("svc");
        }
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagramName = pathParams == null ? null : pathParams.get("d");
        String className = pathParams == null ? null : pathParams.get("c");
        ClassDiagramService.ClassView v =
                svc.getClass(diagramName, className);
        Map<String, String> visByName = visibilitiesByName(
                diagramName, className);
        List<Map<String, Object>> out =
                new ArrayList<Map<String, Object>>(v.operations.size());
        for (String encoded : v.operations) {
            out.add(toView(encoded, visByName));
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }

    private static Map<String, Object> toView(String encoded,
                                               Map<String, String> visByName) {
        int paren = encoded.indexOf('(');
        String name = paren < 0 ? encoded : encoded.substring(0, paren);
        String returnType = extractReturnType(encoded);
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", name);
        m.put("returnType", returnType);
        m.put("visibility", visByName.get(name));
        return m;
    }

    private static String extractReturnType(String encoded) {
        int closeAfterParen = encoded.lastIndexOf("):");
        if (closeAfterParen < 0) {
            return "";
        }
        return encoded.substring(closeAfterParen + 2);
    }

    private static Map<String, String> visibilitiesByName(
            String diagramName, String className) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        ArgoDiagram d = DiagramLocator.byName(diagramName);
        Object cls = new org.argouml.ai.domain.classdiagram.ClassOperations().findByName(d, className);
        if (cls == null) {
            return out;
        }
        Facade facade = Model.getFacade();
        Collection ops = facade.getOperations(cls);
        if (ops == null) {
            return out;
        }
        for (Object op : ops) {
            String n = stringOrNull(facade.getName(op));
            if (n != null) {
                out.put(n, visibilityOf(facade, op));
            }
        }
        return out;
    }

    private static String visibilityOf(Facade facade, Object op) {
        if (facade.isPublic(op)) {
            return "public";
        }
        if (facade.isProtected(op)) {
            return "protected";
        }
        if (facade.isPrivate(op)) {
            return "private";
        }
        if (facade.isPackage(op)) {
            return "package";
        }
        return "public";
    }

    private static String stringOrNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}