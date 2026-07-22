/* $Id$
 *****************************************************************************
 * Copyright (c) 2026 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    utf8-migration
 *****************************************************************************
 */
package org.argouml.i18n;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;

import junit.framework.TestCase;

/**
 * Test that .properties bundles are loaded as UTF-8 and that
 * legacy Unicode escapes (backslash + uXXXX) continue to work.
 *
 * <p>Background: ArgoUML bundles historically used ISO-8859-1 encoding
 * with Unicode escapes for non-ASCII characters. JDK 9+ exposes a
 * {@link ResourceBundle.Control} subclassing API that allows reading
 * .properties files as UTF-8. Both formats are still accepted: Unicode
 * escapes are decoded by {@link java.util.Properties#load} regardless of
 * source encoding, so legacy bundles remain valid; new translations can
 * use native characters directly.
 */
public class TestBundleEncoding extends TestCase {

    /**
     * ResourceBundle.Control that loads .properties files as UTF-8.
     * Mirrors Translator.UTF8_CONTROL for direct bundle loading.
     */
    private static final ResourceBundle.Control UTF8_CONTROL =
        new ResourceBundle.Control() {
            @Override
            public ResourceBundle newBundle(String baseName, Locale locale,
                                            String format, ClassLoader loader,
                                            boolean reload)
                    throws java.io.IOException,
                           IllegalAccessException,
                           InstantiationException {
                if (!"java.properties".equals(format)) {
                    return super.newBundle(baseName, locale, format,
                                           loader, reload);
                }
                String bundleName = toBundleName(baseName, locale);
                String resourceName = toResourceName(bundleName, "properties");
                java.io.InputStream in;
                try {
                    if (reload) {
                        java.net.URL url = loader.getResource(resourceName);
                        if (url == null) {
                            return null;
                        }
                        in = url.openStream();
                    } else {
                        in = loader.getResourceAsStream(resourceName);
                    }
                    if (in == null) {
                        return null;
                    }
                } catch (java.io.IOException e) {
                    return null;
                }
                try {
                    java.io.InputStreamReader reader =
                        new java.io.InputStreamReader(
                            in,
                            StandardCharsets.UTF_8);
                    java.util.Properties props = new java.util.Properties();
                    try {
                        props.load(reader);
                    } finally {
                        reader.close();
                    }
                    java.io.ByteArrayOutputStream baos =
                        new java.io.ByteArrayOutputStream();
                    props.store(baos, null);
                    return new java.util.PropertyResourceBundle(
                        new java.io.ByteArrayInputStream(baos.toByteArray()));
                } catch (java.io.IOException e) {
                    return null;
                }
            }
        };

