# UseCase ↔ 多图关联升级 — 实施计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 升级 `UseCase.representedDiagram` 从 1:1 到 1:N 关联（含属性面板表格、包树管理对话框、右键动态子菜单、∞ 视觉符号、REST API 多端点）。

**Architecture:** 共享缓存 `RepresentedDiagramLinkCache` 的 value 从 `String` 改为 `List<String>`；model tagged value 用同一 tag 名多 value（`dataValues: String[]`）；UI 层用 JTable + JTree 包树对话框 + ArgoJMenu 动态子菜单 + FigUseCase 添加 ∞ FigText 子组件。

**Tech Stack:** 纯 Java + ArgoUML Swing 框架；JUnit 3 测试惯例；无 TDD（GUI 任务以手动验证为主，单元测试覆盖 service / domain 层）。

---

### Task 1: 升级 RepresentedDiagramLinkCache 为 List<String>

**Files:**
- Modify: `src/argouml-core-model/src/org/argouml/model/RepresentedDiagramLinkCache.java`

**Step 1: 把 CACHE 改为 Map<Object, List<String>> + 新增方法**

打开 `RepresentedDiagramLinkCache.java`，修改：

- `Map<Object, String>` → `Map<Object, List<String>>`
- `get(Object)` 返回 `List<String>`（空 list = "" 表示无关联）
- `put(Object, Collection<String>)` 整体替换
- 新增 `addUuid(Object, String)`（幂等，自动去重）
- 新增 `removeUuid(Object, String)`（若不存在则 noop，返回 boolean）
- 新增 `getAll(Object): List<String>`（miss 则返回空列表）
- 新增 `clear(Object)`

完整文件：

```java
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * In-memory cache for the {@code UseCase.representedDiagram} link
 * (now 1:N — a UseCase may link to multiple ArgoDiagrams).
 *
 * <p>See previous design notes — MDR {@code setType(handle, String)}
 * rejects a String type argument, so the model's tagged-value
 * round-trip is best-effort. This cache is the authoritative
 * store for the link set across all consumers (property panel,
 * REST handlers, right-click navigation menu).</p>
 *
 * @author mkl
 */
public final class RepresentedDiagramLinkCache {

    private static final Map<Object, List<String>> CACHE =
            new WeakHashMap<Object, List<String>>();

    private RepresentedDiagramLinkCache() {}

    /** Returns the full link list (immutable copy). Empty list = no links. */
    public static List<String> getAll(Object useCase) {
        if (useCase == null) {
            return Collections.emptyList();
        }
        List<String> list = CACHE.get(useCase);
        if (list == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(list));
    }

    /** Replace all links with {@code uuids}. */
    public static void put(Object useCase, Collection<String> uuids) {
        if (useCase == null) {
            return;
        }
        if (uuids == null || uuids.isEmpty()) {
            CACHE.remove(useCase);
        } else {
            CACHE.put(useCase, new ArrayList<String>(uuids));
        }
    }

    /** Append a single uuid (no-op if already present). */
    public static boolean addUuid(Object useCase, String uuid) {
        if (useCase == null || uuid == null || uuid.isEmpty()) {
            return false;
        }
        List<String> current = CACHE.get(useCase);
        if (current == null) {
            current = new ArrayList<String>();
            CACHE.put(useCase, current);
        }
        if (current.contains(uuid)) {
            return false;
        }
        current.add(uuid);
        return true;
    }

    /** Remove a single uuid. Returns true if removed. */
    public static boolean removeUuid(Object useCase, String uuid) {
        if (useCase == null || uuid == null) {
            return false;
        }
        List<String> current = CACHE.get(useCase);
        if (current == null) {
            return false;
        }
        boolean removed = current.remove(uuid);
        if (current.isEmpty()) {
            CACHE.remove(useCase);
        }
        return removed;
    }

    /** Clear all links for a UseCase. */
    public static void clear(Object useCase) {
        if (useCase == null) {
            return;
        }
        CACHE.remove(useCase);
    }
}
```

**Step 2: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-core-model/src/org/argouml/model/RepresentedDiagramLinkCache.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-core-model/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS。

**Step 3: install 到 ~/.m2**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-core-model/pom.xml install -DskipTests -o 2>&1 | tail -3
```
Expected: BUILD SUCCESS。

**Step 4: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-core-model/src/org/argouml/model/RepresentedDiagramLinkCache.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat: upgrade link cache to List<String> (1:N support)"
```

---

### Task 2: UseCaseOperations 升级为 add/remove/getAll

**Files:**
- Modify: `src/argouml-ai/src/org/argouml/ai/domain/usecasediagram/UseCaseOperations.java`

**Step 1: 改文件 — 完整重写**

