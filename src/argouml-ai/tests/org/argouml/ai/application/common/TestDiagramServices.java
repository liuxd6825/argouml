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

import org.argouml.ai.application.classdiagram.ClassDiagramService;
import org.argouml.ai.domain.common.ModelKind;

/**
 * Tests for {@link DiagramServices}. Pure process-wide bootstrap
 * helper so no ArgoUML subsystem needs to be initialised; the tests
 * run on a plain JVM.
 */
public class TestDiagramServices extends TestCase {

    public void testRegistryHasClassService() {
        assertTrue("registry must have a CLASS service registered",
                DiagramServices.registry().forKind(ModelKind.CLASS).isPresent());
    }

    public void testClassSvcIsClassDiagramService() {
        ClassDiagramService svc = DiagramServices.classSvc();
        assertNotNull("classSvc() must not return null", svc);
        assertTrue("classSvc() must be a ClassDiagramService instance",
                svc instanceof ClassDiagramService);
    }

    public void testUnknownKindReturnsEmpty() {
        assertFalse("registry().forKind(null) must be empty (no null key "
                + "is ever registered)",
                DiagramServices.registry().forKind(null).isPresent());
    }

    public void testRegistryIsSameAcrossCalls() {
        // The bootstrap must return the same singleton registry on
        // every call (process-wide stable handle).
        assertSame(DiagramServices.registry(), DiagramServices.registry());
    }

    public void testClassSvcIsSameAcrossCalls() {
        // Same principle for the typed accessor.
        assertSame(DiagramServices.classSvc(), DiagramServices.classSvc());
    }
}