    /** Remember the system default locale and restore after the test. */
    private Locale savedLocale;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        savedLocale = Locale.getDefault();
        // Translator caches bundles per Locale and re-initializes itself
        // lazily from system properties (user.language/country) on the
        // first call to localize(). When surefire forks the JVM with
        // -Duser.language=en, that lazy init silently overrides our
        // intended locale. Calling init() explicitly with the desired
        // locale marks Translator as initialized and steers the bundle
        // loader to the right locale.
        Translator.init(savedLocale.toString());
        // Reset the cache so subsequent setLocale calls in individual
        // tests work without carrying cached en bundles.
        Translator.setLocale(savedLocale);
    }

    @Override
    protected void tearDown() throws Exception {
        // Restore the JVM default Locale and clear Translator's cache
        // so subsequent tests in the same JVM do not see this test's
        // locale.
        Translator.setLocale(savedLocale);
        super.tearDown();
    }

    /**
     * English baseline bundles load fine and return ASCII strings
     * exactly as before.
     */
    public void testEnglishBundleAscii() {
        // Use Translator.setLocale to also reset the bundle cache.
        Translator.setLocale(Locale.ENGLISH);
        assertEquals("Set", Translator.localize("action.set"));
        assertEquals("File", Translator.localize("menu.file"));
        assertEquals("OK", Translator.localize("button.ok"));
    }

    /**
     * Unicode escapes (backslash + uXXXX) inside legacy zh_CN bundle
     * files (still written as ISO-8859-1 bytes on disk) continue to
     * decode to the expected Chinese characters. This is the critical
     * regression test: it would fail if Translator stopped using Java's
     * {@code Properties.load} decoding.
     */
    public void testLegacyEscapeDecodingInZhCnBundle() {
        Translator.setLocale(new Locale("zh", "CN"));
        assertEquals("\u62bd\u8c61",
                     Translator.localize("checkbox.abstract-uc"));
        assertEquals("\u53f6\u5b50",
                     Translator.localize("checkbox.final-uc"));
        assertEquals("\u6839",
                     Translator.localize("checkbox.root-uc"));
        assertEquals("\u4e3b\u52a8",
                     Translator.localize("checkbox.active-uc"));
        assertEquals("\u5df2\u6392\u5e8f",
                     Translator.localize("label.ordered"));
        assertEquals("\u4e0b\u79fb\u4e00\u5c42",
                     Translator.localize("action.send-backward"));
        assertEquals("\u79fb\u81f3\u5e95\u5c42",
                     Translator.localize("action.send-to-back"));
        assertEquals("\u79fb\u81f3\u9876\u5c42",
                     Translator.localize("action.bring-to-front"));
    }

    /**
     * Direct UTF-8 Control load of a zh_CN bundle succeeds and
     * decodes Unicode escapes (backslash + uXXXX). This test verifies
     * the JDK 9+ API path used by the production Translator.
     */
    public void testUtf8ControlDirectLoadZh() {
        ResourceBundle bundle = ResourceBundle.getBundle(
                "org.argouml.i18n.checkbox",
                new Locale("zh", "CN"),
                UTF8_CONTROL);
        assertNotNull("bundle did not load", bundle);
        assertEquals("\u62bd\u8c61", bundle.getString("checkbox.abstract-uc"));
    }

    /**
     * Direct UTF-8 Control load of the zh_TW bundle works and the
     * Unicode escapes decode. Verifies the same loader supports
     * multiple locales (this is what the existing zh_TW contributors
     * rely on).
     */
    public void testUtf8ControlDirectLoadZhTw() {
        ResourceBundle bundle = ResourceBundle.getBundle(
                "org.argouml.i18n.checkbox",
                new Locale("zh", "TW"),
                UTF8_CONTROL);
        assertNotNull("zh_TW bundle did not load", bundle);
        assertTrue("zh_TW bundle has checkbox.abstract-uc",
                   bundle.containsKey("checkbox.abstract-uc"));
    }

    /**
     * When loaded via Translator.localize with English locale, the
     * fallback path returns the en bundle value. This guards against
     * an accidental regression where zh_CN runs without zh_TW also
     * resolving properly.
     */
    public void testEnglishFallbackWhenLocaleChanged() {
        Translator.setLocale(Locale.ENGLISH);
        assertEquals("Set", Translator.localize("action.set"));
        assertEquals("Abstract",
                     Translator.localize("checkbox.abstract-uc"));
    }

    /**
     * The action.send-to-back and action.bring-to-front values are
     * distinct in zh_CN after the prior batch's duplicate-value fix.
     * This test pins that fix in addition to the new UTF-8 path.
     */
    public void testZhCnSendToBackDistinctFromBringToFront() {
        Translator.setLocale(new Locale("zh", "CN"));
        String sendToBack = Translator.localize("action.send-to-back");
        String bringToFront = Translator.localize("action.bring-to-front");
        assertFalse("send-to-back and bring-to-front must be distinct "
                    + "(regression: prior batch fixed duplicate value)",
                    sendToBack.equals(bringToFront));
        assertEquals("\u79fb\u81f3\u5e95\u5c42", sendToBack);
        assertEquals("\u79fb\u81f3\u9876\u5c42", bringToFront);
    }

    /**
     * The zh_CN bundle for the locale must NOT fall back through to
     * en. The Locale("zh", "") request should land on zh_CN if it's
     * the most-specific match ArgoUML ships; this pins that the
     * fallback chain does not silently drop CJK text.
     */
    public void testZhCnLocaleResolvesToChinese() {
        Translator.setLocale(new Locale("zh", "CN"));
        String abstractLabel =
            Translator.localize("checkbox.abstract-uc");
        assertFalse(
            "expected Chinese for checkbox.abstract-uc, got English",
            "Abstract".equals(abstractLabel));
        assertEquals("\u62bd\u8c61", abstractLabel);
    }
}
