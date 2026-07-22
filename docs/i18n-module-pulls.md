# Adding a new language pack to ArgoUML — PR checklist

This is a maintainer-facing checklist for PRs that introduce a new
i18n module under `externals/argouml-i18n-<lang>/`. It is intentionally
short — the technical instructions are in
[`externals/argouml-i18n-zh/i18n-template/BUILD_INSTRUCTIONS.md`](../externals/argouml-i18n-zh/i18n-template/BUILD_INSTRUCTIONS.md)
and the canonical reference is
[`externals/argouml-i18n-zh/I18N_GUIDELINES.md`](../externals/argouml-i18n-zh/I18N_GUIDELINES.md).

---

## Pre-submit

- [ ] New directory `externals/argouml-i18n-<tag>/` created, populated
      from `externals/argouml-i18n-zh/i18n-template/` (per
      BUILD_INSTRUCTIONS.md).
- [ ] At least one `<BundleBase>_<lang>_<COUNTRY>.properties` file
      inside `src/org/argouml/i18n/`. Smoke-test modules with three
      keys are welcome; full translations are nice but optional.
- [ ] `.properties` files are ISO-8859-1 encoded with `\uXXXX`
      escapes for non-Latin characters (verify with `file ...` and
      `iconv -f UTF-8 ...` should fail).
- [ ] `mvn -f externals/argouml-i18n-<tag>/pom.xml install -DskipTests -o`
      succeeds and drops a jar under
      `~/.m2/repository/org/argouml/argouml-i18n-<tag>/`.

## Functional smoke test

- [ ] Open `src/argouml-app/target/classes/...` plus the freshly built
      ja (or whatever) jar on the classpath (see `AGENTS.md` in the
      repo root for the canonical launch recipe).
- [ ] Start with `-Duser.language=<lang> -Duser.country=<COUNTRY>`.
- [ ] Verify: every translated key shows the new value; every untrans-
      lated key shows the English fallback. **No `MissingResource-
      Exception` should appear in stdout.**
- [ ] Smoke-test modules with very few keys are explicitly fine —
      other keys falling back to English is the expected baseline.

## Documentation

- [ ] `README.txt` in the module's top directory explaining what is
      shipped, what's still TODO (translation completeness), and a
      contact for contributors.
- [ ] Update root `README.md` — add the new language to the table.
- [ ] Optional: if you discovered a non-obvious gotcha while translating,
      add it to `externals/argouml-i18n-zh/I18N_GUIDELINES.md`.

## Things the maintainers WILL reject

- ❌ Adding the new module to `<modules>` in the root `pom.xml`. The
  language modules are intentionally kept out of the reactor so that
  `mvn install` for the core build stays small and fast. See
  `externals/argouml-i18n-zh/I18N_GUIDELINES.md` §"Test coverage" for
  the rationale.
- ❌ UTF-8-encoded `.properties` files. Today's Translator reads in
  ISO-8859-1 mode; future-Java-9+ support for UTF-8 is welcome but
  out of scope for this PR format.
- ❌ Vendoring from any external GitHub repo. All translations live
  directly in this repo. If you imported content from upstream, the
  PR description must say so and link to the vendored source.

## Things the maintainers WILL approve

- ✅ Empty `<Bundle>_<lang>_<COUNTRY>.properties` files — they're
  the natural "I haven't translated this bundle yet" state.
- ✅ Unicode escapes hand-converted via `native2ascii -encoding UTF-8`.
- ✅ Reformatting/extending I18N_GUIDELINES.md with new gotchas.
- ✅ Additional `.properties` files in subsequent PRs (translations
  grow over time, no need to translate everything at once).
