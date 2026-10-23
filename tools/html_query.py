"""Consultas pequenas e truncadas ao HTML de origem (para auditoria progressiva).

  python -I tools/html_query.py show  <ini> <fim> [largura]
  python -I tools/html_query.py grep  <regex> [max] [largura]     (regex Python, insensível a maiúsculas)
  python -I tools/html_query.py fn    <nome> [max_linhas] [largura] (todas as definições de <nome>)
  python -I tools/html_query.py ids   <regex>                     (ids encontrados em atributos/strings)
  python -I tools/html_query.py cut   <linha> <regex> [chars]     (texto a partir do match numa linha longa)

O ficheiro de origem é fixo: Kupila%201.0.239.html na raiz do projecto.
"""
import re
import sys
from pathlib import Path

SRC = Path(__file__).resolve().parent.parent / "Kupila%201.0.239.html"
L = [ln.rstrip("\r") for ln in SRC.read_text(encoding="utf-8", errors="replace").split("\n")]


def clip(s, w):
    s = s.replace("\t", " ")
    return s if len(s) <= w else s[:w] + "…"


def out(s):
    sys.stdout.buffer.write((s + "\n").encode("utf-8", "replace"))


cmd = sys.argv[1]
if cmd == "show":
    a, b = int(sys.argv[2]), int(sys.argv[3])
    w = int(sys.argv[4]) if len(sys.argv) > 4 else 240
    for i in range(a, min(b, len(L)) + 1):
        out(f"{i}\t{clip(L[i - 1], w)}")
elif cmd == "grep":
    rx = re.compile(sys.argv[2], re.I)
    mx = int(sys.argv[3]) if len(sys.argv) > 3 else 60
    w = int(sys.argv[4]) if len(sys.argv) > 4 else 200
    n = 0
    for i, ln in enumerate(L, 1):
        m = rx.search(ln)
        if m:
            start = max(0, m.start() - 60)
            out(f"{i}\t{clip(ln[start:], w)}")
            n += 1
            if n >= mx:
                out(f"… (limite {mx})")
                break
elif cmd == "fn":
    name = sys.argv[2]
    mx = int(sys.argv[3]) if len(sys.argv) > 3 else 40
    w = int(sys.argv[4]) if len(sys.argv) > 4 else 240
    rx = re.compile(r"(?:function\s+%s\s*\(|window\.%s\s*=|(?:const|let|var)\s+%s\s*=|^\s*%s\s*=\s*(?:async\s+)?function)" % ((re.escape(name),) * 4))
    for i, ln in enumerate(L, 1):
        if rx.search(ln):
            out(f"===== {name} @ {i}")
            depth, started = 0, False
            for j in range(i, min(i + mx, len(L) + 1)):
                s = L[j - 1]
                out(f"{j}\t{clip(s, w)}")
                depth += s.count("{") - s.count("}")
                started = started or "{" in s
                if started and depth <= 0:
                    break
elif cmd == "ids":
    rx = re.compile(sys.argv[2])
    seen = {}
    idrx = re.compile(r"""(?:\bid\s*=\s*\\?["'`]|\.id\s*=\s*["'`]|getElementById\(\s*["'`]|openModal\(\s*["'`])([\w-]+)""")
    for i, ln in enumerate(L, 1):
        for m in idrx.finditer(ln):
            if rx.search(m.group(1)):
                seen.setdefault(m.group(1), i)
    for k, v in sorted(seen.items(), key=lambda kv: kv[1]):
        out(f"{v}\t{k}")
elif cmd == "cut":
    i = int(sys.argv[2])
    rx = re.compile(sys.argv[3], re.I)
    n = int(sys.argv[4]) if len(sys.argv) > 4 else 1500
    ln = L[i - 1]
    for m in rx.finditer(ln):
        out(f"{i}@{m.start()}\t{ln[m.start():m.start() + n]}")
        break
else:
    out(__doc__)