```java
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.usecasediagram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.argouml.ai.domain.common.AbstractDiagramElementOperations;
import org.argouml.model.CoreHelper;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.model.RepresentedDiagramLinkCache;
import org.argouml.model.UseCasesFactory;
import org.argouml.uml.diagram.ArgoDiagram;

public final class UseCaseOperations
        extends AbstractDiagramElementOperations<Object> {

    @Override
    protected Object buildImpl(ArgoDiagram diagram, String name) {
        UseCasesFactory uf = Model.getUseCasesFactory();
        CoreHelper ch = Model.getCoreHelper();
        Object useCase = uf.createUseCase();
        ch.setName(useCase, name);
        ch.addOwnedElement(diagram.getNamespace(), useCase);
        return useCase;
    }

    public static Object build(ArgoDiagram diagram, String name, String description) {
        UseCaseOperations self = new UseCaseOperations();
        Object useCase = self.build(diagram, name);
        if (description != null && !description.isEmpty()) {
            setDescription(useCase, description);
        }
        return useCase;
    }

    @Override
    protected boolean isTargetType(Object node) {
        return Model.getFacade().isAUseCase(node);
    }

    public static void setDescription(Object useCase, String description) {
        if (useCase == null || description == null) return;
        try {
            org.argouml.model.ExtensionMechanismsFactory emf =
                    Model.getExtensionMechanismsFactory();
            org.argouml.model.ExtensionMechanismsHelper emh =
                    Model.getExtensionMechanismsHelper();
            Object tv = emf.createTaggedValue();
            emh.addTaggedValue(useCase, tv);
            emh.setType(tv, "documentation");
            emh.setDataValues(tv, new String[] {description});
        } catch (RuntimeException ignored) {}
    }

    public static String getDescription(Object useCase) {
        if (useCase == null) return "";
        try {
            Facade facade = Model.getFacade();
            Iterator tvs = facade.getTaggedValues(useCase);
            if (tvs == null) return "";
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if ("documentation".equals(facade.getName(tv))) {
                    Object v = facade.getValue(tv);
                    return v == null ? "" : v.toString();
                }
            }
        } catch (RuntimeException ignored) {}
        return "";
    }

    /**
     * Replace all links for a UseCase. Authoritative store is
     * {@link RepresentedDiagramLinkCache}; tagged-value write is
     * best-effort.
     */
    public static void setRepresentedDiagrams(Object useCase, Collection<String> diagramUuids) {
        if (useCase == null) return;
        List<String> normalized = diagramUuids == null
                ? Collections.<String>emptyList()
                : sanitizeUuids(diagramUuids);
        RepresentedDiagramLinkCache.put(useCase, normalized);
        writeModelTag(useCase, normalized);
    }

    public static boolean addRepresentedDiagram(Object useCase, String uuid) {
        if (useCase == null || uuid == null || uuid.isEmpty()) return false;
        List<String> current = new ArrayList<String>(getRepresentedDiagrams(useCase));
        if (current.contains(uuid)) return false;
        current.add(uuid);
        setRepresentedDiagrams(useCase, current);
        return true;
    }

    public static boolean removeRepresentedDiagram(Object useCase, String uuid) {
        if (useCase == null || uuid == null || uuid.isEmpty()) return false;
        List<String> current = new ArrayList<String>(getRepresentedDiagrams(useCase));
        boolean removed = current.remove(uuid);
        if (removed) setRepresentedDiagrams(useCase, current);
        return removed;
    }

    /**
     * Return immutable list of currently linked ArgoDiagram UUIDs.
     * Cache-first, model fallback.
     */
    public static List<String> getRepresentedDiagrams(Object useCase) {
        if (useCase == null) return Collections.emptyList();
        if (Model.getFacade().isAUseCase(useCase)) {
            // cache may be populated even for non-UseCase teardown paths
        }
        List<String> cached = RepresentedDiagramLinkCache.getAll(useCase);
        if (!cached.isEmpty()) return cached;
        List<String> fromTag = readModelTag(useCase);
        if (!fromTag.isEmpty()) {
            RepresentedDiagramLinkCache.put(useCase, fromTag);
        }
        return fromTag;
    }

    private static List<String> sanitizeUuids(Collection<String> input) {
        Set<String> seen = new LinkedHashSet<String>();
        for (String s : input) {
            if (s != null && !s.isEmpty()) seen.add(s);
        }
        return new ArrayList<String>(seen);
    }

    private static void writeModelTag(Object useCase, List<String> uuids) {
        try {
            org.argouml.model.ExtensionMechanismsFactory emf =
                    Model.getExtensionMechanismsFactory();
            org.argouml.model.ExtensionMechanismsHelper emh =
                    Model.getExtensionMechanismsHelper();
            Object tv = emf.createTaggedValue();
            emh.addTaggedValue(useCase, tv);
            emh.setType(tv, "representedDiagram");
            emh.setDataValues(tv, uuids.toArray(new String[0]));
        } catch (RuntimeException ignored) {}
    }

    private static List<String> readModelTag(Object useCase) {
        try {
            Facade facade = Model.getFacade();
            Iterator tvs = facade.getTaggedValues(useCase);
            if (tvs == null) return Collections.emptyList();
            while (tvs.hasNext()) {
                Object tv = tvs.next();
                if ("representedDiagram".equals(facade.getName(tv))) {
                    Object raw = facade.getDataValues(tv);
                    List<String> result = new ArrayList<String>();
                    if (raw instanceof String[]) {
                        for (String s : (String[]) raw) {
                            if (s != null && !s.isEmpty()) result.add(s);
                        }
                    } else if (raw instanceof Collection) {
                        for (Object o : (Collection) raw) {
                            if (o != null) result.add(String.valueOf(o));
                        }
                    }
                    return result;
                }
            }
        } catch (RuntimeException ignored) {}
        return Collections.emptyList();
    }

    public static Set<Object> list(ArgoDiagram diagram) {
        Set<Object> out = new LinkedHashSet<Object>();
        if (diagram == null) return out;
        Facade facade = Model.getFacade();
        for (Object node : diagram.getGraphModel().getNodes()) {
            if (facade.isAUseCase(node)) out.add(node);
        }
        return out;
    }

    public static Collection listAllInNamespace(ArgoDiagram diagram) {
        Object ns = diagram == null ? null : diagram.getNamespace();
        if (ns == null) return new java.util.ArrayList();
        return Model.getUseCasesHelper().getAllUseCases(ns);
    }
}
```

**Step 2: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/domain/usecasediagram/UseCaseOperations.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml -am compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS。

**Step 3: install**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml -am install -DskipTests -o 2>&1 | tail -3
```

**Step 4: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-ai/src/org/argouml/ai/domain/usecasediagram/UseCaseOperations.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat: UseCaseOperations 1:N link operations (add/remove/set/getAll)"
```

---

### Task 3: UsecaseUseCaseEntity 加 `representedDiagramUuids`

**Files:**
- Modify: `src/argouml-ai/src/org/argouml/ai/domain/entity/UsecaseUseCaseEntity.java`

**Step 1: 完整重写**

```java
/* $Id$
 *****************************************************************************
 * Copyright (c) 2024 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************
 */
package org.argouml.ai.domain.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class UsecaseUseCaseEntity implements ElementEntity {

    private final String uuid;
    private final String name;
    private final String description;
    private final List<String> representedDiagramUuids;
    private final String diagramUuid;
    private final int x;
    private final int y;

    public UsecaseUseCaseEntity(String uuid, String name, String description,
                         List<String> representedDiagramUuids,
                         String diagramUuid, int x, int y) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name;
        this.description = description == null ? "" : description;
        this.representedDiagramUuids = representedDiagramUuids == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<String>(representedDiagramUuids));
        this.diagramUuid = diagramUuid == null ? "" : diagramUuid;
        this.x = x;
        this.y = y;
    }

    @Override
    public String uuid() { return uuid; }

    @Override
    public String name() { return name; }

    @Override
    public String kind() { return "usecase"; }

    public String description() { return description; }

    public List<String> representedDiagramUuids() { return representedDiagramUuids; }

    @Override
    public String diagramUuid() { return diagramUuid; }

    @Override
    public int x() { return x; }

    @Override
    public int y() { return y; }
}
```

