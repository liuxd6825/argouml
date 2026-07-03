# ArgoUML HTTP Server Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a loopback-only HTTP Server to ArgoUML's `argouml-ai` module that exposes the existing class-diagram CRUD as REST endpoints, sharing business logic with the AI `OpExecutor` through a new `ClassDiagramService`.

**Architecture:** 4-layer Hexagonal (Inbound → Application → Domain ← Infrastructure), strict per-diagram-kind sub-package convention (`<layer>/<kind>/...` with `<layer>/common/` for shared code). MVP implements only `classdiagram`; future kinds slot into the same shape.

**Tech Stack:** NanoHTTPD 2.x (embedded HTTP), JDK 8 (`HttpURLConnection` reference), existing `argouml-core-model` (`Model.getCoreFactory()` etc.), GEF (`MutableGraphModel`, `Fig`), ArgoUML `UndoManager`. No new third-party deps besides NanoHTTPD.

**Reference design doc:** `docs/plans/2026-06-30-http-server-design.md`

**Coding conventions to honor:**
- JUnit 3 only (`import junit.framework.TestCase`)
- Java source encoding: ISO-8859-1 (use Unicode escapes for non-ASCII in source)
- i18n: `org.argouml.i18n.Translator.localize(...)` for UI strings; `.properties` ISO-8859-1
- No commits — user manages git history (AGENTS.md)

**Build / run reference commands** (reused across tasks):
```bash
# Compile current module + install to ~/.m2
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -f src/argouml-ai/pom.xml install -Dmaven.test.skip=true -o

# Run a single test class
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -f src/argouml-ai/pom.xml test -Dtest=TestClassName -o

# Full app rebuild
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -pl src/argouml-app -am package -DskipTests -o

# Smoke-run
java -cp src/argouml-build/target/argouml-jar-with-dependencies.jar:$(echo /tmp/argouml-deps/*.jar | tr ' ' ':'):~/.m2/repository/org/argouml/argouml-ai/*/argouml-ai-*.jar org.argouml.application.Main
```

---

## Task 1: Add NanoHTTPD dependency to argouml-ai POM

**Files:**
- Modify: `src/argouml-ai/pom.xml`

