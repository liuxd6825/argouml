/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.sequencediagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.argouml.model.Facade;

/**
 * Immutable parsed form of a method signature like
 * {@code "getUser(Long)"} or {@code "setName()"}. Stores the simple
 * method name plus the list of parameter <em>type names</em> in
 * declaration order. Return type is not represented (the only caller,
 * {@link MethodOperations}, supplies a fixed {@code void} return).
 *
 * <p>{@link #matches(Object, Facade)} is the idempotency test used by
 * {@link MethodOperations#addMethod(Object, String)} to detect whether
 * an operation with the same name and same input-parameter type names
 * already exists on the target classifier. The return parameter is
 * ignored because ArgoUML's MDR backend always creates one
 * automatically in {@code buildOperation}.</p>
 *
 * <p><b>Architectural boundary.</b> Value object; no HTTP, no AI, no
 * diagram-mutation, no model-mutation.</p>
 */
public final class ParsedSignature {

    private final String name;
    private final List<String> parameterTypeNames;

    public ParsedSignature(String name, List<String> parameterTypeNames) {
        this.name = name;
        this.parameterTypeNames = Collections.unmodifiableList(
                new ArrayList<String>(parameterTypeNames));
    }

    public String name() {
        return name;
    }

    public List<String> parameterTypeNames() {
        return parameterTypeNames;
    }

    /**
     * Test whether {@code op} is an operation with the same simple
     * name and same input-parameter type names as this signature.
     * The return parameter is filtered out because the ArgoUML MDR
     * backend always attaches one automatically in
     * {@code CoreFactory.buildOperation2}.
     *
     * @param op     candidate operation (e.g. an {@code MOperation});
     *               null returns {@code false}.
     * @param facade ArgoUML model facade used to read names, types,
     *               parameter-kind, etc. Must not be null.
     * @return true iff {@code op} has this signature's name and
     *         exactly this signature's input-parameter type names in
     *         order.
     */
    public boolean matches(Object op, Facade facade) {
        if (op == null) {
            return false;
        }
        if (!name.equals(facade.getName(op))) {
            return false;
        }
        Collection params = facade.getParameters(op);
        if (params == null) {
            return false;
        }
        List<Object> inputParams = new ArrayList<Object>();
        for (Object p : params) {
            if (!facade.isReturn(p)) {
                inputParams.add(p);
            }
        }
        if (inputParams.size() != parameterTypeNames.size()) {
            return false;
        }
        for (int i = 0; i < inputParams.size(); i++) {
            Object type = facade.getType(inputParams.get(i));
            String typeName = (type == null) ? "" : facade.getName(type);
            if (!typeName.equals(parameterTypeNames.get(i))) {
                return false;
            }
        }
        return true;
    }
}