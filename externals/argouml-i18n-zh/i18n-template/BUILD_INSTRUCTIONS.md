# How to add a new language pack to ArgoUML

This 5-step procedure takes about 30 minutes and adds a new
language module under `externals/argouml-i18n-<lang>/`.

---

## Step 1 — Copy the template

```bash
cd /path/to/argouml
cp -r externals/argouml-i18n-zh/i18n-template \
      externals/argouml-i18n-<tag>
```

`<tag>` is a short lowercase mnemonic, conventionally the ISO 639-1
language code (`fr`, `de`, `es`, `it`, `ja`, `ko`, `ru`, ...).

## Step 2 — Renaming

Inside the new directory:

```bash
cd externals/argouml-i18n-<tag>
mv pom.xml.template pom.xml
```

Edit `pom.xml` and replace every `<!--lang-->` placeholder with the
language tag. Usually you change:

- `<artifactId>argouml-i18n-<!--lang--></artifactId>` → `<artifactId>argouml-i18n-fr</artifactId>`
- `<name>argouml-<!--lang--></name>` → `<name>argouml-fr</name>`
- the description block (any free-form description)

The rest of the pom (parent, properties, build/resources, scm) is
already correct for any language pack.

## Step 3 — Translate

Inside `src/org/argouml/i18n/` you will find:

- `README.txt` — leave in English (or replace with your target language)
- `PLACEHOLDER_lang.properties` — translate the values

Rename the `.properties` file to follow the BundleBaseName convention
listed in `../I18N_GUIDELINES.md`. Common ones to ship first:

```
label_<lang>_<COUNTRY>.properties
button_<lang>_<COUNTRY>.properties
misc_<lang>_<COUNTRY>.properties
```

`<lang>` is ISO 639-1 lowercase, `<COUNTRY>` is ISO 3166-1 alpha-2
uppercase (`fr`, `FR` for France; `de`, `DE` for Germany; etc.).

**Encoding**:
- Latin script (fr, de, es, it, pt) — plain UTF-8 or ISO-8859-1; both work.
- CJK / Cyrillic / Arabic / Hebrew — ISO-8859-1 with `\uXXXX` escapes.
  Pre-convert via `native2ascii -encoding UTF-8 in out`.

Optional: ship more bundles (action, dialog, menu, ...) as you
translate them. Missing keys fall back to English silently.

## Step 4 — Build the JAR

```bash
mvn -f externals/argouml-i18n-<tag>/pom.xml install -DskipTests -o
```

This installs the JAR to `~/.m2/repository/org/argouml/argouml-i18n-<tag>/`.

If `mvn install` complains:

- "Encoding errors" — re-run `native2ascii` against your .properties files
- "Missing parentpom" — you need `~/.m2/repository/org/argouml/parentpom/`
  resolved first; the project pom in the repo root should make this automatic.

## Step 5 — Verify in ArgoUML

Build and start ArgoUML with your new locale on the classpath:

```bash
mvn -f src/argouml-build/pom.xml -am install -DskipTests -o   # if you need a fat jar

# Or run from target/classes as documented in AGENTS.md:
java -Duser.language=<lang> -Duser.country=<COUNTRY> -ea \
     -cp src/argouml-app/target/classes:external-jar-path/* \
     org.argouml.application.Main
```

(The exact classpath depends on your working directory. See
`AGENTS.md` in the repo root for the canonical launch recipe.)

You should see your translated strings where you shipped them,
and English fallback for every key you didn't translate.

---

## Contributing back

Open a PR against `liuxd6825/argouml` (or whichever fork is
authoritative — see `docs/i18n-module-pulls.md`) with:

- the new `externals/argouml-i18n-<tag>/` directory
- an update to `README.md` listing the new language
- an update to `externals/argouml-i18n-zh/I18N_GUIDELINES.md` if you
  discovered a non-obvious gotcha (optional)

The PR does NOT need a `pom.xml` edit at the reactor level — the
new language module is intentionally NOT registered in `<modules>`
to keep `mvn install` cheap. See `externals/argouml-i18n-zh/I18N_GUIDELINES.md`
for the rationale.

---

## FAQ

**Q. The translator family will not fall back on missing locale.**

A. Check the file name pattern: `<BundleBaseName>_<lang>_<COUNTRY>.properties`.
Lowercase `<lang>`, uppercase `<COUNTRY>`. ArgoUML uses
`ResourceBundle.getBundle(...)` which only matches this exact pattern.

**Q. My characters render as `\uXXXX` instead of the real character.**

A. Either you saved as UTF-8 but ArgoUML reads ISO-8859-1, or you
forgot the `native2ascii` step. Re-run the encoder.

**Q. Argouml starts but logs "Translator: missing bundle `<name>`" at every call.**

A. The JAR is on the classpath but its `org/argouml/i18n/*.properties`
files don't end up at the right path inside the JAR. Check the
`<resources>` block of your pom — by default the template places
files at the JAR root. If you changed the build to nest them
under `META-INF/`, ArgoUML won't see them.