**Step 1: Add NanoHTTPD 2.3.1 dependency** (corrected from 2.3.3 which doesn't exist on Maven Central; 2.3.1 is the latest published)

In `src/argouml-ai/pom.xml`, insert before the closing `</dependencies>`:

```xml
    <dependency>
      <groupId>org.nanohttpd</groupId>
      <artifactId>nanohttpd</artifactId>
      <version>2.3.1</version>
      <scope>compile</scope>
    </dependency>
```

**Step 2: Verify dependency resolves**

Run:
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home \
  PATH=$JAVA_HOME/bin:$PATH \
  mvn -f src/argouml-ai/pom.xml dependency:resolve -o
```

Expected: the dependency tree prints `org.nanohttpd:nanohttpd:jar:2.3.1:compile`.

---

## Task 2: Create the `common` package layout under `application` and `domain`

**Files:**
- Create (empty files with package declaration): 4 stub files to lock in the package structure
  - `src/argouml-ai/src/org/argouml/ai/application/common/package-info.java`
  - `src/argouml-ai/src/org/argouml/ai/application/classdiagram/package-info.java`
  - `src/argouml-ai/src/org/argouml/ai/domain/common/package-info.java`
  - `src/argouml-ai/src/org/argouml/ai/domain/classdiagram/package-info.java`

**Step 1: Create the 4 `package-info.java` files** with this body:

```java
@org.argouml.i18n.Translatable("Module.argouml-ai")
package PACKAGE_PATH;

// Replace PACKAGE_PATH with actual package:
//   org.argouml.ai.application.common
//   org.argouml.ai.application.classdiagram
//   org.argouml.ai.domain.common
//   org.argouml.ai.domain.classdiagram
```

**Step 2: Verify build still succeeds**

Run: `mvn -f src/argouml-ai/pom.xml install -Dmaven.test.skip=true -o`
Expected: `BUILD SUCCESS`. This confirms package hierarchy is reachable from Main.

**Step 3: Stop** — these files are tiny package markers. Save without committing.

---

## Task 3: Move `JsonMini` from `ops` to `infrastructure.json`

**Files:**
- Move: `src/argouml-ai/src/org/argouml/ai/ops/JsonMini.java` → `src/argouml-ai/src/org/argouml/ai/infrastructure/json/JsonMini.java`
- Modify: package declaration only

**Step 1: Write the failing test**

Create `src/argouml-ai/tests/org/argouml/ai/infrastructure/json/TestJsonMiniImportPath.java`:

```java
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
```

**Step 2: Run test to confirm it fails**

Run:
```bash
mvn -f src/argouml-ai/pom.xml test -Dtest=TestJsonMiniImportPath -o
```
Expected: `Tests run: 2, Failures: 1` — `testClassExistsAtNewPath` fails (ClassNotFoundException on the new path).

**Step 3: Move the file**

- `git mv src/argouml-ai/src/org/argouml/ai/ops/JsonMini.java src/argouml-ai/src/org/argouml/ai/infrastructure/json/JsonMini.java` (or pure mv if not under git here)
- Inside the file, change `package org.argouml.ai.ops;` to `package org.argouml.ai.infrastructure.json;`
- Add `package org.argouml.ai.infrastructure.json;` import references inside the file's own body if needed (should be none — file is self-contained)

**Step 4: Fix every stale `org.argouml.ai.ops.JsonMini` import across `argouml-ai/src/`**

Run a project-wide replacement:
```bash
grep -rl "org.argouml.ai.ops.JsonMini" src/argouml-ai/src/
```
For every listed file, replace `org.argouml.ai.ops.JsonMini` with `org.argouml.ai.infrastructure.json.JsonMini`. Known files likely to match:
- `src/argouml-ai/src/org/argouml/ai/tools/ProjectSnapshot.java`
- `src/argouml-ai/src/org/argouml/ai/ops/PlannedOpParser.java`

**Step 5: Run test to confirm it passes**

Run: `mvn -f src/argouml-ai/pom.xml test -Dtest=TestJsonMiniImportPath -o`
Expected: `Tests run: 2, Failures: 0`.

**Step 6: Run the AI module's existing test suite to ensure nothing else broke**

Run: `mvn -f src/argouml-ai/pom.xml test -o`
Expected: all green; in particular `TestPlannedOpParser` and `TestProjectSnapshot` pass without modification (semantics unchanged).

---

## Task 4: Add `ModelKind` enum to `domain.common`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/domain/common/ModelKind.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/domain/common/TestModelKind.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.domain.common;

import junit.framework.TestCase;

public class TestModelKind extends TestCase {
    public void testClassIsKnown() {
        assertEquals("classdiagram",
            ModelKind.CLASS.wireValue());
    }

    public void testUnknownKindThrows() {
        try {
            ModelKind.fromWireValue("usecase");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void testRoundTrip() {
        assertSame(ModelKind.CLASS,
            ModelKind.fromWireValue(ModelKind.CLASS.wireValue()));
    }
}
```

**Step 2: Run test to confirm it fails**

Run: `mvn -f src/argouml-ai/pom.xml test -Dtest=TestModelKind -o`
Expected: `Tests run: 3, Failures: 3` (compilation error or class not found).

**Step 3: Implement**

```java
package org.argouml.ai.domain.common;

/**
 * Enumeration of UML diagram kinds this server understands.
 *
 * <p>The {@link #wireValue()} string is the canonical lowercase path
 * segment used in REST URLs and the on-disk project model.</p>
 *
 * <p>YAGNI: only {@link #CLASS} is implemented. Other kinds
 * (use case, sequence, activity, state, deployment) are intentionally
 * not added; they will arrive together with their
 * {@code domain.}{@code <kind>}{} sub-package and a corresponding
 * {@code application.}{@code <kind>}{}DiagramService registered via
 * {@code DiagramServiceRegistry.register(...)}.</p>
 */
public enum ModelKind {
    CLASS("classdiagram");

    private final String wire;

    ModelKind(String wire) {
        this.wire = wire;
    }

    public String wireValue() {
        return wire;
    }

    public static ModelKind fromWireValue(String s) {
        if (s == null) {
            throw new IllegalArgumentException("wire value is null");
        }
        for (ModelKind k : values()) {
            if (k.wire.equals(s)) {
                return k;
            }
        }
        throw new IllegalArgumentException(
            "Unknown diagram kind: '" + s + "'");
    }
}
```

**Step 4: Run test to confirm it passes**

Run: `mvn -f src/argouml-ai/pom.xml test -Dtest=TestModelKind -o`
Expected: `Tests run: 3, Failures: 0`.

---

## Task 5: Add `DiagramLocator` to `domain.common`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/domain/common/DiagramLocator.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/domain/common/TestDiagramLocator.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.domain.common;

import junit.framework.TestCase;
import org.argouml.model.InitializeModel;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.uml.diagram.ArgoDiagram;

public class TestDiagramLocator extends TestCase {
    public void setUp() throws Exception {
        InitializeModel.initializeDefault();
        Project p = ProjectManager.getManager().getCurrentProject();
        p.addMemberDiagram("MyClassDiagram",
            org.argouml.uml.diagram.DiagramFactory.getInstance()
                .createDiagram("class",
                    ProjectManager.getManager().getCurrentProject()
                        .getModel(), null));
    }

    public void testFindExisting() {
        ArgoDiagram d = DiagramLocator.byName("MyClassDiagram");
        assertNotNull("expected non-null diagram", d);
        assertEquals("MyClassDiagram", DiagramLocator.nameOf(d));
    }

    public void testMissingThrows() {
        try {
            DiagramLocator.byName("DoesNotExist");
            fail("expected DiagramNotFoundException");
        } catch (DiagramLocator.DiagramNotFoundException expected) {
        }
    }
}
```

**Step 2: Run test to confirm it fails**

Run: `mvn -f src/argouml-ai/pom.xml test -Dtest=TestDiagramLocator -o`
Expected: compile or test failure.

**Step 3: Implement**

```java
package org.argouml.ai.domain.common;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.uml.diagram.ArgoDiagram;
import java.util.List;

/**
 * Resolves a diagram by display name within the currently open project.
 *
 * <p>Single point of truth for {@code name \u2192 ArgoDiagram}.
 * Returns a domain exception on miss so callers can map to 404.</p>
 */
public final class DiagramLocator {
    private DiagramLocator() {}

    public static ArgoDiagram byName(String name) {
        if (name == null || name.isEmpty()) {
            throw new DiagramNotFoundException("(null or empty)");
        }
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p == null) {
            throw new DiagramNotFoundException("(no current project)");
        }
        for (Object d : p.getDiagrams()) {
            ArgoDiagram ad = (ArgoDiagram) d;
            if (name.equals(ad.getName())) {
                return ad;
            }
        }
        throw new DiagramNotFoundException(name);
    }

    public static String nameOf(ArgoDiagram d) {
        return d == null ? null : d.getName();
    }

    @SuppressWarnings("serial")
    public static class DiagramNotFoundException extends RuntimeException {
        public DiagramNotFoundException(String name) {
            super("diagram not found: " + name);
        }
    }
}
```

**Step 4: Run test to confirm it passes**

Run: `mvn -f src/argouml-ai/pom.xml test -Dtest=TestDiagramLocator -o`
Expected: green.

**Note**: If the test setup line `addMemberDiagram(name, DiagramFactory.getInstance().createDiagram(...))` fails to type-check against the actual AGENTS-version signatures, look at `TestProject.java` for the exact pattern and align.

---

## Task 6: Add the `DiagramServiceException` hierarchy to `application.common`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/application/common/DiagramServiceException.java`
- Plus 3 subtypes: `InvalidArgumentException`, `NotFoundException`, `DuplicateException`, `UnsupportedException`
- Test: `src/argouml-ai/tests/org/argouml/ai/application/common/TestDiagramServiceException.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.application.common;

import junit.framework.TestCase;

public class TestDiagramServiceException extends TestCase {
    public void testCodesAndStatus() {
        assertEquals(400, new InvalidArgumentException("X", "bad").httpStatus());
        assertEquals("X", new InvalidArgumentException("X", "bad").code());
        assertEquals(404, new NotFoundException("X", "missing").httpStatus());
        assertEquals(409, new DuplicateException("X", "dup").httpStatus());
        assertEquals(501, new UnsupportedException("X", "nope").httpStatus());
    }
    public void testBaseIsRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(
            DiagramServiceException.class));
    }
}
```

**Step 2: Run test (fails with compile error)**

**Step 3: Implement** one abstract base + 4 concrete classes, each ~10 lines. Each carries `code` and `httpStatus()`:

```java
// DiagramServiceException.java
package org.argouml.ai.application.common;

public abstract class DiagramServiceException extends RuntimeException {
    private final String code;
    protected DiagramServiceException(String code, String msg) {
        super(msg);
        this.code = code;
    }
    public String code() { return code; }
    public abstract int httpStatus();
}
```

