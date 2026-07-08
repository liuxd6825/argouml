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
 * Tests for the {@link ModelKind#USECASE} value added as part of
 * the UseCase Diagram API foundation (Phase 1). Only exercises the
 * enum wire/value semantics; the {@link DiagramOperations} integration
 * (factory dispatch + kindOf recognition) is covered by the
 * {@code TestDiagramOperations} test if/when added.
 */
public class TestModelKindUSECASE extends TestCase {

    public void testWireValueIsUsCasediagram() {
        assertEquals("usecasediagram", ModelKind.USECASE.wireValue());
    }

    public void testFromWireValueUsCasediagram() {
        assertEquals(ModelKind.USECASE,
                ModelKind.fromWireValue("usecasediagram"));
    }

    public void testFromWireValueNullThrows() {
        try {
            ModelKind.fromWireValue(null);
            fail("expected IllegalArgumentException for null input");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testFromWireValueUnknownThrows() {
        try {
            ModelKind.fromWireValue("not-a-real-kind");
            fail("expected IllegalArgumentException for unknown wire value");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testValuesContainsUSECASE() {
        boolean found = false;
        for (ModelKind k : ModelKind.values()) {
            if (k == ModelKind.USECASE) {
                found = true;
                break;
            }
        }
        assertTrue("ModelKind.values() must contain USECASE", found);
    }

    public void testClassAndUSECASEHaveDistinctWireValues() {
        assertFalse("classdiagram and usecasediagram must differ",
                ModelKind.CLASS.wireValue().equals(
                        ModelKind.USECASE.wireValue()));
    }
}
