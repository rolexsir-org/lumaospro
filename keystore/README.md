# Local Google Play upload-key setup

This repository contains no private signing key. Release variants are deliberately
**not** signed with the debug key. Without a complete `keystore.properties`, a
release build may be produced unsigned and is not suitable for uploading or
publishing.

For a new Play listing, use a dedicated upload key and enroll the app in Play
App Signing so Google Play protects and manages the app-signing key. Do not use
a debug key or reuse a private app-signing key as the upload key.

1. Generate a local RSA upload key. `keytool` prompts for the passwords; do not
   put passwords in shell history or commit them:

   ```bash
   keytool -genkeypair -v \
     -keystore keystore/lumaos-pro-upload.jks \
     -storetype JKS \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias lumaos-pro-upload
   ```

2. Create the gitignored repository-root `keystore.properties` file with the
   values below. Use the passwords entered above and keep the file private:

   ```properties
   storeFile=keystore/lumaos-pro-upload.jks
   storePassword=YOUR_LOCAL_STORE_PASSWORD
   keyAlias=lumaos-pro-upload
   keyPassword=YOUR_LOCAL_KEY_PASSWORD
   ```

   Restrict local file permissions where supported (`chmod 600
   keystore.properties`). Never put actual credentials in this example, chat,
   CI logs, source control, or screenshots.

3. Keep secure, tested backups of the upload keystore and credentials. Losing an
   upload key can block future updates; follow Play Console's supported upload
   key reset process if it is lost or compromised. Keep the Play app-signing
   key under Play App Signing, not in this repository.

Then build the signed bundle with `./build.sh bundleRelease`. Verify the bundle's
signature and package identity before uploading it to Play Console. No signing
key or release bundle has been generated in this workspace.
