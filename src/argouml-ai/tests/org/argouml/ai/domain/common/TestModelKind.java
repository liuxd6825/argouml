/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.domain.common;

import junit.framework.TestCase;

/**
 * Tests for {@link ModelKind}.
 */
public class TestModelKind extends TestCase {

    public void testClassIsKnown() {
        assertEquals("classdiagram", ModelKind.CLASS.wireValue());
    }

    public void testUnknownKindThrows() {
        try {
            ModelKind.fromWireValue("usecase");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRoundTrip() {
        assertSame(ModelKind.CLASS, ModelKind.fromWireValue(ModelKind.CLASS.wireValue()));
    }

    public void testNullWireThrows() {
        try {
            ModelKind.fromWireValue(null);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }
}