```java
// InvalidArgumentException.java
package org.argouml.ai.application.common;
public class InvalidArgumentException extends DiagramServiceException {
    public InvalidArgumentException(String code, String msg) {
        super(code, msg);
    }
    public int httpStatus() { return 400; }
}
```

```java
// NotFoundException.java
package org.argouml.ai.application.common;
public class NotFoundException extends DiagramServiceException {
    public NotFoundException(String code, String msg) {
        super(code, msg);
    }
    public int httpStatus() { return 404; }
}
```

```java
// DuplicateException.java
package org.argouml.ai.application.common;
public class DuplicateException extends DiagramServiceException {
    public DuplicateException(String code, String msg) {
        super(code, msg);
    }
    public int httpStatus() { return 409; }
}
```

```java
// UnsupportedException.java
package org.argouml.ai.application.common;
public class UnsupportedException extends DiagramServiceException {
    public UnsupportedException(String code, String msg) {
        super(code, msg);
    }
    public int httpStatus() { return 501; }
}
```

**Step 4: Run test, expect green.**

---

## Task 7: Add `UndoScope` to `application.common`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/application/common/UndoScope.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/application/common/TestUndoScope.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.application.common;

import junit.framework.TestCase;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;

public class TestUndoScope extends TestCase {
    public void setUp() { InitializeModel.initializeDefault(); }

    public void testCommitAppliesChange() {
        Project p = ProjectManager.getManager().getCurrentProject();
        Object ns = p.getModel();
        try (UndoScope s = UndoScope.open("test")) {
            Object c = Model.getCoreFactory().buildClass("Foo", ns);
            assertNotNull(c);
        }
        assertNotNull("class should remain after commit",
            Model.getFacade().lookup("Foo", ns));
    }

    public void testRollbackDiscardsChange() {
        Project p = ProjectManager.getManager().getCurrentProject();
        Object ns = p.getModel();
        try (UndoScope s = UndoScope.open("test")) {
            Object c = Model.getCoreFactory().buildClass("Bar", ns);
            s.markRollback();
            // we did not commit; expectation is the class is removed.
        }
        assertNull("class should be gone after rollback",
            Model.getFacade().lookup("Bar", ns));
    }
}
```

**Step 2: Run test (fails compile).**

**Step 3: Implement**

```java
package org.argouml.ai.application.common;

import java.util.ArrayList;
import java.util.List;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.model.UmlFactory;

/**
 * Try-with-resources transaction wrapper around the project's
 * UndoManager and a list of compensating Runnables.
 *
 * <p>Use {@link #open(String)} to start a scope; any number of model
 * mutations happen inside; on close, the scope commits unless
 * {@link #markRollback()} was called, in which case the
 * compensating Runnables run in reverse order.</p>
 */
public final class UndoScope implements AutoCloseable {
    private final String label;
    private final List<Runnable> undos = new ArrayList<Runnable>();
    private boolean rollback;
    private boolean closed;

    private UndoScope(String label) {
        this.label = label;
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p != null && p.getUndoManager() != null) {
            p.getUndoManager().startInteraction(label);
        }
    }

    public static UndoScope open(String label) { return new UndoScope(label); }

    public void recordUndo(Runnable r) { if (r != null) undos.add(r); }

    public void markRollback() { this.rollback = true; }

    public void close() {
        if (closed) return;
        closed = true;
        if (rollback) {
            for (int i = undos.size() - 1; i >= 0; i--) {
                try { undos.get(i).run(); }
                catch (RuntimeException ignored) { /* best-effort */ }
            }
        }
    }
}
```

**Step 4: Run test, expect green.**

> **API correction from Batch C1**: `org.argouml.kernel.UndoManager.endInteraction()` does not exist; the interaction boundary is implicit at the next `startInteraction` call (see `DefaultUndoManager#addCommand:144-154`). `OpExecutor.apply()` exhibits this same pattern — it only calls `startInteraction`. The compensating Runnables registered via `recordUndo` are the real undo mechanism; the `org.argouml.kernel.UndoManager` only provides the project-level grouping label. The `org.tigris.gef.undo.UndoManager` (a different class) has explicit chain start/end, and `OpExecutor` also uses that one via `getInstance().startChain()`.

---

## Task 8: Add `DiagramService` interface and `DiagramServiceRegistry` to `application.common`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/application/common/DiagramService.java`
- Create: `src/argouml-ai/src/org/argouml/ai/application/common/DiagramServiceRegistry.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/application/common/TestDiagramServiceRegistry.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.application.common;

import junit.framework.TestCase;
import org.argouml.ai.domain.common.ModelKind;

public class TestDiagramServiceRegistry extends TestCase {
    public void testRegisterAndLookup() {
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        DiagramService fake = new DiagramService() {
            public ModelKind kind() { return ModelKind.CLASS; } };
        r.register(ModelKind.CLASS, fake);
        assertSame(fake, r.forKind(ModelKind.CLASS).get());
    }

    public void testForKindMissingReturnsEmpty() {
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        assertFalse(r.forKind(ModelKind.CLASS).isPresent());
    }

    public void testReplaceWarns() {
        DiagramServiceRegistry r = new DiagramServiceRegistry();
        DiagramService a = new DiagramService() {
            public ModelKind kind() { return ModelKind.CLASS; } };
        DiagramService b = new DiagramService() {
            public ModelKind kind() { return ModelKind.CLASS; } };
        r.register(ModelKind.CLASS, a);
        r.register(ModelKind.CLASS, b);
        assertSame(b, r.forKind(ModelKind.CLASS).get());
    }
}
```

**Step 2: Run test (fails).**

**Step 3: Implement `DiagramService`**

```java
package org.argouml.ai.application.common;

import org.argouml.ai.domain.common.ModelKind;

/**
 * Per-kind facade over the model layer. MVP ships only
 * {@link org.argouml.ai.application.classdiagram.ClassDiagramService}
 * implementing this; the registry lets future kinds slot in.
 */
public interface DiagramService {
    ModelKind kind();
}
```

**Step 4: Implement `DiagramServiceRegistry`**

```java
package org.argouml.ai.application.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.argouml.ai.domain.common.ModelKind;

/**
 * Lookup table: {@link ModelKind} \u2192 {@link DiagramService}.
 *
 * <p>Thread-safe via the internal map. Handlers and the AI
 * {@code OpExecutor} both go through this; no direct dependency on
 * concrete service classes outside of register-time.</p>
 */
public final class DiagramServiceRegistry {
    private final Map<ModelKind, DiagramService> services =
        new HashMap<ModelKind, DiagramService>();

    public synchronized void register(ModelKind kind, DiagramService svc) {
        if (services.containsKey(kind)) {
            System.err.println("[argouml.ai] replacing service for "
                + kind.wireValue());
        }
        services.put(kind, svc);
    }

    public synchronized Optional<DiagramService> forKind(ModelKind kind) {
        return Optional.ofNullable(services.get(kind));
    }

    public synchronized boolean isRegistered(ModelKind kind) {
        return services.containsKey(kind);
    }
}
```

