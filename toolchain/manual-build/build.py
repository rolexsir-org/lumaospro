#!/usr/bin/env python3
"""
HISTORICAL sandbox builder — NOT an approved production-release pipeline.

This custom path bypasses Gradle/AGP, may resolve library versions that differ
from the current catalog, does not apply the app's configured R8/resource
shrinking, and does not replace current tests, lint, or device verification.
Do not use its outputs for public Play release. Use the current Gradle release
configuration and have the project owner verify all resulting artifacts.

The script remains for historical provenance/recovery only. Phases:
  libs -> manifest -> resources -> codegen -> kotlin -> dex -> aab -> apk -> verify
"""
import json, os, re, shutil, subprocess, sys, zipfile
import xml.etree.ElementTree as ET

# --------------------------------------------------------------------------- #
# Paths
# --------------------------------------------------------------------------- #
ROOT = "/home/user/rolexm"
CACHE = os.path.join(ROOT, ".cache")
NPM = os.path.join(CACHE, "deps", "npm")
OUT = os.path.join(CACHE, "build")
DIST = os.path.join(ROOT, "dist")
GEN = os.path.join(OUT, "gen")

JAVA = os.path.join(CACHE, "toolchain/jdk/jdk4py/java-runtime/bin/java")
KEYTOOL = os.path.join(CACHE, "toolchain/jdk/jdk4py/java-runtime/bin/keytool")
AAPT2 = os.path.join(NPM, "aaptjs3/bin/x64/linux/aapt2")
KOTLINC = os.path.join(NPM, "kotlin-compiler/bin/kotlinc")
KOTLINC_LIB = os.path.join(NPM, "kotlin-compiler/lib")
COMPOSE_PLUGIN = os.path.join(KOTLINC_LIB, "compose-compiler-plugin.jar")
KOTLIN_STDLIB = os.path.join(KOTLINC_LIB, "kotlin-stdlib.jar")
D8JAR = os.path.join(CACHE, "deps/lid/gradle-home/caches/modules-2/files-2.1/com.android.tools.build/builder/8.13.0/d6c27fbcf3b96f30153c8ae51d4be386e36f6cf5/builder-8.13.0.jar")  # embeds D8/R8 8.13.6
APKSIGNER_JAR = os.path.join(NPM, "drxiaozhi-minapk/tools/apksigner.jar")
BUNDLETOOL = os.path.join(NPM, "bundletoolheavy/bundletoolfile/bundletool-all-1.17.2.jar")  # validate/build-apks
SIGNJS = os.path.join(ROOT, "toolchain/manual-build/sign.mjs")
ANDROID_JAR = os.path.join(CACHE, "android-36.jar")

KEYSTORE = os.path.join(ROOT, "keystore/lumaos-pro-upload.jks")
KEYSTORE_PROPS = os.path.join(ROOT, "keystore.properties")
KEY_ALIAS = "lumaos-pro-upload"

APP_ID = "com.lumaos.pro.launcher"
VERSION_CODE = 100
VERSION_NAME = "1.0.0"
MIN_SDK = 26
TARGET_SDK = 36

