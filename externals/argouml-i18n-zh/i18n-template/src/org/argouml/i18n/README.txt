Language module template for ArgoUML
====================================

This directory is a copy-and-translate starting point for adding a new
language pack to ArgoUML.

How to use it:

1. See BUILD_INSTRUCTIONS.md (one level up) for the 5-step procedure.
2. Start by translating PLACEHOLDER_lang.properties into your target
   language. Rename it to e.g. label_xx_YY.properties where xx is the
   ISO 639-1 language code and YY is the ISO 3166-1 alpha-2 country
   code (capitalised).
3. Encoding notes (see I18N_GUIDELINES.md):
    - Use UTF-8 source files. Write non-Latin characters directly.
    - Latin scripts (English, French, German, ...) stay ASCII or use
      UTF-8 with diacritics.
    - CJK / Cyrillic / Arabic / Hebrew use plain UTF-8. No
      native2ascii step is required.
    - Legacy \uXXXX escapes (ISO-8859-1 + Java Unicode escapes) are
      still accepted at runtime. To convert a legacy bundle to
      native UTF-8, run
      ../../scripts/decode-unicode-escapes.py <bundle>.properties

Do NOT translate or rename README.txt — it stays in English.

After your first successful build (`mvn install -DskipTests -o`),
delete this README.txt from YOUR copy and replace it with the
target-language equivalent, or leave a translated equivalent if
the codebase still references the English README (it does not).

See also:
- BUILD_INSTRUCTIONS.md (operational)
- ../I18N_GUIDELINES.md (canonical reference)
- ../../../docs/i18n-module-pulls.md (PR checklist for the main repo)
