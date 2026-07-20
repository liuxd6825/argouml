# argouml
Main project of argouml.

Started in January 1998. Converted from CVS to Subversion in 2006. Converted to git in 2019.

## Resources

* Github organisation: <https://github.com/argouml-tigris-org>
* Web page with maven sites: <https://argouml-tigris-org.github.io/>

## Contributing

Short summary on how to contribute to the project using github and gerrithub:

* Push changes towards master to gerrithub.
* One change per issue.
* Code clean-up in separate changes (gerrithub will make each commit into a change).
* Use the repo tool.

A longer explaination is in <https://github.com/argouml-tigris-org/argouml/wiki/Working-in-the-project>

## Internationalization

The default UI is English. Add-on language packs live under
`externals/argouml-i18n-<lang>/` as independent Maven modules. Each
module ships a JAR containing only `.properties` files; ArgoUML's
`org.argouml.i18n.Translator` loads them via the standard Java
`ResourceBundle` lookup chain (locale → language → system default).

Existing packs:

| Tag | Locales | Status |
| --- | --- | --- |
| `zh` | zh-CN, zh-TW | well-tested (10+ years; ASCII `\uXXXX` escapes) |
| `ja` | ja-JP | smoke-test only (3 keys) |

To enable:

```bash
java -Duser.language=ja -Duser.country=JP -ea \
     -cp src/argouml-app/target/classes:/path/to/externals/argouml-i18n-ja/target/argouml-i18n-ja-0.35.2-SNAPSHOT.jar:... \
     org.argouml.application.Main
```

To **add a new language pack**, start from the
[`externals/argouml-i18n-zh/i18n-template/`](externals/argouml-i18n-zh/i18n-template/)
skeleton (see
[`BUILD_INSTRUCTIONS.md`](externals/argouml-i18n-zh/i18n-template/BUILD_INSTRUCTIONS.md)),
then follow the PR checklist in
[`docs/i18n-module-pulls.md`](docs/i18n-module-pulls.md).

Canonical reference:
[`externals/argouml-i18n-zh/I18N_GUIDELINES.md`](externals/argouml-i18n-zh/I18N_GUIDELINES.md).
