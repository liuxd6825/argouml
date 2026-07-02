/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.infrastructure.model;

import org.argouml.model.CoreFactory;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.model.UmlFactory;

/**
 * Thin static facade over ArgoUML Model APIs. Centralises which
 * subsystem touches the model so future swaps (alternative UML
 * implementations, test fakes) are localised.
 *
 * <p>All methods assume being called on the Swing EDT.</p>
 */
public final class ModelGateway {

    private ModelGateway() { }

    public static CoreFactory coreFactory() { return Model.getCoreFactory(); }
    public static Facade facade()           { return Model.getFacade(); }
    public static UmlFactory umlFactory()    { return Model.getUmlFactory(); }

    public static Object buildClass(String name, Object ns) {
        return coreFactory().buildClass(name, ns);
    }

    public static Object buildInterface(String name, Object ns) {
        return coreFactory().buildInterface(name, ns);
    }

    public static Object buildDataType(String name, Object ns) {
        return coreFactory().buildDataType(name, ns);
    }

    /**
     * Find the class in {@code namespace} whose simple name equals
     * {@code name}, or {@code null} if not found. Null/empty
     * arguments yield {@code null}.
     */
    public static Object findClassByName(String name, Object namespace) {
        if (name == null || name.isEmpty() || namespace == null) {
            return null;
        }
        Facade f = facade();
        for (Object c : f.getOwnedElements(namespace)) {
            if (f.isAClass(c) && name.equals(f.getName(c))) {
                return c;
            }
        }
        return null;
    }
}