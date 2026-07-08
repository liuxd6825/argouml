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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.argouml.model.CoreFactory;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

/**
 * Method-extraction operations against the UML model. Powers the
 * "method extraction" feature: when a {@code syncCall} sequence
 * message carries an {@code actionSignature}, the surrounding service
 * invokes {@link #addMethod(Object, String)} to attach (or re-find) a
 * matching {@code MOperation} on the target classifier.
 *
 * <p><b>Why this class does NOT extend
 * {@link org.argouml.ai.domain.common.AbstractDiagramElementOperations}.</b>
 * The abstract base assumes a {@code build(diagram, name)} factory
 * keyed by diagram (a node on the graph model). Methods are bound to
 * a classifier, not to a sequence diagram; the input is a UML
 * {@code MClassifier}, not a diagram node. Same exception pattern as
 * {@code AttributeOperations} / {@code IncludeOperations} /
 * {@code ExtendOperations} (see AGENTS.md §321).</p>
 *
 * <p><b>Return type.</b> Operations are created with a fixed
 * {@code void} return type. The MDR backend always attaches one
 * return parameter automatically via
 * {@link CoreFactory#buildOperation2(Object, Object, String)}; we use the
 * three-arg overload {@link CoreFactory#buildOperation2(Object, Object, String)}
 * so the operation carries the desired name. Parameters are added
 * with {@link CoreFactory#buildParameter(Object, Object)} (which both
 * creates the {@code MParameter} and attaches it to the operation)
 * — the alternative {@code CoreHelper.addParameter(op, type)} does
 * <em>not</em> work because it requires an existing {@code Parameter}
 * rather than a {@code Classifier} type.</p>
 *
 * <p><b>Caching.</b> {@link #resolveType(String, Object)} caches the
 * created {@code MDataType} per type-name across the JVM so that
 * back-to-back extractions of {@code "Long"} share the same model
 * element. The cache is best-effort: a stale entry (e.g. after the
 * user deletes the model element) is harmless because the next
 * {@code buildDataType} call still creates a fresh one if needed.</p>
 *
 * <p><b>Architectural boundary.</b> Pure functions on the model; no
 * HTTP, no AI, no inbound / outbound adapter, no Swing UI, no
 * diagram-graph-model mutation.</p>
 */
public final class MethodOperations {

    private static final Logger LOG =
            Logger.getLogger(MethodOperations.class.getName());

    /** Cache of resolved {@code MDataType} elements per JVM, keyed by
     *  type-name. Populated lazily by {@link #resolveType}. */
    private static final Map<String, Object> TYPE_CACHE =
            new ConcurrentHashMap<String, Object>();

    /**
     * Clear the per-JVM cache of resolved {@code MDataType}
     * elements. Provided so that test setups (or any long-lived
     * host that creates multiple ArgoUML {@code Project}s in the
     * same JVM) can evict stale entries from a previously-disposed
     * project extent — NetBeans MDR raises an
     * {@code InvalidObjectException} ("Object with MOFID ... no
     * longer exists") when a cached element from a torn-down
     * project is re-used against a new project's extent.
     * Production callers that hold a single project for the
     * lifetime of the JVM rarely need to invoke this.
     */
    public static void clearCache() {
        TYPE_CACHE.clear();
    }

