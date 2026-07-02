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

import java.util.Collection;
import java.util.List;

import org.argouml.model.CoreFactory;
import org.argouml.model.CoreHelper;
import org.argouml.model.Facade;
import org.argouml.model.Model;

/**
 * UML Attribute (typed feature of a Classifier) creation / mutation
 * against the model layer. Pure functions on an owning class model
 * element; the class has no mutable state and depends only on the
 * ArgoUML model facade.
 *
 * <p><b>Architectural boundary.</b> Same as {@link ClassOperations}:
 * no HTTP, no AI, no inbound / outbound adapter layer, no Swing UI,
 * no diagram-graph-model mutation, no {@code Fig} placement. All
 * methods are safe to call on the Swing EDT.
 *
 * <p>Attributes are owned by a Class (they do not live on the
 * diagram's graph model directly), so all methods here operate on a
 * Class model element passed by the caller. The caller is responsible
 * for resolving the owner class to a model element — typically via
 * {@link ClassOperations#findByName}.
 */
public final class AttributeOperations {

    private AttributeOperations() {
    }

    /**
     * Build a UML Attribute on {@code ownerClass} with the given
     * simple name and optional type. If {@code typeName} is
     * non-null and non-empty a {@code DataType} of that name is
     * created in the class's namespace and attached as the attribute
     * type; if it is null or empty the attribute is left untyped
     * (which the renderer displays as {@code attr : Untyped}). The
     * optional {@code visibility} is one of {@code "public"},
     * {@code "protected"}, {@code "private"}, or {@code "package"};
     * a null value leaves visibility at the UML default (public).
     *
     * @param ownerClass the Class that will own the new attribute;
     *                   must not be null.
     * @param name       the simple (unqualified) attribute name;
     *                   must be non-null and non-empty.
     * @param typeName   the simple type name, or null/empty for no
     *                   explicit type.
     * @param visibility the visibility kind name, or null/empty for
     *                   default.
     * @return the newly created model element.
     * @throws IllegalArgumentException if {@code ownerClass} is null,
     *                                  or {@code name} is null or
     *                                  empty, or {@code visibility}
     *                                  is not null/empty and is not
     *                                  one of the four legal values.
     */
    public static Object build(Object ownerClass, String name,
                               String typeName, String visibility) {
        if (ownerClass == null) {
            throw new IllegalArgumentException(
                    "ownerClass must not be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                    "Attribute name must not be empty");
        }
        Object type = resolveType(ownerClass, typeName);
        Object attr = Model.getCoreFactory().buildAttribute2(
                ownerClass, type);
        Model.getCoreHelper().setName(attr, name);
        applyVisibility(attr, visibility);
        return attr;
    }

    /**
     * Find the attribute on {@code ownerClass} whose simple name
     * equals {@code name}.
     *
     * @param ownerClass the class whose attributes are searched;
     *                   a {@code null} value yields a {@code null}
     *                   result without throwing.
     * @param name       the simple name to search for; a null or
     *                   empty value yields a {@code null} result
     *                   without throwing.
     * @return the matching attribute model element, or {@code null}
     *         if no attribute of that name exists on the class.
     */
    public static Object findByName(Object ownerClass, String name) {
        if (ownerClass == null || name == null || name.isEmpty()) {
            return null;
        }
        Facade facade = Model.getFacade();
        List attrs = facade.getAttributes(ownerClass);
        if (attrs == null) {
            return null;
        }
        for (Object a : attrs) {
            if (name.equals(facade.getName(a))) {
                return a;
            }
        }
        return null;
    }

    /**
     * Delete an attribute from its owning class. Mirrors the diagram
     * delete pattern in {@link ClassOperations#delete}: errors during
     * the model delete are swallowed because the MDR backend may
     * refuse to delete an element with live inbound references,
     * which is fine for the caller's purposes.
     *
     * @param ownerClass the owning class (currently unused; reserved
     *                   for future use when the MDR backend requires
     *                   the parent for ownership).
     * @param attribute  the attribute model element; a {@code null}
     *                   value is a no-op.
     */
    public static void delete(Object ownerClass, Object attribute) {
        if (attribute == null) {
            return;
        }
        try {
            Model.getUmlFactory().delete(attribute);
        } catch (RuntimeException ignored) {
            // model may refuse; treat as best-effort
        }
    }

    /**
     * Resolve a type name to a {@code DataType} model element in the
     * owning class's namespace. Returns {@code null} if
     * {@code typeName} is null or empty.
     *
     * <p>Namespace resolution uses
     * {@link Facade#getNamespace(Object)} on the owning class. This
     * is the Task-15 corrected pattern: the diagram's own namespace
     * is not relevant for an attribute's type because the attribute
     * does not live on the diagram graph model.
     *
     * <p><b>Deduplication.</b> Before allocating a new {@code DataType},
     * the namespace is searched for an existing one with the same
     * name; the existing one is reused if found. This prevents the
     * "30+ duplicate DataType" build-up that occurred when every
     * {@code POST /d/{d}/classes/{c}/attributes} call previously
     * created a fresh DataType model element instead of reusing the
     * canonical one for the same primitive type.
     */
    private static Object resolveType(Object ownerClass, String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return null;
        }
        Object ns = Model.getFacade().getNamespace(ownerClass);
        if (ns == null) {
            return null;
        }
        Facade facade = Model.getFacade();
        Collection owned = facade.getOwnedElements(ns);
        if (owned != null) {
            for (Object candidate : owned) {
                if (facade.isADataType(candidate)
                        && typeName.equals(facade.getName(candidate))) {
                    return candidate;
                }
            }
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
        Model.getVisibilityKind(); // touch to ensure initialised
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