**Step 2: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/domain/entity/UsecaseUseCaseEntity.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: 失败（构造器签名变了，调用方还没改）。这是预期的。

**Step 3: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-ai/src/org/argouml/ai/domain/entity/UsecaseUseCaseEntity.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(entity): UsecaseUseCaseEntity.representedDiagramUuids (List)"
```

---

### Task 4: UseCaseDiagramService — 改构造调用 + 加单条端点

**Files:**
- Modify: `src/argouml-ai/src/org/argouml/ai/application/usecasediagram/UseCaseDiagramService.java`

**Step 1: 找出所有 `new UsecaseUseCaseEntity(...)` 调用**

```bash
grep -n "new UsecaseUseCaseEntity" /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/application/usecasediagram/UseCaseDiagramService.java
```
Expected: 5 处（createUseCase, listUseCases, getUseCaseByName, findUseCaseByUuid, setUseCasePosition）。每处都需要把 `"", diagramUuidOf(d), x, y` 替换成 `Collections.<String>emptyList(), diagramUuidOf(d), x, y`。

**Step 2: 各方法补 `representedDiagramUuids` 参数**

- `createUseCase` 内：`Collections.<String>emptyList()`, diagramUuidOf(d), x, y
- `listUseCases` 内：`readRepresentedDiagrams(node)` 替换原来 `"",`，位置：`readDescription(useCase), representedDiagramUuids,`
- `getUseCaseByName` 内：同上
- `findUseCaseByUuid` 内：同上
- `setUseCasePosition` 内：同上

**Step 3: 把 `setUseCaseRepresentedDiagram` / `getUseCaseRepresentedDiagram` 改造为 List 语义 + 新增 `add/remove/list`**

```java
    public UsecaseUseCaseEntity setUseCaseRepresentedDiagrams(
            String diagramName, String name, java.util.List<String> uuids) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByName(d, name);
        if (useCase == null) throw new NotFoundException(...);
        UseCaseOperations.setRepresentedDiagrams(useCase, uuids);
        return findUseCaseByUuid(diagramName, uuidOf(useCase));
    }

    public boolean addUseCaseRepresentedDiagram(
            String diagramName, String name, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByName(d, name);
        if (useCase == null) throw new NotFoundException(...);
        return UseCaseOperations.addRepresentedDiagram(useCase, uuid);
    }

    public boolean removeUseCaseRepresentedDiagram(
            String diagramName, String name, String uuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByName(d, name);
        if (useCase == null) throw new NotFoundException(...);
        return UseCaseOperations.removeRepresentedDiagram(useCase, uuid);
    }

    public java.util.List<String> listUseCaseRepresentedDiagrams(
            String diagramName, String useCaseUuid) {
        ArgoDiagram d = requireDiagram(diagramName);
        requireUseCaseDiagram(d);
        Object useCase = USE_CASE_OPS.findByUuid(d, useCaseUuid);
        if (useCase == null) throw new NotFoundException(...);
        return UseCaseOperations.getRepresentedDiagrams(useCase);
    }

    /** Legacy single-uuid getter — returns the first uuid or "". */
    public String getUseCaseRepresentedDiagram(
            String diagramName, String useCaseUuid) {
        java.util.List<String> all = listUseCaseRepresentedDiagrams(diagramName, useCaseUuid);
        return all.isEmpty() ? "" : all.get(0);
    }

    /** Legacy single-uuid setter — replaces all with [uuid]. */
    public UsecaseUseCaseEntity setUseCaseRepresentedDiagram(
            String diagramName, String name, String uuid) {
        return setUseCaseRepresentedDiagrams(diagramName, name,
                java.util.Collections.singletonList(uuid == null ? "" : uuid));
    }
```

**Step 4: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/application/usecasediagram/UseCaseDiagramService.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS。

**Step 5: install + Commit**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml -am install -DskipTests -o 2>&1 | tail -3
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-ai/src/org/argouml/ai/application/usecasediagram/UseCaseDiagramService.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(service): UseCaseDiagramService 1:N add/remove/list"
```

---

### Task 5: 新增 4 个 REST Handler（PUT 集合 / POST 单条 / DELETE 单条 / GET 列表）

**Files:**
- Create: `src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/SetUseCaseRepresentedDiagramsHandler.java`
- Create: `src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/AddUseCaseRepresentedDiagramHandler.java`
- Create: `src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/RemoveUseCaseRepresentedDiagramHandler.java`
- Create: `src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/ListUseCaseRepresentedDiagramsHandler.java`

**Step 1: SetUseCaseRepresentedDiagramsHandler**

```java
package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.domain.entity.UsecaseUseCaseEntity;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.EntityJson;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

public final class SetUseCaseRepresentedDiagramsHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public SetUseCaseRepresentedDiagramsHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String name = pathParams == null ? null : pathParams.get("u");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase name required"));
        }
        Map<String, Object> json;
        try { json = JsonBodyReader.readMap(body); }
        catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY", ex.getMessage()));
        }
        Object raw = json == null ? null : json.get("diagramUuids");
        List<String> uuids = new ArrayList<String>();
        if (raw instanceof List) {
            for (Object o : (List) raw) {
                if (o != null) uuids.add(String.valueOf(o));
            }
        } else if (raw != null) {
            uuids.add(String.valueOf(raw));
        }
        UsecaseUseCaseEntity v = svc.setUseCaseRepresentedDiagrams(diagram, name, uuids);
        return ResponseEnvelope.json(200, JsonWriter.ok(EntityJson.toMap(v)));
    }
}
```

**Step 2: AddUseCaseRepresentedDiagramHandler**

