"""Lista todas as escritas Firestore do HTML de origem (set/update/add/batch/transaction) por colecção, com a
linha e o excerto a partir da escrita, para reproduzir o esquema exacto no Android (compatibilidade de dados).

  python -I tools/escritas_firestore.py > docs/origem-html/escritas.txt
"""
import re
import sys
from pathlib import Path

SRC = Path(__file__).resolve().parent.parent / "Kupila%201.0.239.html"
L = SRC.read_text(encoding="utf-8").split("\n")
rx = re.compile(r"collection\(\s*['\"`]([\w-]+)['\"`]\s*\)(?:\.doc\(([^)]{0,80})\))?\s*\.(set|update|add|delete)\(|batch\.(set|update|delete)\(\s*db\.collection\(\s*['\"]([\w-]+)['\"]")
for n, ln in enumerate(L, 1):
    for m in rx.finditer(ln):
        col = m.group(1) or m.group(5)
        op = m.group(3) or ("batch." + (m.group(4) or ""))
        trecho = ln[m.start():m.start() + 700].replace("\t", " ")
        sys.stdout.buffer.write(f"{col}\t{op}\t{n}\t{trecho}\n".encode("utf-8"))