    /**
     * Parse a method signature string of the form {@code "name(p1, p2, ...)"}
     * or, if no parameter list is present, just {@code "name"}. Leading
     * and trailing whitespace are ignored; whitespace around commas
     * inside the parameter list is allowed and stripped.
     *
     * @param signature the signature to parse; null or empty throws.
     * @return a {@link ParsedSignature} carrying the simple method name
     *         and the list of parameter type names in declaration order.
     * @throws IllegalArgumentException if {@code signature} is null,
     *         empty, has no closing parenthesis, or has an empty
     *         method name.
     */
    public ParsedSignature parseSignature(String signature) {
        if (signature == null) {
            throw new IllegalArgumentException("signature is null");
        }
        String trimmed = signature.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("signature is empty");
        }
        int parenStart = trimmed.indexOf('(');
        if (parenStart < 0) {
            return new ParsedSignature(trimmed,
                    new ArrayList<String>());
        }
        if (!trimmed.endsWith(")")) {
            throw new IllegalArgumentException(
                    "signature missing closing paren: " + signature);
        }
        String name = trimmed.substring(0, parenStart).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException(
                    "signature has empty method name: " + signature);
        }
        String paramsPart =
                trimmed.substring(parenStart + 1, trimmed.length() - 1)
                        .trim();
        List<String> paramTypes = new ArrayList<String>();
        if (!paramsPart.isEmpty()) {
            for (String raw : paramsPart.split(",")) {
                String t = raw.trim();
                if (!t.isEmpty()) {
                    paramTypes.add(t);
                }
            }
        }
        return new ParsedSignature(name, paramTypes);
    }

    /**
     * Find or create an {@code MDataType} with the given name in the
     * supplied namespace. Caches the result per type-name across the
     * JVM so back-to-back callers share the same model element. The
     * {@code context} may be either an ArgoUML {@link ArgoDiagram} —
     * in which case its {@code getNamespace()} is unwrapped — or a
     * namespace model element directly.
     *
     * @param typeName simple type name; null or empty returns null.
     * @param context  either a diagram or a namespace owner; null
     *                 causes a fresh {@code MDataType} to be created
     *                 with no owner (the MDR backend accepts this).
     * @return the cached / found / newly-created {@code MDataType}, or
     *         null if {@code typeName} is null / empty or creation
     *         fails (logged at WARNING).
     */
    public Object resolveType(String typeName, Object context) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        Object cached = TYPE_CACHE.get(typeName);
        if (cached != null) {
            return cached;
        }
        Object ns = (context instanceof ArgoDiagram)
                ? ((ArgoDiagram) context).getNamespace()
                : context;
        Object existing = findDataTypeByName(typeName, ns);
        if (existing != null) {
            TYPE_CACHE.put(typeName, existing);
            return existing;
        }
        try {
            Object created = Model.getCoreFactory()
                    .buildDataType(typeName, ns);
            TYPE_CACHE.put(typeName, created);
            return created;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not create MDataType "
                    + typeName + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Search the namespace tree rooted at {@code ns} for an existing
     * {@code MDataType} with the given name. Direct owned elements are
     * scanned first; if no match is found, the search recurses into
     * nested namespaces (packages, collaborations, ...).
     *
     * @param name the simple type name to look up.
     * @param ns   the namespace root to search; null returns null.
     * @return the matching {@code MDataType} element, or null if none
     *         is found or the namespace traversal throws.
     */
    private static Object findDataTypeByName(String name, Object ns) {
        Facade facade = Model.getFacade();
        if (ns == null) {
            return null;
        }
        try {
            Collection owned = facade.getOwnedElements(ns);
            if (owned != null) {
                for (Object e : owned) {
                    if (facade.isADataType(e)
                            && name.equals(facade.getName(e))) {
                        return e;
                    }
                }
            }
            if (facade.isANamespace(ns)) {
                for (Object e : facade.getOwnedElements(ns)) {
                    if (facade.isANamespace(e)) {
                        Object found = findDataTypeByName(name, e);
                        if (found != null) {
                            return found;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // best-effort traversal
        }
        return null;
    }

    /**
     * Idempotently attach a method described by {@code actionSignature}
     * to {@code targetClass}. If an operation with the same name and
     * same parameter type names already exists, it is returned
     * unchanged; otherwise a new {@code MOperation} is built (with a
     * {@code void} return type) and its parameters are added.
     *
     * <p>Malformed signatures (null, empty, missing closing paren,
     * empty method name) are logged at WARNING and cause this method
     * to return null rather than throw — callers (the AI REST handlers)
     * treat this as a soft failure.</p>
     *
     * @param targetClass      the {@code MClassifier} that should own
     *                         the operation; must not be null.
     * @param actionSignature  signature string (e.g.
     *                         {@code "getUser(Long)"}).
     * @return the existing or newly-created {@code MOperation}, or null
     *         on parse / build failure.
     * @throws IllegalArgumentException if {@code targetClass} is null.
     */
    public Object addMethod(Object targetClass, String actionSignature) {
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass must not be null");
        }
        ParsedSignature parsed;
        try {
            parsed = parseSignature(actionSignature);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Bad signature: '" + actionSignature
                    + "' — " + e.getMessage());
            return null;
        }
        Facade facade = Model.getFacade();
        try {
            Collection existing = facade.getOperations(targetClass);
            if (existing != null) {
                for (Object op : existing) {
                    if (parsed.matches(op, facade)) {
                        return op;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // idempotency check is best-effort; fall through to create.
        }

        Object returnType = resolveType("void", targetClass);
        if (returnType == null) {
            LOG.log(Level.WARNING,
                    "Could not resolve 'void' return type for class "
                    + facade.getName(targetClass));
            return null;
        }
        Object operation;
        try {
            operation = Model.getCoreFactory().buildOperation2(
                    targetClass, returnType, parsed.name());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not buildOperation2 "
                    + parsed.name() + " on "
                    + facade.getName(targetClass) + ": " + e.getMessage());
            return null;
        }
        CoreFactory coreFactory = Model.getCoreFactory();
        for (String paramTypeName : parsed.parameterTypeNames()) {
            Object paramType = resolveType(paramTypeName, targetClass);
            if (paramType == null) {
                LOG.log(Level.WARNING, "Could not resolve parameter type "
                        + paramTypeName + " on operation " + parsed.name());
                continue;
            }
            try {
                coreFactory.buildParameter(operation, paramType);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "buildParameter failed on "
                        + parsed.name() + " for " + paramTypeName
                        + ": " + e.getMessage());
            }
        }
        return operation;
    }
}