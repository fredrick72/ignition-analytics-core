# Module signing

Ignition refuses to install a `.modl` file on a Gateway that isn't in
developer mode unless the module is signed. This project ships a
self-signed build (see the root `LICENSE` for what that trust level does
and doesn't imply — a self-signed cert proves the module hasn't been
tampered with since signing, not who published it).

The maintainer's signing key lives in this directory as `ignition-analytics.jks`
/ `ignition-analytics.p7b`, plus `signing.properties` holding the keystore
and cert passwords. **None of those three files are committed** — see
`.gitignore` — because anyone with the keystore can sign a `.modl` that
looks like it came from this project.

`build.gradle.kts` checks for `signing/signing.properties` at configuration
time:

- If present, it loads the `ignition.signing.*` properties from it and
  builds a **signed** module (`build/ignition-analytics.modl`).
- If absent — the default for anyone who clones or forks the repo — the
  build falls back to `skipModlSigning`, producing an **unsigned** module
  (`build/ignition-analytics.unsigned.modl`) that still installs fine on
  a Gateway in developer mode.

## Generating your own signing key

You don't need the maintainer's key to build or test the module. If you
want your own local signed build (e.g. to test on a non-developer-mode
Gateway), generate a self-signed keystore the same way this one was made:

```bash
keytool -genkeypair \
  -alias ignition-analytics \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
  -validity 7300 \
  -keystore signing/ignition-analytics.jks \
  -storetype JKS \
  -storepass <choose-a-password> -keypass <same-password> \
  -dname "CN=Your Name, OU=Open Source, O=Your Org, C=US"

keytool -exportcert -alias ignition-analytics \
  -keystore signing/ignition-analytics.jks -storepass <choose-a-password> \
  -rfc -file signing/ignition-analytics.pem

openssl crl2pkcs7 -nocrl -certfile signing/ignition-analytics.pem \
  -out signing/ignition-analytics.p7b

rm signing/ignition-analytics.pem
```

Then create `signing/signing.properties`:

```properties
ignition.signing.keystoreFile=signing/ignition-analytics.jks
ignition.signing.keystorePassword=<choose-a-password>
ignition.signing.certAlias=ignition-analytics
ignition.signing.certPassword=<choose-a-password>
ignition.signing.certFile=signing/ignition-analytics.p7b
```

`./gradlew build` will pick it up automatically.

## Using a CA-issued certificate instead

For a module distributed more broadly than "trust me, it's unmodified,"
Inductive Automation recommends a certificate from an actual CA (see the
[SDK signing docs](https://www.sdk-docs.inductiveautomation.com/docs/getting-started/create-a-module/module-signing/)).
Swap the `keystoreFile`/`certFile` paths above for the CA-issued keystore
and PKCS7 chain — the rest of the build wiring is unchanged.