**Step 5: Run test, expect green.**

---

## Task 9: Add `ModelGateway` to `infrastructure.model`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/model/ModelGateway.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/infrastructure/model/TestModelGateway.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.infrastructure.model;

import junit.framework.TestCase;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;
import org.argouml.model.Model;

public class TestModelGateway extends TestCase {
    public void setUp() { InitializeModel.initializeDefault(); }

    public void testBuildClassAndLookup() {
        Project p = ProjectManager.getManager().getCurrentProject();
        Object ns = p.getModel();
        Object c = ModelGateway.buildClass("GW", ns);
        assertNotNull(c);
        assertSame(c, ModelGateway.findClassByName("GW", ns));
    }
}
```

**Step 2: Run test (fails).**

**Step 3: Implement**

```java
package org.argouml.ai.infrastructure.model;

import org.argouml.model.CoreFactory;
import org.argouml.model.Facade;
import org.argouml.model.Model;

/**
 * Thin static facade over ArgoUML Model APIs. Centralizes which
 * subsystem touches the model so future swaps (alternative UMl
 * implementations, test fakes) are localized.
 *
 * <p>All methods assume being called on the Swing EDT.</p>
 */
public final class ModelGateway {
    private ModelGateway() {}

    public static CoreFactory coreFactory() { return Model.getCoreFactory(); }
    public static Facade facade() { return Model.getFacade(); }

    public static Object buildClass(String name, Object ns) {
        return coreFactory().buildClass(name, ns);
    }

    public static Object buildInterface(String name, Object ns) {
        return coreFactory().buildInterface(name, ns);
    }

    public static Object buildDataType(String name, Object ns) {
        return coreFactory().buildDataType(name, ns);
    }

    public static Object findClassByName(String name, Object ns) {
        Facade f = facade();
        for (Object c : f.getOwnedElements(ns)) {
            if (f.isAClass(c) && name.equals(f.getName(c))) return c;
        }
        return null;
    }
}
```

**Step 4: Run test, expect green.**

---

## Task 10: Add `UndoAdapter` to `infrastructure.undo`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/undo/UndoAdapter.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/infrastructure/undo/TestUndoAdapter.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.infrastructure.undo;

import junit.framework.TestCase;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.InitializeModel;

public class TestUndoAdapter extends TestCase {
    public void setUp() { InitializeModel.initializeDefault(); }

    public void testStartAndEnd() {
        Project p = ProjectManager.getManager().getCurrentProject();
        assertNotNull(p.getUndoManager());
        UndoAdapter.begin("noop");
        UndoAdapter.end();   // must not throw
    }
}
```

**Step 2: Run test (fails).**

**Step 3: Implement**

```java
package org.argouml.ai.infrastructure.undo;

import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;

/**
 * Thin wrapper around {@code org.argouml.kernel.UndoManager} that
 * gracefully no-ops when there is no current project.
 */
public final class UndoAdapter {
    private UndoAdapter() {}

    public static void begin(String label) {
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p != null && p.getUndoManager() != null) {
            p.getUndoManager().startInteraction(label);
        }
    }

    public static void end() {
        Project p = ProjectManager.getManager().getCurrentProject();
        if (p != null && p.getUndoManager() != null) {
            p.getUndoManager().endInteraction();
        }
    }
}
```

**Step 4: Run test, expect green.**

---

## Task 11: Add `EdtDispatcher` to `infrastructure.thread`

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/thread/EdtDispatcher.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/infrastructure/thread/TestEdtDispatcher.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.infrastructure.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import junit.framework.TestCase;

public class TestEdtDispatcher extends TestCase {
    public void testRunsOnEdtFromWorkerThread() throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            // we're already on EDT; skip the actual cross-thread check
            return;
        }
        final AtomicReference<Boolean> onEdt = new AtomicReference<Boolean>();
        Boolean result = EdtDispatcher.toEdt(new Callable<Boolean>() {
            public Boolean call() {
                onEdt.set(SwingUtilities.isEventDispatchThread());
                return Boolean.TRUE;
            }
        });
        assertNotNull(result);
        assertTrue("task should run on EDT", onEdt.get());
    }

    public void testReentrantOk() throws Exception {
        EdtDispatcher.toEdt(new Callable<Object>() {
            public Object call() { return null; }
        });
    }
}
```

**Step 2: Run test (fails).**

**Step 3: Implement**

```java
package org.argouml.ai.infrastructure.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

/**
 * Single-direction dispatcher from worker threads (NanoHTTPD) onto
 * the Swing EDT.
 *
 * <p>{@link #toEdt(Callable)} blocks until the task completes on the
 * EDT, propagating any exception. If already on the EDT, runs
 * inline (no deadlock).</p>
 */
public final class EdtDispatcher {
    private EdtDispatcher() {}

    public static <T> T toEdt(Callable<T> task) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.call();
        }
        FutureTask<T> ft = new FutureTask<T>(task);
        SwingUtilities.invokeAndWait(ft);
        return ft.get();
    }
}
```

**Step 4: Run test, expect green.**

---

## Task 12: JSON envelope (`JsonWriter`, `JsonError`, `JsonBodyReader`)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/json/JsonWriter.java`
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/json/JsonError.java`
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/json/JsonBodyReader.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/infrastructure/json/TestJsonEnvelope.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.infrastructure.json;

import junit.framework.TestCase;
import java.util.LinkedHashMap;
import java.util.Map;

public class TestJsonEnvelope extends TestCase {
    public void testOkEnvelope() {
        Map<String,Object> data = new LinkedHashMap<String,Object>();
        data.put("x", 1);
        String s = JsonWriter.ok(data);
        assertTrue(s, s.contains("\"ok\":true"));
        assertTrue(s, s.contains("\"data\":{"));
        assertTrue(s, s.contains("\"x\":1"));
    }

    public void testErrorEnvelope() {
        String s = JsonError.of("BAD_NAME", "bad");
        assertTrue(s, s.contains("\"ok\":false"));
        assertTrue(s, s.contains("\"code\":\"BAD_NAME\""));
        assertTrue(s, s.contains("\"message\":\"bad\""));
    }

    public void testBodyReaderBasicShape() throws Exception {
        String body = "{\"name\":\"Foo\",\"x\":1,\"y\":2}";
        Map m = JsonBodyReader.readMap(body);
        assertEquals("Foo", m.get("name"));
        assertEquals(1, m.get("x"));
    }
}
```

