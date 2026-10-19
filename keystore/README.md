# Release signing key

`rolexm.jks` is a **development** key generated inside this workspace so that
`./build.sh assembleRelease` produces a consistently signed, installable APK.

It is *not* a publishing key. Before distributing on Google Play or anywhere
public:

1. Generate your own key:
   ```
   keytool -genkeypair -v -keystore rolexm-release.jks -keyalg RSA -keysize 2048 \
     -validity 10000 -alias rolexm
   ```
2. Point `keystore.properties` at it.
3. Keep the new keystore and its password somewhere safe and **backed up**.
   A lost upload key cannot be recovered, and the Play Store will not accept a
   replacement for an existing app.

`keystore.properties` and `*.jks` are listed in `.gitignore` so the password is
never committed.
