#!/usr/bin/env python3
"""Poor-man's Maven resolver over merged Gradle caches (files-2.1 trees)."""
import json, os, re, sys
import xml.etree.ElementTree as ET

# Public GitHub repositories that commit a Gradle module cache (files-2.1).
# Clone each sparsely (git clone --depth 1 --filter=blob:none --sparse, then
# `git sparse-checkout set --no-cone <files-2.1 path>`) into .cache/deps/<dir>.
#   edmo  <- github.com/jonnevuorela/edmo-course            (caches/...)
#   masud <- github.com/masudrana35362/News-Apps-Using-Compose (caches/...)
#   cap   <- github.com/FairuzAldaPerkasa/Project-Capstone  ("Mobile Development/caches/...")
#   lid   <- github.com/TechnoVen-UG/LiD                     (gradle-home/caches/..., also D8 + apksig)
#   zhudi <- github.com/ThanhPham2111/Face_Filter            (.gradle-local/caches/...)
# Roots that are absent are skipped (a warning is printed).
CACHE_ROOTS = [
    "/home/user/rolexm/.cache/deps/edmo/caches/modules-2/files-2.1",
    "/home/user/rolexm/.cache/deps/lid/gradle-home/caches/modules-2/files-2.1",
    "/home/user/rolexm/.cache/deps/cap/Mobile Development/caches/modules-2/files-2.1",
    "/home/user/rolexm/.cache/deps/masud/caches/modules-2/files-2.1",
    "/home/user/rolexm/.cache/deps/zhudi/.gradle-local/caches/modules-2/files-2.1",
]

# group -> module -> version -> {filename -> path}
INDEX = {}

def build_index():
    for root in CACHE_ROOTS:
        if not os.path.isdir(root):
            print("MISSING CACHE ROOT:", root, file=sys.stderr)
            continue
        for dirpath, dirnames, filenames in os.walk(root):
            rel = os.path.relpath(dirpath, root)
            parts = rel.split(os.sep)
            if len(parts) != 4:
                continue
            group, module, version, _sha = parts
            slot = INDEX.setdefault(group, {}).setdefault(module, {}).setdefault(version, {})
            for fn in filenames:
                slot[fn] = os.path.join(dirpath, fn)

def find(group, module, version):
    return INDEX.get(group, {}).get(module, {}).get(version)

def pick_version(group, module, want):
    allv = INDEX.get(group, {}).get(module, {})
    if not allv:
        return None
    if want in allv:
        return want
    withbin = {v: files for v, files in allv.items()
               if any(fn.endswith((".aar", ".jar")) and not any(s in fn for s in ("-sources", "-javadoc", "-tests"))
                      for fn in files)}
    if not withbin:
        return sorted(allv)[-1]
    def key(v):
        return [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-+]", v)[:4]]
    try:
        cands = sorted(withbin, key=key)
        for v in cands:
            if key(v) >= key(want):
                return v
        return cands[-1]
    except Exception:
        return sorted(withbin)[-1]

def get_file(slot, ext):
    """Pick the canonical artifact file, avoiding sources/javadoc/tests."""
    if not slot:
        return None
    cands = [p for fn, p in slot.items()
             if fn.endswith("." + ext) and not any(s in fn for s in ("-sources", "-javadoc", "-tests", "-stubs"))]
    if not cands:
        return None
    # prefer exact '<name>-<version>.<ext>' style (no weird suffixes): shortest name is fine
    return sorted(cands, key=lambda p: (len(os.path.basename(p)), p))[0]

def deps_from_module(path):
    try:
        d = json.load(open(path))
    except Exception:
        return []
    out = []
    for v in d.get("variants", []):
        name = v.get("name", "")
        if not (name.endswith("ApiElements-published") or name.endswith("RuntimeElements-published")):
            continue
        for dep in v.get("dependencies", []):
            ver = (dep.get("version") or {}).get("requires") or (dep.get("version") or {}).get("prefers") or ""
            if ver:
                out.append((dep["group"], dep["module"], ver))
        aa = v.get("available-at")
        if isinstance(aa, dict):
            out.append((aa["group"], aa["module"], aa.get("version", "")))
        elif isinstance(aa, list):
            for item in aa:
                out.append((item["group"], item["module"], item.get("version", "")))
    return out

def deps_from_pom(path):
    try:
        tree = ET.parse(path)
    except Exception:
        return []
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    deps_el = tree.getroot().find("m:dependencies", ns)
    if deps_el is None:
        return []
    out = []
    for dep in deps_el.findall("m:dependency", ns):
        g = dep.findtext("m:groupId", default="", namespaces=ns)
        a = dep.findtext("m:artifactId", default="", namespaces=ns)
        ver = dep.findtext("m:version", default="", namespaces=ns)
        scope = dep.findtext("m:scope", default="compile", namespaces=ns)
        optional = dep.findtext("m:optional", default="false", namespaces=ns)
        if scope in ("test", "provided", "system"):
            continue
        if optional.strip().lower() == "true":
            continue
        if g and a and ver and not ver.startswith(("[", "(")):
            out.append((g, a, ver))
    return out

