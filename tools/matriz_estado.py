"""Actualiza a coluna Estado da matriz de paridade.

  python -I tools/matriz_estado.py <ESTADO> <ID> [<ID> ...]
  ex.: python -I tools/matriz_estado.py "DOM+TST" FIN-01 FIN-02

A coluna Estado é a 8.ª da tabela (ID | Origem | Perfil | Comportamento | Dados | Destino | Teste | Estado | Classe).
"""
import sys
from pathlib import Path

matriz = Path(__file__).resolve().parent.parent / "docs" / "paridade-html-android.md"
estado, ids = sys.argv[1], set(sys.argv[2:])
linhas = matriz.read_text(encoding="utf-8").split("\n")
feitos = set()
for i, ln in enumerate(linhas):
    if not ln.startswith("| "):
        continue
    cel = ln.split(" | ")
    rid = cel[0][2:].strip()
    if rid in ids and len(cel) >= 9:
        cel[7] = estado
        linhas[i] = " | ".join(cel)
        feitos.add(rid)
matriz.write_text("\n".join(linhas), encoding="utf-8")
falta = ids - feitos
print(f"actualizados={len(feitos)}" + (f" não encontrados={sorted(falta)}" if falta else ""))
