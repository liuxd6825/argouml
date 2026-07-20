# ArgoUML Internationalization Guidelines

This document is the canonical reference for anyone adding or
maintaining a language pack under `externals/argouml-i18n-<lang>/`.

---

## 1. Bundle naming

ArgoUML uses the Java `ResourceBundle` mechanism. Each UI string
lives in a `.properties` file whose name has the form:

    <BundleBaseName>_<language>_<COUNTRY>.properties

Where `<language>` is an ISO 639-1 code (lowercase) and `<COUNTRY>`
is an ISO 3166-1 alpha-2 code (uppercase). Examples:

    label_zh_CN.properties    Simplified Chinese
    label_zh_TW.properties    Traditional Chinese
    label_ja_JP.properties    Japanese

BundleBaseNames ArgoUML expects (defined by `Translator.getName(key)`):

| BundleBaseName | Purpose |
| --- | --- |
| `UMLResourceBundle` | model-element names and tooltips |
| `aboutbox`         | About dialog |
| `action`           | menu / button action labels |
| `button`           | dialog buttons (OK, Cancel, ...) |
| `checkbox`         | checkbox labels |
| `combobox`         | combo-box options |
| `critics`          | critic messages |
| `dialog`           | dialog titles and field labels |
| `filechooser`      | file dialog |
| `label`            | form labels (Name:, Type:, ...) |
| `menu`             | menu items |
| `misc`             | catch-all (untitled, unnamed, ...) |
| `mnemonic`         | keyboard shortcuts |
| `optionpane`       | message dialogs |
| `parsing`          | parser messages |
| `radiogroup`       | radio buttons |
| `statusmsg`        | status bar |
| `tab`              | tab titles |
| `tooltip`          | hover tooltips |

The keyspace is the same across all BundleBaseNames — the leading
prefix only affects lookup, never the key. So `button.ok` lives in
`button_xx.properties`; if a language module omits that file,
ArgoUML falls back to `ApplicationBundle_xx.properties` then to
`ApplicationBundle_en.properties`.

---

## 2. File encoding

Bundle `.properties` files are encoded as **UTF-8**. Authors may write
non-Latin characters directly in source:

    button.ok = \u786E\u5B9A          # legacy escape form, still supported
    button.ok = 确定                  # native UTF-8 form, preferred

Both forms are equivalent at runtime — Java's `ResourceBundle.load()`
decodes `\uXXXX` escapes regardless of source encoding, and UTF-8
literal bytes are read transparently via `Translator.UTF8_CONTROL`
(a JDK 9+-style `ResourceBundle.Control` subclass that overrides
`newBundle()` to use a UTF-8 `InputStreamReader`).

The vendored zh bundles (as of late 2025) are stored in **native UTF-8**
form. Authors writing new translations may use native UTF-8 directly
without any preprocessing. Legacy `\uXXXX` files are still accepted.

When you add a new language:

- **Latin-script languages** (fr, de, es, it, pt, ...) write plain
  UTF-8 — diacritics, accents, ñ all work natively.
- **CJK / Cyrillic / Arabic / Hebrew** languages also write plain
  UTF-8. No `native2ascii` step is required.
- **Legacy `\uXXXX` escapes** are accepted everywhere. To convert a
  legacy bundle to native UTF-8, run the helper script:

    scripts/decode-unicode-escapes.py FILE [FILE ...]

  The script is idempotent: running it twice produces no change. It
  preserves line endings (CRLF stays CRLF, LF stays LF) and validates
  that key counts are unchanged.

---

## 3. Fallback chain

When `Translator.localize("button.ok")` is called for locale `ja-JP`,
ArgoUML looks up:

1. `button_ja_JP.properties`
2. `ApplicationBundle_ja_JP.properties` (the catch-all bundle)
3. `button_ja.properties` (language-only fallback)
4. `ApplicationBundle_ja.properties`
5. `button_en.properties` (default locale fallback)
6. `ApplicationBundle_en.properties`
7. the key string itself (last resort)

So a brand-new language module needs to ship **only the keys that
exist** in ArgoUML's English bundles. Missing keys silently fall back
to English.

---

## 4. Adding a new language

See [`i18n-template/BUILD_INSTRUCTIONS.md`](i18n-template/BUILD_INSTRUCTIONS.md)
for the 5-step procedure that takes ~30 minutes from a clean
checkout. Always start from the [`i18n-template/`](i18n-template/)
skeleton — it contains a working `pom.xml.template`, a placeholder
`.properties`, and a stub OSGi manifest.

TL;DR — the 5 steps are:

1. `cp -r externals/argouml-i18n-zh/i18n-template externals/argouml-i18n-<tag>`
2. rename `pom.xml.template` → `pom.xml`, edit `<artifactId>`,
   `<name>`, jar name
3. translate `PLACEHOLDER_lang.properties` into the target language
   (rename to e.g. `label_xx_YY.properties`)
4. `mvn -f externals/argouml-i18n-<tag>/pom.xml install -DskipTests -o`
5. start ArgoUML with `-Duser.language=<lang> -Duser.country=<COUNTRY>`
   and verify

For contributing back to the main repo, see
[`../../docs/i18n-module-pulls.md`](../../docs/i18n-module-pulls.md).

---

## 5. Test coverage

A new language module should at minimum pass `mvn install` cleanly.
The ArgoUML test suite (`mvn -pl src/argouml-app test`) is **not
run against the language module** — the module ships a static JAR and
ArgoUML only `Class.forName`s `ResourceBundle` at startup. Verify
visually by launching ArgoUML with the new locale and confirming the
crash-on-missing-key fallback path (see step 5 above) does not trigger.

---

## 6. Existing modules

| Module path | Locales | Status |
| --- | --- | --- |
| `externals/argouml-i18n-zh` | zh-CN, zh-TW | well-tested (10+ years in upstream) |
| `externals/argouml-i18n-ja` | ja-JP | smoke-test only (3 keys) |

Add new modules by copying the structure described above. There is
**no upstream vendoring** — all translations live in this repo and
are committed directly.
