/* $Id$
 *****************************************************************************
 * Copyright (c) 2009-2012 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    test (2026-07-13)
 *****************************************************************************
 */
// Copyright (c) 1996-2009 The Regents of the University of California. All
// Rights Reserved. Permission to use, copy, modify, and distribute this
// software and its documentation without fee, and without a written
// agreement is hereby granted, provided that the above copyright notice
// and this paragraph appear in all copies.  This software program and
// documentation are copyrighted by The Regents of the University of
// California. The software program and documentation are supplied "AS
// IS", without any accompanying services from The Regents. The Regents
// does not warrant that the operation of the program will be
// uninterrupted or error-free.  The end-user understands that the program
// was developed for research purposes and is advised not to rely
// exclusively on the program for any reason.  IN NO EVENT SHALL THE
// UNIVERSITY OF CALIFORNIA BE LIABLE FOR ANY PARTY FOR DIRECT, INDIRECT,
// SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
// ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE. THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.  THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package org.argouml.uml.diagram.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;

import javax.swing.Action;

import junit.framework.TestCase;

import org.argouml.i18n.Translator;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;
import org.argouml.notation.InitNotation;
import org.argouml.notation.providers.uml.InitNotationUml;
import org.argouml.profile.init.InitProfileSubsystem;
import org.argouml.uml.ui.foundation.core.ActionSetAssociationEndOrdering;

/**
 * Regression tests for the Ordering-related i18n fixes.
 *
 * <p>Three bugs were addressed:</p>
 * <ol>
 *   <li>{@code FigAssociation.FigOrdering.getOrderingName} previously
 *       returned the hardcoded English literal "{ordered}" (braces
 *       included) with a "// TODO: I18N" comment. It now wraps the
 *       localized value of "label.ordered" in braces.</li>
 *   <li>{@link ActionSetAssociationEndOrdering} previously called
 *       {@code Translator.localize("Set")} which always returned the
 *       key string verbatim because no bundle contains a "Set" key.
 *       It now uses the existing "action.set" key, which is translated
 *       in both {@code action.properties} (English) and the
 *       {@code argouml-i18n-zh} bundle (Chinese).</li>
 *   <li>The four ordering-modifier checkbox keys
 *       ("checkbox.abstract-uc", "final-uc", "root-uc",
 *       "active-uc") are now translated in the {@code argouml-i18n-zh}
 *       bundle; previously they fell back to the English literal in
 *       Chinese locales.</li>
 * </ol>
 *
 * <p>This file is intentionally ASCII-only. Chinese strings in the
 * test runtime are produced via Java's {@code Properties.load} which
 * decodes the ISO-8859-1 + backslash-uXXXX escape convention in the
 * bundle files. Java's ISO-8859-1 source encoding would otherwise
 * misinterpret raw multi-byte UTF-8 sequences as invalid
 * backslash-u escapes. Comments and string literals also avoid the
 * literal "backslash-u" sequence so the Java 9+ preprocessor's
 * Unicode-escape consumption does not shift the lexer column count
 * and produce phantom line numbers.</p>
 */
public class TestAssociationOrderingI18n extends TestCase {

