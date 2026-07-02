/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.domain.classdiagram;

import java.util.List;

import org.argouml.model.CoreFactory;
import org.argouml.model.CoreHelper;
import org.argouml.model.Facade;
import org.argouml.model.Model;

/**
 * UML Operation (callable feature of a Classifier) creation /
 * mutation against the model layer. Mirrors
 * {@link AttributeOperations} but uses
 * {@link CoreFactory#buildOperation2(Object, Object, String)}.
 *
 * <p><b>Architectural boundary.</b> Same as
 * {@link AttributeOperations}: no HTTP, no AI, no inbound / outbound
 * adapter layer, no Swing UI, no diagram-graph-model mutation, no
 * {@code Fig} placement.
 */
public final class OperationOperations {

    private OperationOperations() {
    }

    /**
     * Build a UML Operation on {@code ownerClass} with the given
     * simple name, optional return type, and optional visibility.
     * If {@code returnTypeName} is non-null and non-empty a
     * {@code DataType} of that name is created in the class's
     * namespace and attached as the operation's return type; if it
     * is null or empty the operation is left with no explicit return
     * type (which the renderer displays as {@code void-ish}). The
     * optional {@code visibility} is one of {@code "public"},
     * {@code "protected"}, {@code "private"}, or {@code "package"};
     * a null value leaves visibility at the UML default (public).
     *
     * @param ownerClass     the Class that will own the new
     *                       operation; must not be null.
     * @param name           the simple (unqualified) operation name;
     *                       must be non-null and non-empty.
     * @param returnTypeName the simple return type name, or
     *                       null/empty for no explicit return type.
     * @param visibility    the visibility kind name, or null/empty
     *                       for default.
     * @return the newly created model element.
     * @throws IllegalArgumentException if {@code ownerClass} is null,
     *                                  or {@code name} is null or
     *                                  empty, or {@code visibility}
     *                                  is not null/empty and is not
     *                                  one of the four legal values.
     */
    public static Object build(Object ownerClass, String name,
                               String returnTypeName, String visibility) {
        if (ownerClass == null) {
            throw new IllegalArgumentException(
                    "ownerClass must not be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Operation name must not be empty");
        }
        Object returnType = resolveType(ownerClass, returnTypeName);
        Object op = Model.getCoreFactory().buildOperation2(
                ownerClass, returnType, name);
        applyVisibility(op, visibility);
        return op;
    }

    /**
     * Find the operation on {@code ownerClass} whose simple name
     * equals {@code name}.
     *
     * @param ownerClass the class whose operations are searched; a
     *                   {@code null} value yields a {@code null}
     *                   result without throwing.
     * @param name       the simple name to search for; a null or
     *                   empty value yields a {@code null} result
     *                   without throwing.
     * @return the matching operation model element, or {@code null}
     *         if no operation of that name exists on the class.
     */
    public static Object findByName(Object ownerClass, String name) {
        if (ownerClass == null || name == null || name.isEmpty()) {
            return null;
        }
        Facade facade = Model.getFacade();
        List ops = facade.getOperations(ownerClass);
        if (ops == null) {
            return null;
        }
        for (Object o : ops) {
            if (name.equals(facade.getName(o))) {
                return o;
            }
        }
        return null;
    }

    /**
     * Delete an operation from its owning class. Mirrors the
     * diagram delete pattern in {@link ClassOperations#delete}:
     * errors during the model delete are swallowed.
     *
     * @param ownerClass the owning class (currently unused; reserved
     *                   for future use when the MDR backend requires
     *                   the parent for ownership).
     * @param op         the operation model element; a {@code null}
     *                   value is a no-op.
     */
    public static void delete(Object ownerClass, Object op) {
        if (op == null) {
            return;
        }
        try {
            Model.getUmlFactory().delete(op);
        } catch (RuntimeException ignored) {
            // model may refuse; treat as best-effort
        }
    }

    /**
     * Resolve a return-type name to a {@code DataType} model element
     * in the owning class's namespace. Returns {@code null} if
     * {@code returnTypeName} is null or empty.
     */
    private static Object resolveType(Object ownerClass, String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        Object ns = Model.getFacade().getNamespace(ownerClass);
        if (ns == null) {
            return null;
        }
        return Model.getCoreFactory().buildDataType(typeName, ns);
    }

    /**
     * Apply a visibility kind, if one was supplied. Recognises the
     * four UML-defined values: {@code "public"}, {@code "protected"},
     * {@code "private"}, {@code "package"}. Unknown values throw
     * {@link IllegalArgumentException}; null or empty are no-ops.
     */
    private static void applyVisibility(Object element, String visibility) {
        if (visibility == null || visibility.isEmpty()) {
            return;
        }
        org.argouml.model.VisibilityKind vk = Model.getVisibilityKind();
        Object kind;
        if ("public".equals(visibility)) {
            kind = vk.getPublic();
        } else if ("protected".equals(visibility)) {
            kind = vk.getProtected();
        } else if ("private".equals(visibility)) {
            kind = vk.getPrivate();
        } else if ("package".equals(visibility)) {
            kind = vk.getPackage();
        } else {
            throw new IllegalArgumentException(
                    "Unknown visibility: '" + visibility
                    + "' (use public|protected|private|package)");
        }
        if (kind != null) {
            Model.getCoreHelper().setVisibility(element, kind);
        }
    }
}