```java
package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonBodyReader;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

public final class AddUseCaseRepresentedDiagramHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public AddUseCaseRepresentedDiagramHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String name = pathParams == null ? null : pathParams.get("u");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase name required"));
        }
        Map<String, Object> json;
        try { json = JsonBodyReader.readMap(body); }
        catch (IllegalArgumentException ex) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY", ex.getMessage()));
        }
        Object o = json == null ? null : json.get("diagramUuid");
        if (o == null) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_BODY",
                    "diagramUuid required"));
        }
        boolean added = svc.addUseCaseRepresentedDiagram(diagram, name, String.valueOf(o));
        if (!added) {
            return ResponseEnvelope.json(409, JsonError.of("DUPLICATE",
                    "UUID already in link list"));
        }
        return ResponseEnvelope.json(200, JsonWriter.ok(null));
    }
}
```

**Step 3: RemoveUseCaseRepresentedDiagramHandler**

```java
package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;

public final class RemoveUseCaseRepresentedDiagramHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public RemoveUseCaseRepresentedDiagramHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String name = pathParams == null ? null : pathParams.get("u");
        String uuid = pathParams == null ? null : pathParams.get("uuid");
        if (name == null || name.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME", "UseCase name required"));
        }
        if (uuid == null || uuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME", "diagramUuid required"));
        }
        boolean removed = svc.removeUseCaseRepresentedDiagram(diagram, name, uuid);
        if (!removed) {
            return ResponseEnvelope.json(404, JsonError.of("UUID_NOT_FOUND",
                    "UUID not in link list"));
        }
        return ResponseEnvelope.json(204, "");
    }
}
```

**Step 4: ListUseCaseRepresentedDiagramsHandler**

```java
package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.argouml.ai.application.usecasediagram.UseCaseDiagramService;
import org.argouml.ai.inbound.rest.common.IRequestHandler;
import org.argouml.ai.inbound.rest.common.ResponseEnvelope;
import org.argouml.ai.infrastructure.json.JsonError;
import org.argouml.ai.infrastructure.json.JsonWriter;

public final class ListUseCaseRepresentedDiagramsHandler implements IRequestHandler {
    private final UseCaseDiagramService svc;
    public ListUseCaseRepresentedDiagramsHandler(UseCaseDiagramService svc) {
        if (svc == null) throw new IllegalArgumentException("svc");
        this.svc = svc;
    }

    @Override
    public ResponseEnvelope handle(Map<String, String> pathParams,
                                   Map<String, String> queryParams,
                                   String body) {
        String diagram = pathParams == null ? null : pathParams.get("d");
        String uuid = pathParams == null ? null : pathParams.get("uuid");
        if (uuid == null || uuid.isEmpty()) {
            return ResponseEnvelope.json(400, JsonError.of("INVALID_NAME",
                    "UseCase uuid required in path"));
        }
        List<String> uuids = svc.listUseCaseRepresentedDiagrams(diagram, uuid);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("diagramUuids", uuids);
        return ResponseEnvelope.json(200, JsonWriter.ok(out));
    }
}
```

**Step 5: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/*.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS。

**Step 6: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/SetUseCaseRepresentedDiagramsHandler.java \
        src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/AddUseCaseRepresentedDiagramHandler.java \
        src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/RemoveUseCaseRepresentedDiagramHandler.java \
        src/argouml-ai/src/org/argouml/ai/inbound/rest/usecasediagram/handlers/usecase/ListUseCaseRepresentedDiagramsHandler.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(rest): 1:N represented-diagram endpoints (set/add/remove/list)"
```

---

### Task 6: 注册 4 条新 REST 路由

**Files:**
- Modify: `src/argouml-ai/src/org/argouml/ai/InitHttpServerSubsystem.java`
- Modify: `src/argouml-ai/src/org/argouml/ai/tools/StandaloneHttpServer.java`

**Step 1: 在 `InitHttpServerSubsystem.java:301-318` 区域（UseCase CRUD 之后）加 4 条新路由**

```java
        // UseCase -> representedDiagram (1:N) endpoints
        router.add(Method.PUT,
                "/d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagrams",
                new SetUseCaseRepresentedDiagramsHandler(ucSvc));
        router.add(Method.GET,
                "/d/{d}/usecasediagram/usecases/{uuid}/representedDiagrams",
                new ListUseCaseRepresentedDiagramsHandler(ucSvc));
        router.add(Method.POST,
                "/d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagram",
                new AddUseCaseRepresentedDiagramHandler(ucSvc));
        router.add(Method.DELETE,
                "/d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagram/{uuid}",
                new RemoveUseCaseRepresentedDiagramHandler(ucSvc));
```

**Step 2: 加 4 个 import**

```java
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.SetUseCaseRepresentedDiagramsHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.AddUseCaseRepresentedDiagramHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.RemoveUseCaseRepresentedDiagramHandler;
import org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase.ListUseCaseRepresentedDiagramsHandler;
```

**Step 3: 同样改 `StandaloneHttpServer.java`（per AGENTS.md §8a）**

找到 UseCase CRUD 区域，同样 4 行 + 4 个 import。

**Step 4: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/InitHttpServerSubsystem.java \
      /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/tools/StandaloneHttpServer.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS。

**Step 5: install + Commit**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml -am install -DskipTests -o 2>&1 | tail -3
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-ai/src/org/argouml/ai/InitHttpServerSubsystem.java \
        src/argouml-ai/src/org/argouml/ai/tools/StandaloneHttpServer.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(router): register 4 new represented-diagram endpoints"
```

---

### Task 7: EntityJson 序列化 `representedDiagramUuids`

**Files:**
- Modify: `src/argouml-ai/src/org/argouml/ai/infrastructure/json/EntityJson.java`

**Step 1: 改 `toMap(UsecaseUseCaseEntity)`**

找到现有 `private static Map<String, Object> toMap(UsecaseUseCaseEntity e)` 函数，把 `m.put("representedDiagramUuid", e.representedDiagramUuid());` 改成：

```java
        m.put("representedDiagramUuids", e.representedDiagramUuids());
```

**Step 2: 编译 + 验证**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/src/org/argouml/ai/infrastructure/json/EntityJson.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```

**Step 3: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-ai/src/org/argouml/ai/infrastructure/json/EntityJson.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(json): UsecaseUseCaseEntity.representedDiagramUuids serialization"
```

---

### Task 8: ActionNavigateRepresentedDiagram 加 lookupAllRepresentedDiagrams

**Files:**
- Modify: `src/argouml-app/src/org/argouml/uml/ui/ActionNavigateRepresentedDiagram.java`

**Step 1: 替换 `lookupRepresentedDiagram(useCase)` 为 `lookupAllRepresentedDiagrams(...)`**

重写为：

```java
package org.argouml.uml.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.uml.diagram.ArgoDiagram;

public final class ActionNavigateRepresentedDiagram extends AbstractActionNavigate {
    private static final long serialVersionUID = 1L;

    public ActionNavigateRepresentedDiagram() {
        super("menu.popup.jump-to-represented-diagram", true);
    }

    @Override
    protected Object navigateTo(Object source) {
        List<ArgoDiagram> all = lookupAllRepresentedDiagrams(source);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Static: walk the model's ArgoDiagram list and return every
     * diagram matching any UUID in the cache. Order: insertion
     * order of the link list.
     */
    public static List<ArgoDiagram> lookupAllRepresentedDiagrams(Object useCase) {
        List<ArgoDiagram> result = new ArrayList<ArgoDiagram>();
        if (useCase == null || !Model.getFacade().isAUseCase(useCase)) {
            return result;
        }
        List<String> uuids = UseCaseOperations.getRepresentedDiagrams(useCase);
        if (uuids.isEmpty()) return result;
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project == null) return result;
        Facade facade = Model.getFacade();
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;
            if (matches(ad, uuids, facade)) {
                result.add(ad);
            }
        }
        return result;
    }

    /** Legacy single-diagram helper — returns the first match, or null. */
    public static ArgoDiagram lookupRepresentedDiagram(Object useCase) {
        List<ArgoDiagram> all = lookupAllRepresentedDiagrams(useCase);
        return all.isEmpty() ? null : all.get(0);
    }

    private static boolean matches(ArgoDiagram ad, List<String> uuids, Facade facade) {
        for (String uuid : uuids) {
            if (uuid.equals(ad.getName())) return true;
            Object ns = ad.getNamespace();
            if (ns != null && uuid.equals(facade.getUUID(ns))) return true;
        }
        return false;
    }
}
```

**Step 2: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/uml/ui/ActionNavigateRepresentedDiagram.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```
Expected: BUILD SUCCESS（需要 `argouml-ai` 已 install 到 ~/.m2 — Task 4 已经做了）。

