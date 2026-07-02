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

/**
 * Tests for the {@link DiagramServiceException} hierarchy and its
 * four concrete subtypes.
 */
public class TestDiagramServiceException extends TestCase {

    public void testBaseIsRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(
            DiagramServiceException.class));
    }

    public void testCodesAndStatus() {
        assertEquals("X", new InvalidArgumentException("X", "bad").code());
        assertEquals(400, new InvalidArgumentException("X", "bad").httpStatus());
        assertEquals("X", new NotFoundException("X", "missing").code());
        assertEquals(404, new NotFoundException("X", "missing").httpStatus());
        assertEquals("X", new DuplicateException("X", "dup").code());
        assertEquals(409, new DuplicateException("X", "dup").httpStatus());
        assertEquals("X", new UnsupportedException("X", "nope").code());
        assertEquals(501, new UnsupportedException("X", "nope").httpStatus());
    }

    public void testSubtypeHierarchy() {
        assertTrue(DiagramServiceException.class.isAssignableFrom(
            InvalidArgumentException.class));
        assertTrue(DiagramServiceException.class.isAssignableFrom(
            NotFoundException.class));
        assertTrue(DiagramServiceException.class.isAssignableFrom(
            DuplicateException.class));
        assertTrue(DiagramServiceException.class.isAssignableFrom(
            UnsupportedException.class));
    }

    public void testMessagePropagation() {
        assertEquals("bad", new InvalidArgumentException("X", "bad").getMessage());
        assertEquals("missing",
            new NotFoundException("X", "missing").getMessage());
    }
}