**Step 2: Run test (fails).**

**Step 3: Implement `JsonWriter`**

```java
package org.argouml.ai.infrastructure.json;

import java.util.Map;
import org.argouml.ai.ops.JsonMini;

/**
 * Serializes the standard {@code {"ok":true,"data":...}} envelope.
 */
public final class JsonWriter {
    private JsonWriter() {}

    public static String ok(Object data) {
        return JsonMini.toJson(new Object[] {"ok", true, "data", data});
    }

    public static <T> String okKv(Map<String, T> kv) {
        return JsonMini.toJson(kv);
    }
}
```

**Step 4: Implement `JsonError`**

```java
package org.argouml.ai.infrastructure.json;

import org.argouml.ai.ops.JsonMini;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JsonError {
    private JsonError() {}

    public static String of(String code, String message) {
        Map<String,Object> err = new LinkedHashMap<String,Object>();
        err.put("code", code);
        err.put("message", message);
        return JsonMini.toJson(new Object[] {"ok", false, "error", err});
    }
}
```

**Step 5: Implement `JsonBodyReader`**

```java
package org.argouml.ai.infrastructure.json;

import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import org.argouml.ai.ops.JsonMini;

public final class JsonBodyReader {
    private JsonBodyReader() {}

    public static Map<String,Object> readMap(String body) {
        if (body == null || body.isEmpty()) return new LinkedHashMap<String,Object>();
        Reader r = new StringReader(body);
        Object o = JsonMini.parseOne(r);
        if (o == null) return new LinkedHashMap<String,Object>();
        if (!(o instanceof Map)) {
            throw new IllegalArgumentException("body is not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String,Object> m = (Map<String,Object>) o;
        return m;
    }
}
```

**Step 6: Run test, expect green.**

---

## Task 13: `ServerConfig` + `ServerConfigStore` (configuration persistence)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/config/ServerConfig.java`
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/config/ServerConfigStore.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/infrastructure/config/TestServerConfigStore.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.infrastructure.config;

import java.io.File;
import junit.framework.TestCase;

public class TestServerConfigStore extends TestCase {
    public void testDefaults() throws Exception {
        File tmp = File.createTempFile("argouml-http", ".properties");
        tmp.delete();
        ServerConfigStore s = new ServerConfigStore(tmp);
        ServerConfig c = s.load();
        assertTrue(c.enabled);
        assertEquals(8766, c.port);
        assertEquals("127.0.0.1", c.bind);
    }
    public void testPersistRoundTrip() throws Exception {
        File tmp = File.createTempFile("argouml-http", ".properties");
        tmp.deleteOnExit();
        ServerConfigStore s = new ServerConfigStore(tmp);
        ServerConfig c = s.load();
        c.port = 9999;
        s.save(c);
        ServerConfig d = s.load();
        assertEquals(9999, d.port);
    }
}
```

**Step 2: Run test (fails).**

**Step 3: Implement `ServerConfig`**

```java
package org.argouml.ai.infrastructure.config;

public class ServerConfig {
    public boolean enabled = true;
    public int port = 8766;
    public String bind = "127.0.0.1";
    public int timeoutSec = 30;
    public int maxBodyBytes = 1024 * 1024;
}
```

**Step 4: Implement `ServerConfigStore`**

```java
package org.argouml.ai.infrastructure.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;

public class ServerConfigStore {
    private final File file;
    public ServerConfigStore(File f) { this.file = f; }

    public ServerConfig load() {
        ServerConfig c = new ServerConfig();
        if (!file.exists()) return c;
        Properties p = new Properties();
        try {
            FileReader r = new FileReader(file);
            try { p.load(r); } finally { r.close(); }
        } catch (Exception ex) {
            return c; // unreadable -> defaults
        }
        c.enabled = Boolean.parseBoolean(p.getProperty("http.enabled", "true"));
        try {
            c.port = Integer.parseInt(p.getProperty("http.port", "8766"));
        } catch (NumberFormatException ignored) {}
        c.bind = p.getProperty("http.bind", "127.0.0.1");
        try {
            c.timeoutSec = Integer.parseInt(
                p.getProperty("http.timeoutSec", "30"));
        } catch (NumberFormatException ignored) {}
        try {
            c.maxBodyBytes = Integer.parseInt(
                p.getProperty("http.maxBodyBytes", "1048576"));
        } catch (NumberFormatException ignored) {}
        return c;
    }

    public void save(ServerConfig c) throws java.io.IOException {
        Properties p = new Properties();
        p.setProperty("http.enabled", String.valueOf(c.enabled));
        p.setProperty("http.port", String.valueOf(c.port));
        p.setProperty("http.bind", c.bind);
        p.setProperty("http.timeoutSec", String.valueOf(c.timeoutSec));
        p.setProperty("http.maxBodyBytes", String.valueOf(c.maxBodyBytes));
        FileWriter w = new FileWriter(file);
        try { p.store(w, "ArgoUML HTTP Server"); } finally { w.close(); }
    }

    public static File defaultFile() {
        File home = new File(System.getProperty("user.home"));
        File dir = new File(home, ".argouml");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "http-server.properties");
    }
}
```

**Step 5: Run test, expect green.**

---

## Task 14: `NanoHttpAdapter` (server lifecycle)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/http/NanoHttpAdapter.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/infrastructure/http/TestNanoHttpAdapter.java`

**Step 1: Write the failing test**

```java
package org.argouml.ai.infrastructure.http;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import junit.framework.TestCase;

public class TestNanoHttpAdapter extends TestCase {
    public void testStartAndStop() throws Exception {
        NanoHttpAdapter a = new NanoHttpAdapter("127.0.0.1", 0, new NanoHTTPD(null, 0) {
            @Override public Response serve(IHTTPSession session) {
                return newFixedLengthResponse("pong");
            }
        });
        a.start();
        try {
            URL u = new URL("http://127.0.0.1:" + a.boundPort() + "/");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            assertEquals(200, c.getResponseCode());
        } finally {
            a.stop();
        }
    }
}
```

**Step 2: Run test (fails).**

**Step 3: Implement**