    private Project project;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        InitializeModel.initializeDefault();
        new InitProfileSubsystem().init();
        project = ProjectManager.getManager().makeEmptyProject();
        new InitNotation().init();
        new InitNotationUml().init();
    }

    @Override
    public void tearDown() throws Exception {
        // Remove the project we created in setUp, otherwise the
        // ProjectManager accumulates projects across test runs and
        // subsequent tests (e.g. TestRepresentedDiagramIsolation)
        // trip on ConcurrentModificationException when iterating the
        // project list.
        if (project != null) {
            ProjectManager.getManager().removeProject(project);
            project = null;
        }
        super.tearDown();
    }

    /**
     * Bug #1 - FigAssociation.FigOrdering.getOrderingName must
     * return a localized string. In en the inner word is "ordered";
     * in zh_CN it must be a Chinese word (currently "yi pai xu" from
     * the zh_CN bundle). The braces and overall wrapper are
     * code-level, not from the bundle.
     *
     * <p>The method takes a UML {@code OrderingKind} element (NOT a
     * raw String), since it calls {@code Model.getFacade().getName}
     * which requires a real model element. The MDR backend refuses
     * to call {@code getName} on a non-element object.</p>
     */
    public void testGetOrderingNameIsLocalizedEn() {
        Locale prev = Locale.getDefault();
        Translator.setLocale("en");
        try {
            Object ordered = Model.getOrderingKind().getOrdered();
            String got = invokeGetOrderingName(ordered);
            assertEquals("{ordered}", got);
        } finally {
            Translator.setLocale(prev);
        }
    }

    public void testGetOrderingNameIsLocalizedZh() {
        Locale prev = Locale.getDefault();
        Translator.setLocale("zh");
        Translator.setLocale("zh_CN");
        try {
            Object ordered = Model.getOrderingKind().getOrdered();
            String got = invokeGetOrderingName(ordered);
            // Inner word must come from label.ordered, not be the
            // raw English "ordered".
            String expectedInner = Translator.localize("label.ordered");
            assertEquals("{" + expectedInner + "}", got);
            assertFalse("Raw 'ordered' must not appear in zh locale: " + got,
                    got.equals("{ordered}"));
        } finally {
            Translator.setLocale(prev);
        }
    }

    /**
     * Bug #1b - unordered returns empty (no annotation), even after
     * the fix (the "unordered" UML orderingKind is intentionally
     * not annotated on the diagram).
     */
    public void testGetOrderingNameUnorderedIsEmpty() {
        Object unordered = Model.getOrderingKind().getUnordered();
        String got = invokeGetOrderingName(unordered);
        assertEquals("", got);
    }

    /**
     * Bug #1c - null ordering kind returns empty (no annotation).
     */
    public void testGetOrderingNameNullIsEmpty() {
        String got = invokeGetOrderingName(null);
        assertEquals("", got);
    }

    /**
     * Reflectively call {@code FigOrdering.getOrderingName(Object)}.
     * The method lives in the package-private static nested class
     * {@code FigOrdering} (rendering the annotation next to the
     * association edge), not on the outer FigAssociation. We use
     * reflection so we can call the private method without changing
     * its visibility.
     *
     * <p>FigOrdering's constructor needs a real {@code DiagramSettings}
     * (otherwise the {@code ArgoFigText} base class NPEs), so we
     * reuse the current project's default settings.</p>
     */
    private String invokeGetOrderingName(Object orderingKind) {
        try {
            Class<?> figOrdering = Class.forName(
                    "org.argouml.uml.diagram.ui.FigOrdering");
            Object settings = ProjectManager.getManager()
                    .getCurrentProject()
                    .getProjectSettings()
                    .getDefaultDiagramSettings();
            Object instance = figOrdering.getDeclaredConstructor(
                            Object.class,
                            org.argouml.uml.diagram.DiagramSettings.class)
                    .newInstance(null, settings);
            Method m = figOrdering.getDeclaredMethod("getOrderingName", Object.class);
            m.setAccessible(true);
            return (String) m.invoke(instance, orderingKind);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Bug #2 - ActionSetAssociationEndOrdering NAME must be the
     * RESOLVED value of the "action.set" key, not the literal
     * English "Set" that the previous
     * {@code Translator.localize("Set")} call returned (because no
     * bundle carries a "Set" key).
     */
    public void testActionSetOrderingNameIsLocalizedEn() {
        Locale prev = Locale.getDefault();
        Translator.setLocale("en");
        try {
            Action a = newActionSetAssociationEndOrdering();
            String name = (String) a.getValue(Action.NAME);
            assertEquals("NAME must equal the localized value of action.set",
                    Translator.localize("action.set"), name);
            assertEquals("In en locale action.set resolves to 'Set'",
                    "Set", name);
            // Tooltip also localized.
            assertEquals("Tooltip matches NAME",
                    name, a.getValue(Action.SHORT_DESCRIPTION));
        } finally {
            Translator.setLocale(prev);
        }
    }

    public void testActionSetOrderingNameIsLocalizedZh() {
        Locale prev = Locale.getDefault();
        Translator.setLocale("zh");
        Translator.setLocale("zh_CN");
        try {
            Action a = newActionSetAssociationEndOrdering();
            String name = (String) a.getValue(Action.NAME);
            assertEquals("NAME must equal the localized value of action.set",
                    Translator.localize("action.set"), name);
            // Must NOT be the raw English "Set" anymore.
            assertFalse("Raw 'Set' must not appear as action NAME in zh",
                    "Set".equals(name));
        } finally {
            Translator.setLocale(prev);
        }
    }

    /**
     * Bug #3 - the four modifier checkboxes (Abstract/Leaf/Root/Active)
     * are now translated in the zh_CN bundle. We read the file
     * directly as raw bytes and check the key=value bytes. The values
     * are the UTF-8 encoded Chinese characters for the modifier
     * labels (after the 2025 native-UTF-8 migration).
     *
     * <p>Byte arrays (rather than string literals) are used so that
     * the Java 9+ Unicode-escape preprocessor cannot trigger on raw
     * backslash-uXXXX sequences.
     */
    public void testCheckboxModifiersTranslatedInZh() throws Exception {
        verifyZhBundleHasKey("checkbox.abstract-uc",
                new byte[] {(byte) 0xe6, (byte) 0x8a, (byte) 0xbd, (byte) 0xe8, (byte) 0xb1, (byte) 0xa1});
        verifyZhBundleHasKey("checkbox.final-uc",
                new byte[] {(byte) 0xe5, (byte) 0x8f, (byte) 0xb6, (byte) 0xe5, (byte) 0xad, (byte) 0x90});
        verifyZhBundleHasKey("checkbox.root-uc",
                new byte[] {(byte) 0xe6, (byte) 0xa0, (byte) 0xb9});
        verifyZhBundleHasKey("checkbox.active-uc",
                new byte[] {(byte) 0xe4, (byte) 0xb8, (byte) 0xbb, (byte) 0xe5, (byte) 0x8a, (byte) 0xa8});
    }

    /** Sanity check - label.ordered is also translated in zh. */
    public void testLabelOrderedTranslatedInZh() throws Exception {
        verifyZhBundleHasKey("label.ordered",
                new byte[] {(byte) 0xe5, (byte) 0xb7, (byte) 0xb2,
                            (byte) 0xe6, (byte) 0x8e, (byte) 0x92,
                            (byte) 0xe5, (byte) 0xba, (byte) 0x8f});
    }

    /**
     * Read the zh_CN bundle file from the working tree and assert the
     * given key maps to the given raw bytes. After the 2025 native
     * UTF-8 migration, bundle values are stored as raw UTF-8 bytes
     * (no longer as ISO-8859-1 + Java Unicode escapes), so the
     * comparison is against UTF-8 directly.
     */
    private void verifyZhBundleHasKey(String key, byte[] expectedRaw)
            throws IOException {
        String path = "externals/argouml-i18n-zh/src/org/argouml/i18n/"
                + bundleNameForKey(key) + "_zh_CN.properties";
        File f = new File(
                "/Users/lxd/Projects/ai/uml-project/argouml/" + path);
        assertTrue("Bundle file must exist: " + f, f.exists());
        byte[] raw = readRawLineBytes(f, key);
        assertNotNull("Missing key in zh_CN bundle: " + key, raw);
        assertEquals("Wrong zh_CN raw value for " + key, expectedRaw.length,
                raw.length);
        for (int i = 0; i < expectedRaw.length; i++) {
            assertEquals("Byte " + i + " for " + key,
                    expectedRaw[i] & 0xff, raw[i] & 0xff);
        }
    }

    /**
     * Read the raw ISO-8859-1 bytes of the key=value line, returning
     * the value (after the "=" and trimmed).
     */
    private byte[] readRawLineBytes(File f, String key) throws IOException {
        try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(
                new java.io.FileInputStream(f))) {
            byte[] keyBytes = key.getBytes("ISO-8859-1");
            byte[] buf = new byte[(int) f.length()];
            int n = in.read(buf);
            int start = 0;
            for (int i = 0; i < n; i++) {
                if (buf[i] == (byte) '\n' || i + 1 == n) {
                    int end = (buf[i] == (byte) '\n') ? i : i + 1;
                    if (startsWith(buf, start, end, keyBytes)
                            && containsEq(buf, start, end)) {
                        int eq = indexOfEq(buf, start, end);
                        int vStart = eq + 1;
                        int vEnd = end;
                        // trim leading and trailing spaces
                        while (vStart < vEnd && buf[vStart] == (byte) ' ') {
                            vStart++;
                        }
                        // Trim trailing CR (CRLF line ending) or space.
                        while (vEnd > vStart
                                && (buf[vEnd - 1] == (byte) ' '
                                    || buf[vEnd - 1] == (byte) '\r')) {
                            vEnd--;
                        }
                        byte[] out = new byte[vEnd - vStart];
                        System.arraycopy(buf, vStart, out, 0, out.length);
                        return out;
                    }
                    start = end + 1;
                }
            }
        }
        return null;
    }

    private static boolean startsWith(byte[] buf, int start, int end,
            byte[] prefix) {
        if (end - start < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (buf[start + i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean containsEq(byte[] buf, int start, int end) {
        return indexOfEq(buf, start, end) >= 0;
    }

    private static int indexOfEq(byte[] buf, int start, int end) {
        for (int i = start; i < end; i++) {
            if (buf[i] == (byte) '=') return i;
        }
        return -1;
    }

    /** Map a key like "label.ordered" to its bundle base name "label". */
    private static String bundleNameForKey(String key) {
        int dot = key.indexOf('.');
        return dot > 0 ? key.substring(0, dot) : "ApplicationBundle";
    }

    /**
     * ActionSetAssociationEndOrdering has a protected constructor (it
     * is a singleton-by-design with {@link #getInstance()}). We
     * reflectively call it so this test can run from a different
     * package.
     */
    private static Action newActionSetAssociationEndOrdering() {
        try {
            java.lang.reflect.Constructor<?> ctor =
                    ActionSetAssociationEndOrdering.class
                            .getDeclaredConstructor();
            ctor.setAccessible(true);
            return (Action) ctor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sanity guard - keep a UseCase creation working so the project
     * model is initialised even if a test method only exercises
     * static helpers.
     */
    public void testProjectInitialised() {
        assertNotNull("ProjectManager must have a current project after setUp",
                ProjectManager.getManager().getCurrentProject());
        // Just touch the model to confirm it's loaded.
        Object usecase = Model.getUseCasesFactory().createUseCase();
        assertNotNull(usecase);
        Model.getUmlFactory().delete(usecase);
    }
}