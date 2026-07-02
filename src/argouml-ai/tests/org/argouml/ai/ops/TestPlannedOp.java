/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */

package org.argouml.ai.ops;

import junit.framework.TestCase;

/**
 * Tests the {@link PlannedOp} value object.
 *
 * <p>Exercises the typed-attribute bag: each op carries a {@link PlannedOp.Type}
 * and a mutable map of fields consumed by the executor in {@code org.argouml.ai.exec}.
 */
public class TestPlannedOp extends TestCase {

    public void testAddClassCarriesAllFields() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setString("name", "Order");
        op.setInt("x", 200);
        op.setInt("y", 100);
        assertEquals("Order", op.getString("name"));
        assertEquals(200, op.getInt("x"));
        assertEquals(100, op.getInt("y"));
    }

    public void testGetTypeReturnsConstructorArg() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.LIST_CLASSES);
        assertEquals(PlannedOp.Type.LIST_CLASSES, op.getType());
    }

    public void testSetStringRoundtrips() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_ATTRIBUTE);
        op.setString("visibility", "public");
        op.setString("type", "String");
        assertEquals("public", op.getString("visibility"));
        assertEquals("String", op.getString("type"));
    }

    public void testSetStringOverwritesPreviousValue() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.RENAME_CLASS);
        op.setString("newName", "Foo");
        op.setString("newName", "Bar");
        assertEquals("Bar", op.getString("newName"));
    }

    public void testGetStringReturnsNullForUnsetKey() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        assertNull(op.getString("missing"));
    }

    public void testGetIntReturnsZeroForUnsetKey() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        assertEquals(0, op.getInt("missing"));
    }

    public void testSetIntAcceptsZeroAndNegative() {
        PlannedOp op = new PlannedOp(PlannedOp.Type.ADD_CLASS);
        op.setInt("zero", 0);
        op.setInt("neg", -42);
        assertEquals(0, op.getInt("zero"));
        assertEquals(-42, op.getInt("neg"));
    }

    public void testTypeEnumHasTenValues() {
        PlannedOp.Type[] values = PlannedOp.Type.values();
        assertEquals(10, values.length);
    }

    public void testTypeEnumValueOfRoundtrips() {
        for (PlannedOp.Type t : PlannedOp.Type.values()) {
            assertEquals(t, PlannedOp.Type.valueOf(t.name()));
        }
    }
}