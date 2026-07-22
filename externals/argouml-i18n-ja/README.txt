argouml-i18n-ja — Japanese localization skeleton for ArgoUML
============================================================

This module is a SMOKE TEST ONLY for the multi-language plumbing. It
ships three keys to verify:

  - ISO-8859-1 .properties files with `\uXXXX` escapes
  - the maven jar shape (resources at `org/argouml/i18n/*` in the JAR)
  - the `ResourceBundle.getBundle(...)` locale fallback chain
  - locale selection via `-Duser.language=ja -Duser.country=JP` on the JVM

The remaining ArgoUML UI strings are NOT translated yet — they fall
back to English when running with `ja_JP` locale. This matches the
behaviour of any new-language module until enough translators join.


How the smoke test works
------------------------

The shipped bundle `misc_ja_JP.properties` carries three keys:

  misc.unnamed                 = (Unnamed {0})
  button.ok                    = OK
  button.refresh               = Refresh

When the JVM runs with `-Duser.language=ja -Duser.country=JP`, the
`Translator` class looks up `misc_ja_JP.properties` first, then falls
back to `misc_en.properties` (or `ApplicationBundle_en.properties`).
Only the three keys above will appear in Japanese; everything else
is English.

The shipped bundle `UMLResourceBundle_ja_JP.properties` is intentionally
EMPTY so that future translators can fill it in incrementally without
breaking the build.


How to extend this module
-------------------------

For full documentation see:

  - externals/argouml-i18n-zh/I18N_GUIDELINES.md  (canonical reference)
  - externals/argouml-i18n-zh/i18n-template/BUILD_INSTRUCTIONS.md
                                                (5-step procedure)

TL;DR:

  1. Add more `.properties` files under src/org/argouml/i18n/.
     Follow the BundleBaseName conventions documented in
     I18N_GUIDELINES.md.
  2. Encoding: ISO-8859-1 with `\uXXXX` escapes for non-ASCII.
     Use `native2ascii -encoding UTF-8` to convert.
  3. mvn -f externals/argouml-i18n-ja/pom.xml install -DskipTests -o
  4. Start ArgoUML with `-Duser.language=ja -Duser.country=JP`
     and verify the new strings.


Why this module exists
----------------------

ArgoUML pulls its multi-language bundles from `externals/argouml-i18n-<lang>/`
at runtime — each jar drops on the classpath and the Translator class
loads the matching `.properties` files. Before this module existed,
adding Japanese would have required creating a new directory, pom,
and bundle from scratch. The i18n-template in argouml-i18n-zh makes
that 30 minutes of work; the ja folder here is the resulting
demonstration that the end-to-end mechanism actually works.


Status: stable, ships three keys, contributions welcome.