**Step 3: install + Commit**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml install -DskipTests -o 2>&1 | tail -3
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-app/src/org/argouml/uml/ui/ActionNavigateRepresentedDiagram.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(action): ActionNavigateRepresentedDiagram.lookupAllRepresentedDiagrams"
```

---

### Task 9: 新建 ActionJumpToRepresentedDiagram + ActionManageRepresentedDiagrams

**Files:**
- Create: `src/argouml-app/src/org/argouml/uml/ui/ActionJumpToRepresentedDiagram.java`
- Create: `src/argouml-app/src/org/argouml/uml/ui/ActionManageRepresentedDiagrams.java`

**Step 1: ActionJumpToRepresentedDiagram.java**

```java
package org.argouml.uml.ui;

import javax.swing.Action;

import org.argouml.uml.diagram.ArgoDiagram;

public final class ActionJumpToRepresentedDiagram extends AbstractActionNavigate {
    private static final long serialVersionUID = 1L;

    private final ArgoDiagram diagram;
    private final Object useCase;

    public ActionJumpToRepresentedDiagram(Object useCase, ArgoDiagram diagram) {
        super(diagram.getName(), false);
        this.diagram = diagram;
        this.useCase = useCase;
        putValue(Action.NAME, diagram.getName());
    }

    @Override
    protected Object navigateTo(Object source) {
        return diagram;
    }
}
```

**Step 2: ActionManageRepresentedDiagrams.java**

```java
package org.argouml.uml.ui;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.argouml.ui.UseCaseManageRepresentedDiagramsDialog;

/**
 * Opens the multi-link management dialog for a UseCase.
 */
public final class ActionManageRepresentedDiagrams extends AbstractAction {
    private static final long serialVersionUID = 1L;
    private final Object useCase;

    public ActionManageRepresentedDiagrams(Object useCase) {
        super("Manage...");
        this.useCase = useCase;
        putValue(Action.NAME, "关联管理...");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new UseCaseManageRepresentedDiagramsDialog(useCase).setVisible(true);
    }
}
```

**Step 3: 编译**（应失败，因为 ManageDialog 还未建）

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/uml/ui/*.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml compile -DskipTests -o 2>&1 | grep ERROR | head -5
```
Expected: 编译错误（UseCaseManageRepresentedDiagramsDialog 不存在）——这正常，下一个 Task 修。

**Step 4: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-app/src/org/argouml/uml/ui/ActionJumpToRepresentedDiagram.java \
        src/argouml-app/src/org/argouml/uml/ui/ActionManageRepresentedDiagrams.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(actions): add jump-to + manage-represented-diagrams actions"
```

---

### Task 10: 新建 UseCaseManageRepresentedDiagramsDialog (包树 JTree + 多选 + Add/Remove)

**Files:**
- Create: `src/argouml-app/src/org/argouml/uml/ui/UseCaseManageRepresentedDiagramsDialog.java`

**Step 1: 完整实现（核心逻辑）**

```java
package org.argouml.ai.inbound.rest.usecasediagram.handlers.usecase;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.argouml.ai.domain.usecasediagram.UseCaseOperations;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.ui.DisplayTextTree;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.uml.ui.UMLTreeCellRenderer;

public final class UseCaseManageRepresentedDiagramsDialog extends JDialog {
    private static final long serialVersionUID = 1L;

    private final Object useCase;
    private final JTree tree;

    public UseCaseManageRepresentedDiagramsDialog(Object useCase) {
        super((Frame) null, "Manage Represented Diagrams", true);
        this.useCase = useCase;
        setLayout(new BorderLayout(8, 8));

        tree = buildTree();
        add(new JScrollPane(tree), BorderLayout.CENTER);

        add(buildButtonPanel(), BorderLayout.SOUTH);
        setSize(420, 480);
        setLocationRelativeTo(null);
    }