EXTRA_DEPS = {
    ("androidx.activity", "activity-compose"): [("androidx.activity", "activity"), ("androidx.activity", "activity-ktx")],
    ("androidx.activity", "activity-ktx"): [("androidx.activity", "activity")],
    ("androidx.activity", "activity"): [("androidx.core", "core"), ("androidx.lifecycle", "lifecycle-viewmodel"),
                                        ("androidx.lifecycle", "lifecycle-viewmodel-savedstate"), ("androidx.savedstate", "savedstate")],
    ("androidx.recyclerview", "recyclerview"): [("androidx.core", "core"), ("androidx.customview", "customview"),
                                                ("androidx.cursoradapter", "cursoradapter"), ("androidx.interpolator", "interpolator")],
}

def resolve(direct):
    pins = {(g, m) for g, m, _ in direct}
    chosen = {}
    queue = list(direct)
    resolved_keys = set()
    while queue:
        g, m, v = queue.pop(0)
        key = (g, m)
        ver = pick_version(g, m, v)
        if ver is None:
            print(f"UNRESOLVED: {g}:{m}:{v}")
            continue
        prev = chosen.get(key)
        if key in pins and ver != v:
            ver = v
        if prev is not None and prev != ver:
            if key in pins:
                if prev == chosen.get(key):
                    continue
            
            def vkey(x):
                return [int(y) if y.isdigit() else 0 for y in re.split(r"[.\-+]", x)[:4]]
            try:
                best = sorted([prev, ver], key=vkey)[-1]
            except Exception:
                best = ver
            if best == prev:
                continue
            ver = best
        if key in resolved_keys and chosen.get(key) == ver:
            continue
        chosen[key] = ver
        resolved_keys.add(key)
        slot = find(g, m, ver)
        deps = []
        mod = get_file(slot, "module")
        if mod:
            deps = deps_from_module(mod)
        if not deps:
            pom = get_file(slot, "pom")
            if pom:
                deps = deps_from_pom(pom)
        for dg, dm, dv in deps:
            queue.append((dg, dm, dv))
        for eg, em in EXTRA_DEPS.get((g, m), []):
            queue.append((eg, em, ver))
    artifacts = []
    def nearest_bin_version(g, m, v):
        slot = find(g, m, v)
        if slot and (get_file(slot, "aar") or get_file(slot, "jar")):
            return v
        allv = INDEX.get(g, {}).get(m, {})
        def key(x):
            return [int(y) if y.isdigit() else 0 for y in re.split(r"[.\-+]", x)[:4]]
        cands = [x for x in allv if get_file(allv[x], "aar") or get_file(allv[x], "jar")]
        if not cands:
            return v
        cands.sort(key=key)
        try:
            for x in cands:
                if key(x) >= key(v):
                    return x
        except Exception:
            pass
        return cands[-1]
    # dedup: prefer -android variant over MPP root when both have binaries
    drop = set()
    for (g, m), v in chosen.items():
        if not m.endswith(("-android", "-jvm")) and ((g, m + "-android") in chosen or (g, m + "-jvm") in chosen):
            if get_file(find(g, m, v), "aar") or get_file(find(g, m, v), "jar"):
                pass
            drop.add((g, m))
    chosen = {k: v for k, v in chosen.items() if k not in drop}
    for (g, m), v in sorted(chosen.items()):
        v = nearest_bin_version(g, m, v)
        slot = find(g, m, v)
        art = get_file(slot, "aar") or get_file(slot, "jar")
        if art:
            artifacts.append((g, m, v, art))
        else:
            print(f"NO-BINARY (pom/BOM only): {g}:{m}:{v}")
    return artifacts

def main():
    build_index()
    print(f"indexed groups: {len(INDEX)}", file=sys.stderr)
    direct = [
        ("androidx.core", "core-ktx", "1.13.1"),
        ("androidx.recyclerview", "recyclerview", "1.3.2"),
        ("androidx.lifecycle", "lifecycle-runtime-ktx", "2.8.3"),
        ("androidx.lifecycle", "lifecycle-process", "2.8.3"),
        ("androidx.lifecycle", "lifecycle-viewmodel-compose", "2.8.3"),
        ("androidx.compose.ui", "ui", "1.7.0"),
        ("androidx.compose.ui", "ui-graphics", "1.7.0"),
        ("androidx.compose.ui", "ui-tooling-preview", "1.7.0"),
        ("androidx.compose.material3", "material3", "1.3.0"),
        ("androidx.activity", "activity-compose", "1.9.2"),
        ("androidx.datastore", "datastore-preferences", "1.1.1"),
        ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.8.1"),
        ("androidx.activity", "activity", "1.9.2"),
        ("androidx.activity", "activity-ktx", "1.9.2"),
    ]
    arts = resolve(direct)
    out = [{"group": g, "module": m, "version": v, "file": f} for g, m, v, f in arts]
    json.dump(out, open("/home/user/rolexm/.cache/deps/resolved.json", "w"), indent=1)
    print(f"resolved artifacts: {len(arts)}")
    for a in arts:
        print(f"  {a[0]}:{a[1]}:{a[2]}  ->  {os.path.basename(a[3])}")

if __name__ == "__main__":
    main()
