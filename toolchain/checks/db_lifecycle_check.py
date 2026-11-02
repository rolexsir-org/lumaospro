#!/usr/bin/env python3
"""Static guard for the database-lifecycle defect that crashed Settings.

Why this exists
---------------
``DiagnosticsStore`` extends ``SQLiteOpenHelper``, and a helper owns exactly one
``SQLiteDatabase`` for its whole lifetime: ``readableDatabase`` and
``writableDatabase`` hand back that same cached instance, shared by every caller
in the process. The store used to wrap each operation in
``readableDatabase.use { ... }`` / ``writableDatabase.use { ... }``. Because
``SQLiteDatabase`` implements ``Closeable``, ``use`` closed the shared
connection after every read and write, so a concurrent operation failed with
"attempt to re-open an already-closed object" or "connection pool has been
closed" -- and since that happened inside a ``viewModelScope`` coroutine it
became an unhandled fatal exception.

The behavioural proof lives in
``app/src/test/java/com/lumaos/pro/launcher/robolectric/DiagnosticsStoreConcurrencyTest.kt``,
which runs the real SQLite engine under Robolectric. This script is the cheap
half: it needs nothing but Python 3, runs in milliseconds, and fails the moment
the pattern is reintroduced anywhere in the source tree.

It is a linter, not a behavioural test -- it inspects the shipped sources, it
does not execute them.

Usage
-----
    python3 toolchain/checks/db_lifecycle_check.py [repo_root]

Exits 0 when clean, 1 on violations, 2 on a usage error.
"""

from __future__ import annotations

import os
import re
import sys
from typing import Dict, Iterable, List, Set, Tuple

# `readableDatabase.use { ... }` and friends: the original defect.
DB_USE = re.compile(
    r"\b(?:readableDatabase|writableDatabase|getReadableDatabase\(\)|getWritableDatabase\(\))"
    r"\s*(?:!!)?\s*\.\s*use\b"
)

# `val db = writableDatabase` -- a name bound to the helper's shared connection.
DB_BINDING = re.compile(
    r"\bval\s+(\w+)\s*(?::[^=]+)?=\s*(?:\w+\.)*(?:readableDatabase|writableDatabase)\b"
)

# Calls that hand back something that must be closed.
QUERY_CALL = re.compile(r"\.(?:query|rawQuery|rawQueryWithFactory|compileStatement)\s*\(")

CLOSE_CALL = re.compile(r"\b(\w+)\.close\(\)")


def strip_comments(source: str) -> str:
    """Remove // and /* */ comments so commented-out code cannot trip a rule.

    Kotlin block comments nest, and string contents must survive untouched so a
    message mentioning `readableDatabase.use` is not reported as code.
    """
    out: List[str] = []
    i, n = 0, len(source)
    in_string = False
    while i < n:
        c = source[i]
        nxt = source[i + 1] if i + 1 < n else ""
        if in_string:
            out.append(c)
            if c == "\\" and nxt:
                out.append(nxt)
                i += 2
                continue
            if c == '"':
                in_string = False
            i += 1
            continue
        if c == '"':
            in_string = True
            out.append(c)
            i += 1
            continue
        if c == "/" and nxt == "/":
            while i < n and source[i] != "\n":
                i += 1
            continue
        if c == "/" and nxt == "*":
            depth = 1
            i += 2
            while i < n and depth > 0:
                if source[i] == "/" and i + 1 < n and source[i + 1] == "*":
                    depth += 1
                    i += 2
                elif source[i] == "*" and i + 1 < n and source[i + 1] == "/":
                    depth -= 1
                    i += 2
                else:
                    if source[i] == "\n":
                        out.append("\n")
                    i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check_file(relative: str, text: str) -> List[str]:
    source = strip_comments(text)
    lines = source.split("\n")
    found: List[str] = []
    db_names: Set[str] = set()
    pending_query: Tuple[int, str] | None = None

    for index, line in enumerate(lines, start=1):
        if DB_USE.search(line):
            found.append(
                f"{relative}:{index}: closes the SQLiteOpenHelper's shared connection "
                f"via `use`. Only a Cursor may be closed with `use`; the helper owns "
                f"its database for life and closes it once, at shutdown."
            )

        db_names.update(DB_BINDING.findall(line))

        for name in db_names:
            for closed in CLOSE_CALL.findall(line):
                if closed == name:
                    found.append(
                        f"{relative}:{index}: `{name}.close()` closes the helper's "
                        f"shared connection. Close the SQLiteOpenHelper once at "
                        f"shutdown instead."
                    )

        if QUERY_CALL.search(line):
            pending_query = (index, line.strip())

        # A cursor opened above is closed when its `.use` or `.close()` appears.
        if pending_query and (".use" in line or ".close()" in line):
            pending_query = None

    if pending_query:
        line_no, snippet = pending_query
        if len(snippet) > 70:
            snippet = snippet[:70] + "..."
        found.append(
            f"{relative}:{line_no}: cursor from `{snippet}` is never closed. "
            "Wrap the query in `.use { ... }`."
        )
    return found


def iter_kotlin(root: str) -> Iterable[str]:
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames.sort()
        for fn in sorted(filenames):
            if fn.endswith(".kt"):
                yield os.path.join(dirpath, fn)


def violations(source_root: str) -> List[str]:
    out: List[str] = []
    for path in iter_kotlin(source_root):
        with open(path, encoding="utf-8") as handle:
            out.extend(check_file(os.path.relpath(path, source_root), handle.read()))
    return out


def resolve_root(start: str) -> str:
    here = os.path.abspath(start)
    for _ in range(8):
        if os.path.isdir(os.path.join(here, "app", "src", "main", "java")):
            return here
        parent = os.path.dirname(here)
        if parent == here:
            break
        here = parent
    return os.path.abspath(start)


def main(argv: List[str]) -> int:
    root = resolve_root(argv[1] if len(argv) > 1 else os.getcwd())
    sources = os.path.join(root, "app", "src", "main", "java")
    if not os.path.isdir(sources):
        print(f"db_lifecycle_check: no such directory: {sources}", file=sys.stderr)
        return 2
    print(f"db_lifecycle_check: scanning {sources}")
    found = violations(sources)
    if not found:
        print("OK: no database-lifecycle violations.")
        return 0
    print(f"{len(found)} violation(s):")
    for item in found:
        print(f"  {item}")
    return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