    private JTree buildTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Project");
        Project project = ProjectManager.getManager().getCurrentProject();
        if (project != null) {
            for (Object ns : project.getUserDefinedModelList()) {
                DefaultMutableTreeNode node = buildNamespaceNode(ns, project);
                if (node != null) root.add(node);
            }
        }
        JTree t = new DisplayTextTree(root);
        t.setCellRenderer(new UMLTreeCellRenderer());
        t.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        return t;
    }

    private DefaultMutableTreeNode buildNamespaceNode(Object ns, Project project) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NamespaceNode(ns));
        Facade facade = Model.getFacade();
        try {
            Collection subs = facade.getAllModelElementsOfKind(ns, Model.getMetaTypes().getPackage());
            if (subs != null) {
                for (Object sub : subs) {
                    DefaultMutableTreeNode child = buildNamespaceNode(sub, project);
                    if (child != null) node.add(child);
                }
            }
        } catch (RuntimeException ignored) {}
        for (Object d : project.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;
            if (ad.getNamespace() == ns) {
                node.add(new DefaultMutableTreeNode(new DiagramNode(ad), false));
            }
        }
        return node;
    }

    private JPanel buildButtonPanel() {
        JPanel p = new JPanel();
        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove");
        JButton close = new JButton("Close");
        add.addActionListener(e -> onAdd());
        remove.addActionListener(e -> onRemove());
        close.addActionListener(e -> dispose());
        p.add(add); p.add(remove); p.add(close);
        return p;
    }

    private void onAdd() {
        List<String> toAdd = collectSelectedDiagramUuids();
        int n = 0;
        for (String uuid : toAdd) {
            if (UseCaseOperations.addRepresentedDiagram(useCase, uuid)) n++;
        }
        if (n > 0) {
            javax.swing.JOptionPane.showMessageDialog(this,
                "Added " + n + " diagram(s).");
        }
    }

    private void onRemove() {
        TreePath[] sel = tree.getSelectionPaths();
        if (sel == null) return;
        int n = 0;
        for (TreePath p : sel) {
            Object node = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (node instanceof DiagramNode) {
                String uuid = ((DiagramNode) node).uuid;
                if (UseCaseOperations.removeRepresentedDiagram(useCase, uuid)) n++;
            }
        }
        if (n > 0) {
            javax.swing.JOptionPane.showMessageDialog(this,
                "Removed " + n + " diagram(s).");
        }
    }

    private List<String> collectSelectedDiagramUuids() {
        List<String> out = new ArrayList<String>();
        TreePath[] sel = tree.getSelectionPaths();
        if (sel == null) return out;
        for (TreePath p : sel) {
            Object o = ((DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject();
            if (o instanceof DiagramNode) out.add(((DiagramNode) o).uuid);
        }
        return out;
    }

    private static final class NamespaceNode {
        final Object ns;
        NamespaceNode(Object ns) { this.ns = ns; }
        @Override public String toString() {
            String n = (String) Model.getFacade().getName(ns);
            return n == null ? "(namespace)" : n;
        }
    }

    private static final class DiagramNode {
        final ArgoDiagram diagram;
        final String uuid;
        DiagramNode(ArgoDiagram ad) {
            this.diagram = ad;
            Object ns = ad.getNamespace();
            String u = "";
            try {
                String t = (String) Model.getFacade().getUUID(ns);
                if (t != null) u = t;
            } catch (RuntimeException ignored) {}
            this.uuid = u;
        }
        @Override public String toString() {
            return (diagram.getName() == null ? "(unnamed)" : diagram.getName())
                    + "  [" + diagram.getClass().getSimpleName() + "]";
        }
    }
}
```

**Step 2: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/uml/ui/UseCaseManageRepresentedDiagramsDialog.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```

**Step 3: install + Commit**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml install -DskipTests -o 2>&1 | tail -3
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-app/src/org/argouml/uml/ui/UseCaseManageRepresentedDiagramsDialog.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(dialog): UseCaseManageRepresentedDiagramsDialog (package tree + Add/Remove)"
```

---

### Task 11: UseCaseContextPopupFactory 返回 ArgoJMenu 动态子菜单

**Files:**
- Modify: `src/argouml-app/src/org/argouml/uml/ui/UseCaseContextPopupFactory.java`

**Step 1: 完整重写**

```java
package org.argouml.uml.ui;

import java.util.Collections;
import java.util.List;

import javax.swing.Action;

import org.argouml.model.Model;
import org.argouml.ui.ArgoJMenu;
import org.argouml.uml.diagram.ArgoDiagram;

public final class UseCaseContextPopupFactory implements ContextActionFactory {

    @Override
    public List<Action> createContextPopupActions(Object context) {
        if (context == null || !Model.getFacade().isAUseCase(context)) {
            return Collections.emptyList();
        }
        ArgoJMenu menu = new ArgoJMenu("menu.popup.related-diagrams");
        menu.add(new ActionManageRepresentedDiagrams(context));
        List<ArgoDiagram> diagrams =
                ActionNavigateRepresentedDiagram.lookupAllRepresentedDiagrams(context);
        if (!diagrams.isEmpty()) {
            menu.addSeparator();
            for (ArgoDiagram d : diagrams) {
                menu.add(new ActionJumpToRepresentedDiagram(context, d));
            }
        }
        return Collections.<Action>singletonList(menu);
    }
}
```

**Step 2: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/ui/UseCaseContextPopupFactory.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```

**Step 3: install + Commit**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml install -DskipTests -o 2>&1 | tail -3
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-app/src/org/argouml/ui/UseCaseContextPopupFactory.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(factory): UseCaseContextPopupFactory returns dynamic ArgoJMenu"
```

---

### Task 12: FigUseCase 添加 ∞ FigText 子组件 + 监听

**Files:**
- Modify: `src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/FigUseCase.java`

**Step 1: 加 import + 字段**

文件顶部 imports 加：

```java
import org.argouml.uml.diagram.ui.FigSingleLineText;
import org.argouml.uml.ui.ActionNavigateRepresentedDiagram;
import java.awt.Rectangle;
import java.util.List;
```

类字段加（在 `linkIndicatorFig = new FigSingleLineText("∞");` 位置附近）：

```java
    private FigSingleLineText linkIndicatorFig;
```

**Step 2: `initialize()` 在 `addFig(getStereotypeFig())` 之后追加**

```java
        linkIndicatorFig = new FigSingleLineText("\u221e");  // ∞
        linkIndicatorFig.setEditable(false);
        linkIndicatorFig.setLineWidth(0);
        linkIndicatorFig.setFilled(false);
        linkIndicatorFig.setVisible(false);
        addFig(linkIndicatorFig);
```

**Step 3: 加 `updateLinkIndicator()` + 在 `updateListeners` / 渲染时调用**

新增方法：

```java
    private void updateLinkIndicator() {
        Object owner = getOwner();
        if (owner == null || linkIndicatorFig == null) return;
        List<ArgoDiagram> all =
                ActionNavigateRepresentedDiagram.lookupAllRepresentedDiagrams(owner);
        boolean show = !all.isEmpty();
        linkIndicatorFig.setVisible(show);
        if (show) {
            Rectangle bounds = getBigPort().getBounds();
            linkIndicatorFig.setLocation(
                bounds.x + bounds.width - 14,
                bounds.y - 8);
        }
        damage();
    }
```

`updateListeners()` 方法末尾加一行 `updateLinkIndicator();`（或新增 listener 监听 `"representedDiagram"` model event）。

直接调用：把 `updateLinkIndicator()` 也加进 `setBounds()` 或 `updateListeners()` 末尾。

**Step 4: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/FigUseCase.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```

**Step 5: install + Commit**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml install -DskipTests -o 2>&1 | tail -3
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-app/src/org/argouml/uml/diagram/use_case/ui/FigUseCase.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(fig): FigUseCase ∞ indicator (top-right) for any links"
```

---

### Task 13: 属性面板 UseCaseRepresentedDiagramField 改为 JTable + Manage 按钮

**Files:**
- Modify: `src/argouml-core-umlpropertypanels/src/org/argouml/core/propertypanels/ui/UseCaseRepresentedDiagramField.java`

**Step 1: 完整重写 — 用 JTable**

```java
/* $Id$ (... license header ...) */
package org.argouml.core.propertypanels.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import org.argouml.i18n.Translator;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectManager;
import org.argouml.model.Facade;
import org.argouml.model.Model;
import org.argouml.ui.UseCaseManageRepresentedDiagramsDialog;
import org.argouml.uml.diagram.ArgoDiagram;
import org.argouml.ai.domain.usecasediagram.UseCaseOperations;

public final class UseCaseRepresentedDiagramField extends JPanel {

    private final LinkTableModel tableModel = new LinkTableModel();
    private final JTable table = new JTable(tableModel);
    private final JButton manageButton = new JButton(
            Translator.localize("button.manage"));
    private Object useCase;

    public UseCaseRepresentedDiagramField() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        JLabel header = new JLabel(Translator.localize("label.represented-diagrams"));
        add(header, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowGrid(false);
        table.setRowHeight(20);
        // Hide UUID column (index 2)
        table.removeColumn(table.getColumnModel().getColumn(2));
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override public void valueChanged(ListSelectionEvent e) { /* reserved */ }
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(280, 60));
        add(sp, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.add(Box.createHorizontalGlue());
        buttonRow.add(manageButton);
        add(buttonRow, BorderLayout.SOUTH);

        manageButton.addActionListener(e -> {
            if (useCase == null) return;
            new UseCaseManageRepresentedDiagramsDialog(useCase).setVisible(true);
        });
    }

    public void setTarget(Object target) {
        this.useCase = target;
        tableModel.setRows(buildRows(target));
    }

    private static List<Row> buildRows(Object useCase) {
        List<Row> rows = new ArrayList<Row>();
        if (useCase == null) return rows;
        List<String> uuids = UseCaseOperations.getRepresentedDiagrams(useCase);
        if (uuids.isEmpty()) return rows;
        Project p = ProjectManager.getManager().getCurrentProject();
        Facade facade = Model.getFacade();
        for (String uuid : uuids) {
            ArgoDiagram ad = lookupDiagram(p, uuid, facade);
            rows.add(new Row(
                ad == null ? "(missing)" : safeName(ad),
                ad == null ? uuid : pathOf(ad),
                uuid));
        }
        return rows;
    }

    private static ArgoDiagram lookupDiagram(Project p, String uuid, Facade facade) {
        if (p == null) return null;
        for (Object d : p.getDiagramList()) {
            if (!(d instanceof ArgoDiagram)) continue;
            ArgoDiagram ad = (ArgoDiagram) d;
            if (uuid.equals(ad.getName())) return ad;
            try {
                Object ns = ad.getNamespace();
                if (ns != null && uuid.equals(facade.getUUID(ns))) return ad;
            } catch (RuntimeException ignored) {}
        }
        return null;
    }

    private static String safeName(ArgoDiagram ad) {
        return ad.getName() == null ? "(unnamed)" : ad.getName();
    }

    private static String pathOf(ArgoDiagram ad) {
        Object ns = ad.getNamespace();
        if (ns == null) return "";
        String n = (String) Model.getFacade().getName(ns);
        return n == null ? "" : n;
    }

    private static final class Row {
        final String name;
        final String path;
        final String uuid;
        Row(String name, String path, String uuid) {
            this.name = name; this.path = path; this.uuid = uuid;
        }
    }

    private static final class LinkTableModel extends AbstractTableModel {
        private List<Row> rows = new ArrayList<Row>();
        private final String[] cols = {"Diagram", "Path", "UUID"};
        void setRows(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            if (c == 0) return row.name;
            if (c == 1) return row.path;
            return row.uuid;
        }
    }
}
```

**Step 2: 编译**

```bash
touch /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-core-umlpropertypanels/src/org/argouml/core/propertypanels/ui/UseCaseRepresentedDiagramField.java
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-core-umlpropertypanels/pom.xml compile -DskipTests -o -q 2>&1 | tail -5
```

**Step 3: install + Commit**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-core-umlpropertypanels/pom.xml install -DskipTests -o 2>&1 | tail -3
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-core-umlpropertypanels/src/org/argouml/core/propertypanels/ui/UseCaseRepresentedDiagramField.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "feat(panel): UseCaseRepresentedDiagramField → JTable + Manage dialog"
```

---

### Task 14: i18n key + 重打 fat jar + 重启 GUI

**Files:**
- Modify: `src/argouml-app/src/org/argouml/i18n/menu.properties`

**Step 1: 加 i18n key**

找到 `menu.popup.jump-to-represented-diagram` 那行附近，加：

```
menu.popup.related-diagrams = 关联图
```

**Step 2: 重打 fat jar + 重启 GUI**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-core-umlpropertypanels/pom.xml install -DskipTests -o 2>&1 | tail -3
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/pom.xml install -DskipTests -o 2>&1 | tail -3
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml install -DskipTests -o 2>&1 | tail -3
mvn -pl /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-build -am package -DskipTests -o 2>&1 | tail -3

# 关闭老 GUI
if [ -f /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid ]; then
  PID=$(cat /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid)
  if ps -p "$PID" > /dev/null 2>&1; then kill "$PID"; fi
fi
pkill -f "argouml.application.Main" 2>/dev/null
sleep 2

# 启动新 GUI
nohup /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-app/src/bin/run-argouml.sh \
  > /tmp/argouml-logs/argouml-gui.log 2>&1 &
GUI_PID=$!
sleep 12
if ps -p "$GUI_PID" > /dev/null 2>&1; then
  echo "GUI PID=$GUI_PID"
  echo $GUI_PID > /Users/lxd/Projects/ai/uml-project/argouml/tests/smoke/.gui.pid
else
  echo "FAILED"; tail -20 /tmp/argouml-logs/argouml-gui.log
fi
```

**Step 3: 验证类在 fat jar**

```bash
unzip -l /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-build/target/argouml-jar-with-dependencies.jar | grep -E "UseCaseManageRepresentedDiagrams|ActionJumpToRepresentedDiagram|ActionManageRepresentedDiagrams|AddUseCaseRepresentedDiagram|RemoveUseCaseRepresentedDiagram|ListUseCaseRepresentedDiagrams|SetUseCaseRepresentedDiagrams" | head -10
```

**Step 4: Commit i18n**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-app/src/org/argouml/i18n/menu.properties
git -c user.email="agent@local" -c user.name="opencode" commit -m "i18n: add menu.popup.related-diagrams = 关联图"
```

---

### Task 15: 添加 REST 端点测试

**Files:**
- Modify: `src/argouml-ai/tests/org/argouml/ai/inbound/rest/usecasediagram/handlers/TestUseCaseHandlers.java`

**Step 1: 加 8 个测试方法（追加在文件末尾即可）**

```java
    public void testListMultipleRepresentedDiagrams() {
        // assumes test fixture has set some diagrams via PUT
        new SetUseCaseRepresentedDiagramsHandler(svc).handle(ppWithName("U1"),
                new HashMap<String, String>(),
                "{\"diagramUuids\":[\"u1\",\"u2\"]}");
        Map<String, String> m = pp(); m.put("uuid", "U1UUID");
        ResponseEnvelope env = new ListUseCaseRepresentedDiagramsHandler(svc).handle(
                m, new HashMap<String, String>(), "");
        assertEquals(200, env.status);
        assertTrue(env.body.contains("u1") && env.body.contains("u2"));
    }

    public void testSetReplacesAllUuids() {
        // ... similar
    }

    public void testAddAppendsUuid() {
        new AddUseCaseRepresentedDiagramHandler(svc).handle(ppWithName("U1"),
                new HashMap<String, String>(),
                "{\"diagramUuid\":\"new-uuid\"}");
    }

    public void testAddDuplicateReturns409() {
        // ... setup, add once, add again -> expect 409
    }

    public void testRemoveReturns204() {
        // ...
    }

    public void testRemoveMissingUuidReturns404() {
        // ...
    }

    public void testAddNonexistentDiagramReturns404() {
        // ...
    }

    public void testLegacySingleUuidEndpointStillWorks() {
        // UseCase "U1" has [uuid1]; GET representedDiagram -> returns uuid1
    }

    private Map<String, String> ppWithName(String name) {
        Map<String, String> m = pp();
        m.put("u", name);
        return m;
    }
```

**Step 2: 跑测试**

```bash
mvn -f /Users/lxd/Projects/ai/uml-project/argouml/src/argouml-ai/pom.xml test -Dtest=TestUseCaseHandlers -o 2>&1 | tail -10
```
Expected: 全部通过 (原 17 + 新增 8 = 25)。

**Step 3: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add src/argouml-ai/tests/org/argouml/ai/inbound/rest/usecasediagram/handlers/TestUseCaseHandlers.java
git -c user.email="agent@local" -c user.name="opencode" commit -m "test: 8 new tests for 1:N REST endpoints"
```

---

### Task 16: 更新 AGENTS.md

**Files:**
- Modify: `AGENTS.md`

**Step 1: 在 Extension points 表格加 1 行**

```markdown
| Support 1:N element-to-diagram linking | (1) Bump shared cache (`RepresentedDiagramLinkCache`) value from `String` to `List<String>`; add `addUuid/removeUuid/getAll`. (2) Add `add/remove/getAll` to `UseCaseOperations` writing the same `representedDiagram` tag with `dataValues: String[]`. (3) Build a dynamic submenu `ArgoJMenu("关联图")` in `UseCaseContextPopupFactory` containing `Manage...` + one `JumptoDiagram` per linked diagram. (4) Browse dialog: reuse `DisplayTextTree` + `UMLTreeCellRenderer`; populate by walking `Project.getUserDefinedModelList()` and `Model.getModelManagementHelper().getAllModelElementsOfKind(ns, PACKAGE)`. (5) Fig decoration: add a `FigSingleLineText("∞")` child in `FigUseCase.initialize()` after `addFig(getStereotypeFig())`, toggle visibility via `setTarget` + tag event listener. (6) REST: `PUT /d/{d}/usecasediagram/usecases/by-name/{u}/representedDiagrams` (replace-all), `POST .../representedDiagram` (add-one), `DELETE .../representedDiagram/{uuid}` (remove-one), `GET .../{uuid}/representedDiagrams` (list-all) |
```

**Step 2: 重新打包 + 重启（确保新 AGENTS.md 不需要重新打包，AGENTS.md 是给人看的）**

不需要重新打包。

**Step 3: Commit**

```bash
cd /Users/lxd/Projects/ai/uml-project/argouml
git add AGENTS.md
git -c user.email="agent@local" -c user.name="opencode" commit -m "docs: extension-point for 1:N element-to-diagram linking"
```

---

## 执行选项

**Plan complete and saved to `docs/plans/2026-07-09-usecase-repr-diagram-multi.md`. Two execution options:**

1. **Subagent-Driven (this session)** - I dispatch fresh subagent per task, review between tasks, fast iteration
2. **Parallel Session (separate)** - Open new session with executing-plans, batch execution with checkpoints

**Which approach?**