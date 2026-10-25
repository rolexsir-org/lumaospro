#!/usr/bin/env node
// Sign the LumaOS Pro AAB (JAR v1 scheme) and APK (v1+v2+v3, zipaligned)
// using the pure-JS apk_sign_ts library. Reads keystore.properties.
import { readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const require = createRequire(import.meta.url);
const LIBDIR = process.env.APK_SIGN_TS_LIB || '/home/user/rolexm/.cache/deps/npm/apk_sign_ts';
const { ApkSigner, SigningKey, parseKeystore } = await import(join(LIBDIR, 'dist/index.js'));
const { V1Signer } = await import(join(LIBDIR, 'dist/V1Signer.js'));

const ROOT = '/home/user/rolexm';
const props = {};
for (const line of readFileSync(join(ROOT, 'keystore.properties'), 'utf8').split('\n')) {
  const m = line.match(/^(\w+)=(.*)$/);
  if (m) props[m[1]] = m[2].trim();
}
const ksBytes = new Uint8Array(readFileSync(join(ROOT, props.storeFile)));

const entry = await parseKeystore(ksBytes, props.storePassword, props.keyAlias);
const key = SigningKey.fromPEM(entry.privateKey, entry.certificate);

const mode = process.argv[2];
const input = process.argv[3];
const output = process.argv[4];
const data = new Uint8Array(readFileSync(input));

if (mode === 'aab') {
  const signer = new V1Signer({ signingKey: key, keyName: 'CERT' });
  const signed = await signer.sign(data);
  writeFileSync(output, signed instanceof Uint8Array ? signed : signed.signedApk ?? signed);
  console.log('signed AAB (JAR v1) ->', output);
} else if (mode === 'apk') {
  const signer = new ApkSigner({ signingKey: key });
  const { signedApk } = await signer.sign(data);
  writeFileSync(output, signedApk);
  console.log('signed APK (v1+v2+v3, aligned) ->', output);
} else {
  console.error('usage: sign.mjs aab|apk <input> <output>');
  process.exit(2);
}