EXCLUDE = {
    ("org.jetbrains.kotlin", "kotlin-android-extensions-runtime"),
    ("org.jetbrains.kotlin", "kotlin-parcelize-runtime"),
    ("org.jetbrains.kotlin", "kotlin-stdlib"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-jdk7"),
    ("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
    ("androidx.datastore", "datastore-preferences-core"),  # 1.0.0 stub; -core-jvm 1.1.1 is used
}

def sh(args, **kw):
    env = dict(os.environ)
    env.setdefault("JAVA_HOME", os.path.dirname(os.path.dirname(JAVA)))
    env["PATH"] = os.path.dirname(JAVA) + ":" + env["PATH"]
    shown = [("--ks-pass", "pass:***") if a == "pass:***" else a for a in args]
    red = []
    hide = False
    for a in args:
        if hide:
            red.append("pass:***"); hide = False; continue
        if a in ("--ks-pass", "--key-pass"):
            red.append(a); hide = True; continue
        red.append(a)
    # Long classpaths make full command lines unreadable; show the tool and
    # the first arguments only, with long values elided.
    shown = [a if len(a) <= 120 else a[:60] + "..." + a[-40:] for a in red[:6]]
    print("+", " ".join(shown), "..." if len(red) > 6 else "")
    r = subprocess.run(args, env=env, **kw)
    if r.returncode != 0:
        sys.exit(f"command failed ({os.path.basename(args[0])}, exit {r.returncode})")
    return r

# --------------------------------------------------------------------------- #
# Phase 1: gather libraries
# --------------------------------------------------------------------------- #
def phase_libs():
    arts = json.load(open(os.path.join(CACHE, "deps", "resolved.json")))
    libjars, libdirs = [], []
    ext = os.path.join(OUT, "extract")
    shutil.rmtree(ext, ignore_errors=True)
    for a in arts:
        g, m, v, path = a["group"], a["module"], a["version"], a["file"]
        if (g, m) in EXCLUDE:
            continue
        name = f"{g}.{m}-{v}".replace("/", "_")
        d = os.path.join(ext, name)
        os.makedirs(d, exist_ok=True)
        if path.endswith(".aar"):
            with zipfile.ZipFile(path) as z:
                z.extractall(d)
            cj = os.path.join(d, "classes.jar")
            if os.path.exists(cj):
                libjars.append(cj)
            for extra in sorted(os.listdir(os.path.join(d, "libs")) if os.path.isdir(os.path.join(d, "libs")) else []):
                if extra.endswith(".jar"):
                    libjars.append(os.path.join(d, "libs", extra))
        else:
            cj = os.path.join(d, "classes.jar")
            shutil.copy(path, cj)
            libjars.append(cj)
        libdirs.append(d)
    # kotlin stdlib from the kotlinc distribution (2.2.21, matches the compiler)
    libjars.insert(0, KOTLIN_STDLIB)
    json.dump({"libjars": libjars, "libdirs": libdirs}, open(os.path.join(OUT, "libs.json"), "w"), indent=1)
    print(f"libraries: {len(libdirs)} aars/jars, {len(libjars)} classpath jars")

# --------------------------------------------------------------------------- #
# Phase 2: manifest merge
# --------------------------------------------------------------------------- #
A = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"

def _strip_tools(el):
    for k in list(el.attrib):
        if k.startswith("{") and not k.startswith(A):
            del el.attrib[k]
    for child in el:
        _strip_tools(child)

def _subst(v):
    return (v.replace("${applicationId}", APP_ID)
             .replace("${applicationIdSuffix}", "")) if v else v

def _merge_app_children(base_app, lib_app):
    def key(el):
        return (el.tag, _subst(el.attrib.get(A + "name", "")))
    base_by_key = {key(c): c for c in list(base_app)}
    for child in list(lib_app):
        k = key(child)
        existing = base_by_key.get(k)
        if existing is None:
            base_app.append(child)
            base_by_key[k] = child
        else:
            # merge meta-data children (e.g. androidx.startup InitializationProvider)
            if child.tag == "provider" or child.tag == "activity" or True:
                ex_meta = {_subst(m.attrib.get(A + "name")): m for m in existing.findall("meta-data")}
                for md in child.findall("meta-data"):
                    n = _subst(md.attrib.get(A + "name"))
                    if n not in ex_meta:
                        existing.append(md)

def phase_manifest():
    info = json.load(open(os.path.join(OUT, "libs.json")))
    app_mf = os.path.join(ROOT, "app/src/main/AndroidManifest.xml")
    tree = ET.parse(app_mf)
    root = tree.getroot()
    root.set("package", APP_ID)
    base_app = root.find("application")
    seen_perms = {p.attrib.get(A + "name") for p in root.findall("uses-permission")}
    for d in info["libdirs"]:
        mf = os.path.join(d, "AndroidManifest.xml")
        if not os.path.exists(mf):
            continue
        try:
            lroot = ET.parse(mf).getroot()
        except Exception:
            continue
        for p in lroot.findall("uses-permission"):
            n = p.attrib.get(A + "name")
            if n and n not in seen_perms:
                root.insert(0, p)
                seen_perms.add(n)
        lapp = lroot.find("application")
        if lapp is not None and base_app is not None:
            _merge_app_children(base_app, lapp)
    _strip_tools(root)
    # substitute placeholders anywhere
    for el in root.iter():
        for k, v in list(el.attrib.items()):
            el.attrib[k] = _subst(v)
    os.makedirs(os.path.join(OUT, "merged"), exist_ok=True)
    out_mf = os.path.join(OUT, "merged", "AndroidManifest.xml")
    ET.indent(tree, space="    ")
    tree.write(out_mf, encoding="utf-8", xml_declaration=True)
    print("merged manifest ->", out_mf)
    return out_mf

# --------------------------------------------------------------------------- #
# Phase 3: resources (aapt2 compile + link, binary and proto)
# --------------------------------------------------------------------------- #
def phase_resources(manifest):
    info = json.load(open(os.path.join(OUT, "libs.json")))
    flat = os.path.join(OUT, "flat")
    shutil.rmtree(flat, ignore_errors=True)
    os.makedirs(flat)
    flats = []
    def compile_dir(resdir, tag):
        outd = os.path.join(flat, tag)
        os.makedirs(outd, exist_ok=True)
        sh([AAPT2, "compile", "--dir", resdir, "-o", outd])
        for fn in sorted(os.listdir(outd)):
            if fn.endswith(".flat"):
                flats.append(os.path.join(outd, fn))
    # libraries first, app last (app wins via --auto-add-overlay)
    for i, d in enumerate(info["libdirs"]):
        resdir = os.path.join(d, "res")
        if os.path.isdir(resdir) and os.listdir(resdir):
            compile_dir(resdir, f"lib{i}")
    compile_dir(os.path.join(ROOT, "app/src/main/res"), "app")

    common = ["-I", ANDROID_JAR, "--manifest", manifest,
              "--auto-add-overlay",
              "--min-sdk-version", str(MIN_SDK), "--target-sdk-version", str(TARGET_SDK),
              "--version-code", str(VERSION_CODE), "--version-name", VERSION_NAME]
    for f in flats:
        common += ["-R", f]

    # binary APK resources
    sh([AAPT2, "link", "-o", os.path.join(OUT, "base.apk"),
        "--output-text-symbols", os.path.join(OUT, "R.txt"),
        "--proguard", os.path.join(OUT, "aapt2-keep.pro")] + common)
    # proto format for AAB
    sh([AAPT2, "link", "--proto-format", "-o", os.path.join(OUT, "proto.zip")] + common)
    print("resources linked; flats:", len(flats))

# --------------------------------------------------------------------------- #
# Phase 4: R.kt + BuildConfig.kt generation
# --------------------------------------------------------------------------- #
def parse_r_txt(path):
    """-> {type: {name: value}} where value is int or list[int] for arrays."""
    table = {}
    pat_arr = re.compile(r"^int\[\]\s+(\S+)\s+(\S+)\s+\{([^}]*)\}")
    pat_int = re.compile(r"^int\s+(\S+)\s+(\S+)\s+(-?\w+)")
    for line in open(path):
        line = line.strip()
        m = pat_arr.match(line)
        if m:
            typ, name, body = m.groups()
            ids = [int(x.strip(), 16) if x.strip().startswith("0x") else int(x.strip())
                   for x in body.split(",") if x.strip()]
            table.setdefault(typ, {})[name] = ids
            continue
        m = pat_int.match(line)
        if m:
            typ, name, val = m.groups()
            v = int(val, 16) if val.startswith("0x") else int(val)
            table.setdefault(typ, {})[name] = v
    return table

def kt_ident(name):
    return f"`{name}`" if not re.match(r"^[A-Za-z_][A-Za-z_0-9]*$", name) else name

def emit_r(package, table, only=None):
    lines = ["@file:Suppress(\"unused\", \"UNUSED_ANONYMOUS_PARAMETER\")", f"package {package}", "",
             "object R {"]
    for typ in sorted(table):
        entries = table[typ]
        if only is not None:
            entries = {n: v for n, v in entries.items() if (typ, n) in only or (typ == "styleable" and (typ, n.replace('$','.')) in only)}
        if not entries:
            continue
        lines.append(f"    object {kt_ident(typ)} {{")
        for n in sorted(entries):
            v = entries[n]
            if isinstance(v, list):
                arr = ", ".join(f"0x{x:08x}" for x in v)
                lines.append(f"        @JvmField val {kt_ident(n)} = intArrayOf({arr})")
            else:
                lines.append(f"        const val {kt_ident(n)} = 0x{v & 0xffffffff:08x}")
        lines.append("    }")
    lines.append("}")
    return "\n".join(lines) + "\n"

def phase_codegen():
    app_table = parse_r_txt(os.path.join(OUT, "R.txt"))
    os.makedirs(GEN, exist_ok=True)
    pkg_path = os.path.join(GEN, *APP_ID.split("."))
    os.makedirs(pkg_path, exist_ok=True)
    open(os.path.join(pkg_path, "R.kt"), "w").write(emit_r(APP_ID, app_table))
    open(os.path.join(pkg_path, "BuildConfig.kt"), "w").write(f"""package {APP_ID}

object BuildConfig {{
    const val DEBUG = false
    const val APPLICATION_ID = "{APP_ID}"
    const val BUILD_TYPE = "release"
    const val VERSION_CODE = {VERSION_CODE}
    const val VERSION_NAME = "{VERSION_NAME}"
}}
""")
    # Library R classes: each AAR's R.txt names, values from the app table.
    info = json.load(open(os.path.join(OUT, "libs.json")))
    made = 0
    for d in info["libdirs"]:
        rtxt = os.path.join(d, "R.txt")
        mf = os.path.join(d, "AndroidManifest.xml")
        if not (os.path.exists(rtxt) and os.path.exists(mf)):
            continue
        try:
            pkg = ET.parse(mf).getroot().attrib.get("package")
        except Exception:
            continue
        if not pkg:
            continue
        lib_names = parse_r_txt(rtxt)
        only = set()
        subset = {}
        for typ, names in lib_names.items():
            for n in names:
                if typ in app_table and n in app_table[typ]:
                    subset.setdefault(typ, {})[n] = app_table[typ][n]
        if not subset:
            continue
        lp = os.path.join(GEN, "libs", *pkg.split("."))
        os.makedirs(lp, exist_ok=True)
        open(os.path.join(lp, "R.kt"), "w").write(emit_r(pkg, subset))
        made += 1
    print(f"generated R for app + {made} libraries")

# --------------------------------------------------------------------------- #
# Phase 5: kotlin compile
# --------------------------------------------------------------------------- #
def phase_kotlin():
    info = json.load(open(os.path.join(OUT, "libs.json")))
    classes = os.path.join(OUT, "classes")
    shutil.rmtree(classes, ignore_errors=True)
    os.makedirs(classes)
    srcs = []
    for base in [os.path.join(ROOT, "app/src/main/java"), GEN]:
        for dp, _, fns in os.walk(base):
            for fn in fns:
                if fn.endswith(".kt"):
                    srcs.append(os.path.join(dp, fn))
    cp = ":".join([ANDROID_JAR] + info["libjars"])
    args = [KOTLINC, "-classpath", cp, "-jvm-target", "17",
            "-Xplugin=" + COMPOSE_PLUGIN,
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-d", classes] + srcs
    sh(args)
    print("kotlin compiled:", len(srcs), "sources")

# --------------------------------------------------------------------------- #
# Phase 5b: JVM unit tests (logic tests only)
# --------------------------------------------------------------------------- #
# JUnit 4.13.2 + Hamcrest 1.3 (SHA-1 verified against Maven Central:
# 8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12 / 42a25dc3219429f0e5d060061f71acb49bf010a0).
TESTLIBS = os.path.join(CACHE, "deps", "testlibs")
JUNIT_JARS = [os.path.join(TESTLIBS, "junit-4.13.2.jar"), os.path.join(TESTLIBS, "hamcrest-core-1.3.jar")]
# Robolectric runtime tests need org.robolectric jars plus a ~140 MB
# android-all-instrumented framework jar that cannot be obtained offline here,
# so that directory is excluded and reported as NOT RUN.
TEST_EXCLUDE_DIRS = ("robolectric",)

def phase_test():
    info = json.load(open(os.path.join(OUT, "libs.json")))
    test_root = os.path.join(ROOT, "app/src/test/java")
    tclasses = os.path.join(OUT, "test-classes")
    shutil.rmtree(tclasses, ignore_errors=True)
    os.makedirs(tclasses)
    srcs, skipped = [], []
    for dp, dns, fns in os.walk(test_root):
        rel = os.path.relpath(dp, test_root).split(os.sep)
        for fn in fns:
            if not fn.endswith(".kt"):
                continue
            (skipped if any(part in TEST_EXCLUDE_DIRS for part in rel) else srcs).append(os.path.join(dp, fn))
    cp = [os.path.join(OUT, "classes"), ANDROID_JAR] + info["libjars"] + JUNIT_JARS
    # -Xfriend-paths lets tests see `internal` declarations, as Gradle does for
    # a module's own test source set.
    sh([KOTLINC, "-classpath", ":".join(cp), "-jvm-target", "17",
        "-Xfriend-paths=" + os.path.join(OUT, "classes"), "-d", tclasses] + srcs)
    names = []
    for dp, _, fns in os.walk(tclasses):
        for fn in fns:
            if fn.endswith("Test.class"):
                names.append(os.path.relpath(os.path.join(dp, fn), tclasses)[:-6].replace(os.sep, "."))
    names.sort()
    # android.jar goes LAST at runtime so real library classes win over stubs.
    run_cp = [tclasses, os.path.join(OUT, "classes"), KOTLIN_STDLIB] + info["libjars"] + JUNIT_JARS + [ANDROID_JAR]
    print(f"running {len(names)} test classes; skipped (Robolectric, not runnable offline): {len(skipped)} files")
    sh([JAVA, "-cp", ":".join(run_cp), "org.junit.runner.JUnitCore"] + names)

# --------------------------------------------------------------------------- #
# Phase 6: dex (D8)
# --------------------------------------------------------------------------- #
def phase_dex():
    info = json.load(open(os.path.join(OUT, "libs.json")))
    dexdir = os.path.join(OUT, "dex")
    shutil.rmtree(dexdir, ignore_errors=True)
    os.makedirs(dexdir)
    # jar up our classes
    appjar = os.path.join(OUT, "app-classes.jar")
    with zipfile.ZipFile(appjar, "w", zipfile.ZIP_DEFLATED) as z:
        for dp, _, fns in os.walk(os.path.join(OUT, "classes")):
            for fn in fns:
                p = os.path.join(dp, fn)
                z.write(p, os.path.relpath(p, os.path.join(OUT, "classes")))
    sh([JAVA, "-cp", D8JAR, "com.android.tools.r8.D8", "--release",
        "--min-api", str(MIN_SDK), "--lib", ANDROID_JAR,
        "--output", dexdir, appjar] + info["libjars"])
    print("dex:", sorted(os.listdir(dexdir)))

# --------------------------------------------------------------------------- #
# Phase 7: AAB
# --------------------------------------------------------------------------- #
def encode_bundle_config(version="1.17.2"):
    # BundleConfig { Bundletool bundletool = 1; } / Bundletool { string version = 1; }
    def varint(n):
        out = b""
        while True:
            b = n & 0x7F
            n >>= 7
            out += bytes([b | (0x80 if n else 0)])
            if not n:
                return out
    inner = b"\x12" + varint(len(version)) + version.encode()  # Bundletool.version = field 2 (field 1 reserved)
    return b"\x0a" + varint(len(inner)) + inner

def phase_aab():
    proto = os.path.join(OUT, "proto.zip")
    aab_path = os.path.join(OUT, "app-release-unsigned.aab")
    if os.path.exists(aab_path):
        os.remove(aab_path)
    with zipfile.ZipFile(proto) as pz, zipfile.ZipFile(aab_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("BundleConfig.pb", encode_bundle_config())
        for n in pz.namelist():
            if n.startswith("res/"):
                z.writestr("base/" + n, pz.read(n))
            elif n == "AndroidManifest.xml":
                z.writestr("base/manifest/AndroidManifest.xml", pz.read(n))
            elif n == "resources.pb":
                z.writestr("base/resources.pb", pz.read(n))
            elif n.startswith("assets/"):
                z.writestr("base/root/" + n, pz.read(n))
    dexdir = os.path.join(OUT, "dex")
    with zipfile.ZipFile(aab_path, "a") as z:
        for fn in sorted(os.listdir(dexdir)):
            if fn.endswith(".dex"):
                z.write(os.path.join(dexdir, fn), "base/dex/" + fn)
    os.makedirs(DIST, exist_ok=True)
    sh(["node", SIGNJS, "aab", aab_path, os.path.join(DIST, "app-release.aab")])
    print("signed AAB ->", os.path.join(DIST, "app-release.aab"))

# --------------------------------------------------------------------------- #
# Phase 8: universal APK from the AAB
# --------------------------------------------------------------------------- #
def phase_apk():
    # Universal-style APK straight from the aapt2-linked resources + dex
    # (bundletool's build-apks cannot run here: the sandbox blocks JVM child
    # processes, and build-apks shells out to aapt2).
    apk = os.path.join(OUT, "app-release-unsigned.apk")
    shutil.copy(os.path.join(OUT, "base.apk"), apk)
    with zipfile.ZipFile(apk, "a") as z:
        for fn in sorted(os.listdir(os.path.join(OUT, "dex"))):
            if fn.endswith(".dex"):
                z.write(os.path.join(OUT, "dex", fn), fn)
    sh(["node", SIGNJS, "apk", apk, os.path.join(DIST, "app-release.apk")])
    print("signed APK ->", os.path.join(DIST, "app-release.apk"))

# --------------------------------------------------------------------------- #
# Phase 9: verify
# --------------------------------------------------------------------------- #
def phase_verify():
    import hashlib, base64
    print("\n=== bundletool validate (AAB) ===")
    sh([JAVA, "-jar", BUNDLETOOL, "validate", "--bundle=" + os.path.join(DIST, "app-release.aab")])
    print("\n=== AAB JAR-signature digest check ===")
    aab = os.path.join(DIST, "app-release.aab")
    with zipfile.ZipFile(aab) as z:
        names = z.namelist()
        assert "META-INF/MANIFEST.MF" in names, "missing MANIFEST.MF"
        sf = [n for n in names if n.startswith("META-INF/") and n.endswith(".SF")]
        rsa = [n for n in names if n.startswith("META-INF/") and (n.endswith(".RSA") or n.endswith(".DSA") or n.endswith(".EC"))]
        assert sf and rsa, "missing signature block files"
        mf = z.read("META-INF/MANIFEST.MF").decode()
        ok = bad = 0
        cur = None
        digests = {}
        for line in mf.splitlines():
            if line.startswith("Name: "):
                cur = line[6:]
            elif line.startswith("SHA-256-Digest: ") and cur:
                digests[cur] = line.split(": ", 1)[1].strip()
        for name, d in digests.items():
            if name.startswith("META-INF/"):
                continue
            calc = base64.b64encode(hashlib.sha256(z.read(name)).digest()).decode()
            if calc == d:
                ok += 1
            else:
                bad += 1
                print("  DIGEST MISMATCH:", name)
        print(f"  MANIFEST.MF entries verified: {ok}, mismatches: {bad}")
        print(f"  signature files: {sf + rsa}")
        assert bad == 0
    print("\n=== apksig verify (APK v1/v2/v3) ===")
    verify_jar = os.path.join(OUT, "verify.jar")
    apksig = subprocess.run(["bash", "-c", "find /home/user/rolexm/.cache/deps/lid -name 'apksig-8.13.0.jar' | head -1"],
                            capture_output=True, text=True).stdout.strip()
    sh([KOTLINC, "-classpath", apksig, "-include-runtime", "-d", verify_jar,
        os.path.join(ROOT, "toolchain/manual-build/verify.kt")])
    sh([JAVA, "-cp", verify_jar + ":" + apksig, "VerifyKt", os.path.join(DIST, "app-release.apk")])
    print("\n=== aapt2 dump badging ===")
    r = subprocess.run([AAPT2, "dump", "badging", os.path.join(DIST, "app-release.apk")],
                       capture_output=True, text=True)
    for line in r.stdout.splitlines()[:10]:
        print("  ", line)
    import hashlib
    with open(os.path.join(DIST, "SHA256SUMS"), "w") as fh:
        for f in ["app-release.aab", "app-release.apk"]:
            p = os.path.join(DIST, f)
            h = hashlib.sha256(open(p, "rb").read()).hexdigest()
            fh.write(f"{h}  {f}\n")
            print(f"{f}: {os.path.getsize(p)/1024/1024:.2f} MB  sha256={h}")

# --------------------------------------------------------------------------- #
# Keystore
# --------------------------------------------------------------------------- #
def load_keystore():
    props = {}
    if os.path.exists(KEYSTORE_PROPS):
        for line in open(KEYSTORE_PROPS):
            if "=" in line and not line.strip().startswith("#"):
                k, v = line.split("=", 1)
                props[k.strip()] = v.strip()
    return props

def phase_keystore():
    if os.path.exists(KEYSTORE) and os.path.exists(KEYSTORE_PROPS):
        print("keystore already configured")
        return
    import secrets
    pw = secrets.token_urlsafe(18)
    os.makedirs(os.path.dirname(KEYSTORE), exist_ok=True)
    sh([KEYTOOL, "-genkeypair", "-storetype", "JKS", "-keystore", KEYSTORE, "-alias", KEY_ALIAS,
        "-keyalg", "RSA", "-keysize", "4096", "-validity", "36500",
        "-storepass", pw, "-keypass", pw,
        "-dname", "CN=LumaOS Pro Release Upload, O=LumaOS Pro, C=IN"])
    with open(KEYSTORE_PROPS, "w") as f:
        f.write(f"storeFile=keystore/lumaos-pro-upload.jks\nstorePassword={pw}\nkeyAlias={KEY_ALIAS}\nkeyPassword={pw}\n")
    os.chmod(KEYSTORE_PROPS, 0o600)
    print("generated upload keystore:", KEYSTORE, "(credentials in keystore.properties)")

# --------------------------------------------------------------------------- #
def main():
    os.makedirs(OUT, exist_ok=True)
    steps = sys.argv[1:] or ["all"]
    manifest = os.path.join(OUT, "merged", "AndroidManifest.xml")
    if "all" in steps or "keystore" in steps:
        phase_keystore()
    if "all" in steps or "libs" in steps:
        phase_libs()
    if "all" in steps or "manifest" in steps:
        manifest = phase_manifest()
    if "all" in steps or "resources" in steps:
        phase_resources(manifest)
    if "all" in steps or "codegen" in steps:
        phase_codegen()
    if "all" in steps or "kotlin" in steps:
        phase_kotlin()
    if "test" in steps:
        phase_test()
    if "all" in steps or "dex" in steps:
        phase_dex()
    if "all" in steps or "aab" in steps:
        phase_aab()
    if "all" in steps or "apk" in steps:
        phase_apk()
    if "all" in steps or "verify" in steps:
        phase_verify()

if __name__ == "__main__":
    main()