```java
package org.argouml.ai.infrastructure.http;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;

public class NanoHttpAdapter {
    private final NanoHTTPD server;
    private String boundHost;
    private int boundPort;

    public NanoHttpAdapter(String host, int port, NanoHTTPD server) {
        this.server = server;
        // NanoHTTPD picks host/port at start(); store expected for later.
        this.boundHost = host;
    }

    public void start() {
        try {
            server.start(5000); // 5s read timeout per request
        } catch (IOException ex) {
            throw new RuntimeException("failed to start HTTP server", ex);
        }
    }

    public void stop() {
        server.stop();
    }

    public int boundPort() {
        // NanoHTTPD exposes listening port via ServerSocket; fall back to stored.
        return server.getListeningPort();
    }
}
```

> The NanoHTTPD API surface varies slightly across 2.x releases; if `start()` signature differs (some releases take `(int timeoutMs)` as second positional), match the actual JAR present in `~/.m2`. Adjust the test accordingly.

**Step 4: Run test, expect green.**

---

## Task 15: Domain `ClassOperations` (build/delete/rename/find class)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/domain/classdiagram/ClassOperations.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/domain/classdiagram/TestClassOperations.java`

**Step 1: Write failing test** — verify `build`, `findByName`, `delete` on a real `UMLClassDiagram`. Set up project + class diagram in `setUp()` mirroring `argouml-ai/tests/.../TestProjectSnapshot.java`:

```java
public void setUp() throws Exception {
    InitializeModel.initializeDefault();
    new InitProfileSubsystem().init();
    Project p = ProjectManager.getManager().makeEmptyProject();
    Object ns = p.getModel();
    ArgoDiagram d = new UMLClassDiagram("MyClassDiagram", ns);
    p.addDiagram(d);
}
```

