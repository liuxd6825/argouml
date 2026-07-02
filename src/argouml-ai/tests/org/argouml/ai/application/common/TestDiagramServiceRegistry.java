/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.application.common;

import junit.framework.TestCase;

import org.argouml.ai.domain.common.ModelKind;

/**
 * Tests for {@link DiagramServiceRegistry}.
 */
public class TestDiagramServiceRegistry extends TestCase {

    public void testRegisterAndLookup() {
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        DiagramService fake = new DiagramService() {
            public ModelKind kind() {
                return ModelKind.CLASS;
            }
        };
        r.register(ModelKind.CLASS, fake);
        assertSame(fake, r.forKind(ModelKind.CLASS).get());
    }

    public void testForKindMissingReturnsEmpty() {
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        assertFalse(r.forKind(ModelKind.CLASS).isPresent());
    }

    public void testReplaceByDefaultSilently() {
        // YAGNI: replacing is allowed and last-wins; future versions
        // may log a warning but for now we accept it silently.
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        DiagramService a = new DiagramService() {
            public ModelKind kind() {
                return ModelKind.CLASS;
            }
        };
        DiagramService b = new DiagramService() {
            public ModelKind kind() {
                return ModelKind.CLASS;
            }
        };
        r.register(ModelKind.CLASS, a);
        r.register(ModelKind.CLASS, b);
        assertSame(b, r.forKind(ModelKind.CLASS).get());
    }

    public void testIsRegisteredTrue() {
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        DiagramService fake = new DiagramService() {
            public ModelKind kind() {
                return ModelKind.CLASS;
            }
        };
        r.register(ModelKind.CLASS, fake);
        assertTrue(r.isRegistered(ModelKind.CLASS));
    }

    public void testIsRegisteredFalse() {
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        assertFalse(r.isRegistered(ModelKind.CLASS));
    }
}
