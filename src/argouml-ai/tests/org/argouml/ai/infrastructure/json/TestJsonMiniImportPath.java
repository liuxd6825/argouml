package org.argouml.ai.infrastructure.json;

import junit.framework.TestCase;

public class TestJsonMiniImportPath extends TestCase {
    public void testClassExistsAtNewPath() throws Exception {
        Class<?> c = Class.forName("org.argouml.ai.infrastructure.json.JsonMini");
        assertNotNull(c);
    }

    public void testOpsPathNoLongerExists() throws Exception {
        try {
            Class.forName("org.argouml.ai.ops.JsonMini");
            fail("old JsonMini should no longer exist at ops path");
        } catch (ClassNotFoundException expected) {
        }
    }
}