(API corrections from prior tasks: `addMemberDiagram(name, diagram)` does NOT exist in this codebase; use `new UMLClassDiagram(name, ns)` then `p.addDiagram(d)`. Likewise `p.getDiagramList()` returns `List<ArgoDiagram>`; `p.getDiagrams()` doesn't exist.)

**Step 2: Run test (fails).**

**Step 3: Implement** a static utility class with `buildClass`, `findByName(diagram, name)`, `deleteClass(diagram, cls)`, `renameClass(cls, newName)`, `setAbstract`, `addStereotype`. Mirror the corresponding snippets already used inside the existing `OpExecutor.applyAddClass` (refactor that code into here).

**Step 4: Run test, expect green.** Optionally also re-run `argouml-ai` existing tests to confirm `OpExecutor` is still consistent (it isn't refactored yet — that comes in Task 19).

---

## Task 16: Domain `InterfaceOperations`, `AttributeOperations`, `OperationOperations`, `RelationshipOperations`

**Files:**
- Create: 4 ops classes + 4 test classes under `domain/classdiagram/`

Repeat the Task 15 pattern: failing test, run, impl, run, repeat for each of:
- `InterfaceOperations` (`buildInterface`, `findByName`, `deleteInterface`)
- `AttributeOperations` (`buildAttribute`, `deleteAttribute`, `findByName`)
- `OperationOperations` (`buildOperation`, `deleteOperation`, `findBySignature`)
- `RelationshipOperations` (`buildAssociation`, `buildGeneralization`, `buildDependency`, `deleteRelationship`, `setMultiplicity`, `setRoleName`)

**Implementation contract**: each method takes the diagram/owner object as the first parameter, returns either the created/affected model element or `void`; throws `IllegalArgumentException` with a meaningful message on invalid input.

For `RelationshipOperations.findAssociation`, the lookup walks `MutableGraphModel.getEdges()` and matches both endpoints; this is reused by the delete handler for `DELETE /d/{d}/relationships/{id}`.

Each `*Operations` class is **self-contained** — only `import org.argouml.model.*` and `domain.common.*`. No inbound or application dependencies.

---

## Task 17: `ClassDiagramService` (application layer; orchestrates domain ops with Undo)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/application/classdiagram/ClassDiagramService.java`
- Test: `src/argouml-ai/tests/org/argouml/ai/application/classdiagram/TestClassDiagramService.java`

**Step 1: Write failing test** — verify the service exposes `kind() = ModelKind.CLASS`, and each of the six CRUD areas (classes, interfaces, attributes, operations, relationships, undo rollback) does the right thing. For example:

```java
public void testCreateClassAndAddAttrsIsOneTransaction() {
    ClassDiagramService svc = new ClassDiagramService();
    ClassCreateRequest r = new ClassCreateRequest("Order", 100, 100, null, false);
    svc.createClass("MyClassDiagram", r);
    svc.addAttribute("MyClassDiagram", "Order",
        new AttributeCreateRequest("id", "long", "private"));
    // rollback should remove both
    Object ns = currentProject().getModel();
    svc.rollbackLastOp(); // (or use try-with-resources pattern)
    assertNull(Model.getFacade().lookup("Order", ns));
}
```

If the test pattern is awkward, the next design simplification is to make each public method on the service accept a `Consumer<UndoScope>` or to wrap its body in `UndoScope.open(label)`. Concrete shape is up to the implementer; the test should cover happy path + rollback.

**Step 2: Run test (fails).**

**Step 3: Implement** as a class implementing `DiagramService`:
- `public ModelKind kind() { return ModelKind.CLASS; }`
- Methods like `createClass(String d, CreateRequest body)`, `updateClass`, `deleteClass`, `listClasses`, `getClass(name)`, plus analogous for interfaces, attributes, operations, relationships.
- Every mutating method opens `UndoScope.open("...")`, calls the corresponding `domain.classdiagram.*Operations` method, registers compensating Runnables on the scope, and the scope commits on close.
- Each method's return value is a small POJO like `ClassHandle { String name; Object element; }` or `void`.
- Read methods do not open an `UndoScope`.

**Step 4: Run test, expect green.** Add a second test for the rollback case to lock the contract.

---

## Task 18: Wire `ClassDiagramService` into a registry singleton

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/application/common/DiagramServices.java` (small bootstrap helper that creates the registry and registers `ClassDiagramService`)

**Step 1: Implement**

```java
package org.argouml.ai.application.common;

import org.argouml.ai.application.classdiagram.ClassDiagramService;

public final class DiagramServices {
    private static final DiagramServiceRegistry REG = new DiagramServiceRegistry();
    static {
        REG.register(org.argouml.ai.domain.common.ModelKind.CLASS,
            new ClassDiagramService());
    }
    private DiagramServices() {}
    public static DiagramServiceRegistry registry() { return REG; }
}
```

(Note: extends Task 8's test by verifying `DiagramServices.registry().forKind(CLASS).isPresent()`.)

**Step 2: Run all `application.common` tests, expect green.**

---

## Task 19: Refactor `OpExecutor` to delegate to `ClassDiagramService`

**Files:**
- Modify: `src/argouml-ai/src/org/argouml/ai/inbound/ai/classdiagram/OpExecutor.java`

**Step 1: Verify all existing AI tests still pass before refactor**

Run: `mvn -f src/argouml-ai/pom.xml test -o`
Expected: green.

**Step 2: Refactor each `applyAddXxx` body** to delegate to `ClassDiagramService`. The public API (`apply(List<PlannedOp>)`, `apply(List, IntConsumer, BiConsumer)`) and inner behavior (AiBatchMemento registration) MUST be unchanged. The delegate calls look like:

```java
private void applyAddClass(PlannedOp op,
                           List<Runnable> undos, List<Runnable> redos) {
    ClassDiagramService svc = (ClassDiagramService)
        DiagramServices.registry().forKind(ModelKind.CLASS).get();
    CreateRequest req = new CreateRequest();
    req.name = op.getString("name");
    req.x = op.getInt("x");
    req.y = op.getInt("y");
    req.stereotype = op.getString("stereotype");
    req.isAbstract = op.getBoolean("isAbstract", false);
    // Capture whatever the service does; reuse its undo wiring by,
    // for example, asking the service to return the created element
    // and push a synthetic undo/redo runnable. (Delete-class still
    // a deliberate gap; see existing @todo in OpExecutor.)
    svc.createClass(diagram.getName(), req);
}
```

**Goal**: keep the existing `AiBatchMemento`/`startChain` plumbing intact; only replace the body of each `applyXxx`.

**Step 3: Run AI tests**

Run: `mvn -f src/argouml-ai/pom.xml test -o`
Expected: green. In particular, every existing `TestOpExecutor` test continues to pass without code changes.

---

## Task 20: `Router` + `PathMatcher` + `Dispatcher` (REST common)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/inbound/rest/common/PathMatcher.java`
- Create: `src/argouml-ai/src/org/argouml/ai/inbound/rest/common/Router.java`
- Create: `src/argouml-ai/src/org/argouml/ai/inbound/rest/common/Dispatcher.java`
- Test: 3 test classes under `tests/.../inbound/rest/common/`

**Implementation notes**:
- `PathMatcher` takes a template like `/d/{d}/classes/{c}` and a real path; returns either `null` or a `Map<String,String>` of captured segments. Test with multiple templates returning the right map.
- `Router` holds `Map<Method, List<Route>>` where each `Route` is `(PathMatcher, HandlerFactory)`. Lookup returns the first matching route or 404.
- `Dispatcher` is the single `NanoHTTPD.IHandler` registered with NanoHTTPD; it dispatches the matched route to the handler, wraps it in `EdtDispatcher.toEdt(...)`, and translates exceptions into HTTP responses (DiagramServiceException → 4xx/501; Throwable → 500).

Write failing tests covering: method mismatch → 405, path mismatch → 404, success path returns 200, DiagramServiceException maps to its `httpStatus()`.

---

## Task 21: REST common handlers (`HealthHandler`, `ListDiagramsHandler`, `GetDiagramHandler`, `SnapshotHandler`)

**Files:**
- Create: 4 handlers in `src/argouml-ai/src/org/argouml/ai/inbound/rest/common/handlers/`
- Test: 4 test classes in `tests/.../inbound/rest/common/handlers/`

Each handler:

- **`HealthHandler`** — returns `{version,enabled,port,currentProject}`. Reads `ServerConfig` via the `HttpServerSettingsTab`'s `currentConfig()` (or a small accessor).
- **`ListDiagramsHandler`** — walks `ProjectManager.getCurrentProject().getDiagrams()` and serializes each diagram's name + kind; returns `[]` if no project.
- **`GetDiagramHandler`** — single diagram by name; 404 if missing.
- **`SnapshotHandler`** — body = `JsonWriter.ok(ProjectSnapshot.snapshot(diagram).toJson())`. For non-class diagrams, returns 501 via `UnsupportedHandler`.

Each handler accepts a `DiagramServices.registry()` reference (constructor-injected) so tests can pass a fake.

---

## Task 22: REST `classdiagram` handlers — Classes & Interfaces (7 endpoints)

**Files:**
- Create: `inbound/rest/classdiagram/handlers/{ListClassesHandler, GetClassHandler, CreateClassHandler, UpdateClassHandler, DeleteClassHandler, CreateInterfaceHandler}.java`
- Test: 6 test classes in `tests/.../inbound/rest/classdiagram/handlers/`

Each handler is a thin wrapper:
```java
public Response handle(Session s, ...) {
  String body = readBody(s);
  CreateRequest req = parseCreateRequest(body);
  Result r = classDiagramService.createClass(diagramName, req);
  return JsonResponse.created(JsonWriter.ok(toView(r)));
}
```

`UpdateClassHandler` accepts partial body (only fields present get updated). All DELETE handlers return 204 with empty body. All POSTs that succeed return 201 + envelope.

Use the `JsonBodyReader` from Task 12 and `DiagramServiceException` for error mapping (each handler catches the typed exceptions, returns the appropriate HTTP status + `JsonError.of(code, msg)`).

---

## Task 23: REST `classdiagram` handlers — Attributes (4 endpoints)

**Files:**
- Create: `inbound/rest/classdiagram/handlers/attribute/{ListAttributesHandler, GetAttributeHandler, AddAttributeHandler, DeleteAttributeHandler}.java`
- Test: 4 test classes

Implement `visibility` validation as `public|protected|private|package` and lookup via `ClassOperations.findClassByName` + `AttributeOperations.findByName`.

---

## Task 24: REST `classdiagram` handlers — Operations (4 endpoints)

**Files:**
- Create: `inbound/rest/classdiagram/handlers/operation/{ListOperationsHandler, GetOperationHandler, AddOperationHandler, DeleteOperationHandler}.java`
- Test: 4 test classes

`{op}` in the URL is the operation signature encoded as `name(returnType)`; treat 404 for any non-match.

---

## Task 25: REST `classdiagram` handlers — Relationships (7 endpoints)

**Files:**
- Create: `inbound/rest/classdiagram/handlers/relationship/{ListAssociationsHandler, AddAssociationHandler, ListGeneralizationsHandler, AddGeneralizationHandler, ListDependenciesHandler, AddDependencyHandler, DeleteRelationshipHandler}.java` + `UnsupportedHandler.java`
- Test: 8 test classes

`DeleteRelationshipHandler` accepts `{id}` which is the association/generalization/dependency's id (we use the underlying JMI/MDR element id, exposed in responses); the handler looks up the relationship via `RelationshipOperations.findById` and calls `deleteRelationship`.

---

## Task 26: `InitHttpServerSubsystem` wiring

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/InitHttpServerSubsystem.java`
- Modify: `src/argouml-app/src/org/argouml/application/Main.java` — append one line after line 431

**Step 1: Implement `InitHttpServerSubsystem`**

```java
package org.argouml.ai;

import java.util.List;
import org.argouml.ai.application.common.DiagramServiceRegistry;
import org.argouml.ai.inbound.rest.common.Dispatcher;
import org.argouml.ai.inbound.rest.common.Router;
import org.argouml.ai.infrastructure.config.ServerConfig;
import org.argouml.ai.infrastructure.config.ServerConfigStore;
import org.argouml.ai.infrastructure.http.NanoHttpAdapter;
import org.argouml.application.api.AbstractArgoJPanel;
import org.argouml.application.api.GUISettingsTabInterface;
import org.argouml.application.api.InitSubsystem;
import fi.iki.elonen.NanoHTTPD;

public class InitHttpServerSubsystem implements InitSubsystem {
    private NanoHttpAdapter adapter;

    public void init() {
        ServerConfig cfg = new ServerConfigStore(
            ServerConfigStore.defaultFile()).load();
        if (!cfg.enabled) return;
        try {
            Dispatcher d = new Dispatcher(
                buildRouter(DiagramServiceRegistryBridge.current()));
            adapter = new NanoHttpAdapter(cfg.bind, cfg.port,
                new NanoHTTPD(cfg.bind, cfg.port) {
                    @Override public Response serve(IHTTPSession s) {
                        return d.handle(s);
                    }
                });
            adapter.start();
        } catch (Exception ex) {
            // log; do not crash app
            ex.printStackTrace();
        }
    }

    public List<AbstractArgoJPanel> getDetailsTabs() { return List.of(); }
    public List<GUISettingsTabInterface> getSettingsTabs() {
        return List.of(new org.argouml.ai.infrastructure.config.HttpServerSettingsTab());
    }
    public List<GUISettingsTabInterface> getProjectSettingsTabs() { return List.of(); }

    private Router buildRouter(DiagramServiceRegistry... registries) { /* … */ return null; }
}
```

(Implement `buildRouter` to register all handlers from Tasks 20–25 in the right order.)

**Step 2: Append to `Main.java:432`**

```
        SubsystemUtility.initSubsystem(new InitHttpServerSubsystem());
```

(insert immediately after line 431 which registers `InitAiSubsystem()`.)

**Step 3: Run argouml-app build**

Run: `mvn -pl src/argouml-app -am package -DskipTests -o`
Expected: `BUILD SUCCESS` for the full reactor including `argouml-ai`.

---

## Task 27: `HttpServerSettingsTab` (UI for port / enabled toggle)

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/infrastructure/config/HttpServerSettingsTab.java`

**Step 1: Implement** as a `JPanel` with two fields (`enabled` checkbox, `port` spinner) and a "Apply" button that writes `ServerConfig` via `ServerConfigStore.save(...)` and prompts the user to restart ArgoUML for changes to take effect. i18n keys:

- `http.settings.title`
- `http.settings.enable`
- `http.settings.port`
- `http.settings.bindhint` ("loopback only — change at your own risk")
- `http.settings.apply`

Add an `HttpServerSettingsTab.properties` ISO-8859-1 file with the Chinese translations.

**Step 2: Run build**

Run: `mvn -pl src/argouml-app -am package -DskipTests -o`
Expected: green.

---

## Task 28: End-to-end smoke test (manual)

**Step 1: Start argouml with the new module**

Run: see the run command in the reference header; add `-Dargouml.modules=org.argouml.dev.DeveloperModule` if Dev tools are needed.

**Step 2: Verify HTTP server is reachable**

```bash
curl -s http://127.0.0.1:8766/health
```
Expected:
```json
{"ok":true,"data":{"version":"0.35.2-SNAPSHOT","enabled":true,"port":8766,"currentProject":null}}
```

**Step 3: Open a project** (via File → Open; pick a sample `.zargo`)

**Step 4: Verify list, get, create, delete**

```bash
curl -s http://127.0.0.1:8766/project/diagrams
curl -s "http://127.0.0.1:8766/project/diagrams/<name>/classes"
curl -X POST "http://127.0.0.1:8766/d/<name>/classes" \
  -H "Content-Type: application/json" \
  -d '{"name":"HttpOrder","x":200,"y":120,"stereotype":"entity"}'
curl -X POST "http://127.0.0.1:8766/d/<name>/classes/HttpOrder/attributes" \
  -H "Content-Type: application/json" \
  -d '{"name":"id","type":"long","visibility":"private"}'
curl -X DELETE "http://127.0.0.1:8766/d/<name>/classes/HttpOrder"
```

Expected:
- list returns array with the seeded diagram name
- classes lists all original classes
- POST returns 201 + envelope
- attribute add returns 201
- delete returns 204

**Step 5: Verify 404 / 400 / 501 paths**

```bash
curl -i http://127.0.0.1:8766/d/no-such-diagram/classes
curl -i -X POST http://127.0.0.1:8766/d/<name>/classes \
  -H "Content-Type: application/json" -d '{}'
curl -i -X POST http://127.0.0.1:8766/d/<use-case-diagram>/usecases -d '{}'
```

Expected: 404, 400, 501 respectively, each with `{"ok":false,"error":{"code":"...","message":"..."}}`.

**Step 6: Verify Ctrl+Z undoes the latest batch**

After Step 4's POSTs + DELETE, pressing Ctrl+Z in ArgoUML must revert the most recent change.

**Step 7: Write a Task-28 smoke test doc** at `docs/plans/2026-06-30-http-server-smoke-test.md` summarizing what passed and the response captures. Save without committing.

---

## Task 29: Update `argouml-ai/README.md`

**Files:**
- Modify: `src/argouml-ai/README.md`

Append a section "HTTP Server" explaining:
- Default endpoint, port, loopback-only
- How to disable (Settings → HTTP Server)
- One `curl` example hitting `/health`
- Link to `docs/plans/2026-06-30-http-server-design.md`

---

## Done

End state:
- 4 layer module produces a packaged jar with all handlers wired
- `OpExecutor` AI path still green (existing tests still pass)
- HTTP server running on loopback:8766 by default
- Manual smoke test confirms CRUD works
- README + design doc + plan + smoke-test-doc all in `docs/plans/`

Out of scope (future PRs): use case / sequence / activity / state / deployment diagram support, Bearer token auth, SSE events, bulk operations endpoint.
