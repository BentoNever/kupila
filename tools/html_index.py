"""Indexa o HTML de origem do Kupila sem o carregar para a conversa.

Gera em docs/origem-html/:
  blocos.tsv      — blocos <script>/<style> (linha inicial/final, tamanho, src)
  funcoes.tsv     — definições de funções (nome, linha, bloco, forma) incluindo redefinições
  redefinicoes.tsv— nomes definidos mais de uma vez (ordem de carregamento = ordem das linhas)
  ids.tsv         — elementos HTML com id (ecrãs, modais, campos), fora dos scripts
  colecoes.tsv    — colecções Firestore referenciadas (collection('x'))

Uso: python -I tools/html_index.py "Kupila%201.0.239.html" docs/origem-html
"""
import re
import sys
from pathlib import Path

src = Path(sys.argv[1])
out = Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
# Só "\n": splitlines() também parte em U+2028 etc. e desalinha as linhas face aos editores.
lines = [ln.rstrip("\r") for ln in src.read_text(encoding="utf-8", errors="replace").split("\n")]

blocks = []  # (kind, start, end, attrs)
cur = None
open_re = re.compile(r"<(script|style)\b([^>]*)>", re.I)
close_re = {"script": re.compile(r"</script>", re.I), "style": re.compile(r"</style>", re.I)}
for i, ln in enumerate(lines, 1):
    if cur is None:
        m = open_re.search(ln)
        if m:
            kind, attrs = m.group(1).lower(), m.group(2).strip()
            rest = ln[m.end():]
            if close_re[kind].search(rest):
                blocks.append((kind, i, i, attrs))
            else:
                cur = (kind, i, attrs)
    else:
        if close_re[cur[0]].search(ln):
            blocks.append((cur[0], cur[1], i, cur[2]))
            cur = None

def block_of(n):
    for idx, (k, s, e, _) in enumerate(blocks):
        if s <= n <= e:
            return idx, k
    return -1, "html"

with (out / "blocos.tsv").open("w", encoding="utf-8") as f:
    f.write("idx\ttipo\tini\tfim\tlinhas\tattrs\n")
    for idx, (k, s, e, a) in enumerate(blocks):
        f.write(f"{idx}\t{k}\t{s}\t{e}\t{e - s + 1}\t{a[:120]}\n")

fn_patterns = [
    ("function", re.compile(r"^\s*(?:async\s+)?function\s*\*?\s*([A-Za-z_$][\w$]*)\s*\(")),
    ("window.x=", re.compile(r"^\s*window\.([A-Za-z_$][\w$]*)\s*=\s*(?:async\s+)?(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)")),
    ("const=fn", re.compile(r"^\s*(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s+)?(?:function\b|\([^)]*\)\s*=>|[A-Za-z_$][\w$]*\s*=>)")),
    ("assign=fn", re.compile(r"^\s*([A-Za-z_$][\w$]*)\s*=\s*(?:async\s+)?function\b")),
]
fns = []
for i, ln in enumerate(lines, 1):
    bidx, kind = block_of(i)
    if kind != "script":
        continue
    for form, rx in fn_patterns:
        m = rx.match(ln)
        if m:
            fns.append((m.group(1), i, bidx, form))
            break

with (out / "funcoes.tsv").open("w", encoding="utf-8") as f:
    f.write("nome\tlinha\tbloco\tforma\n")
    for n, l, b, form in fns:
        f.write(f"{n}\t{l}\t{b}\t{form}\n")

from collections import defaultdict
byname = defaultdict(list)
for n, l, b, form in fns:
    byname[n].append(l)
with (out / "redefinicoes.tsv").open("w", encoding="utf-8") as f:
    f.write("nome\tvezes\tlinhas (última = activa se executada)\n")
    for n, ls in sorted(byname.items(), key=lambda kv: (-len(kv[1]), kv[0])):
        if len(ls) > 1:
            f.write(f"{n}\t{len(ls)}\t{','.join(map(str, ls))}\n")

id_re = re.compile(r"<(\w+)\b[^>]*\bid=\"([^\"]+)\"[^>]*>")
with (out / "ids.tsv").open("w", encoding="utf-8") as f:
    f.write("linha\ttag\tid\tclasse\n")
    for i, ln in enumerate(lines, 1):
        if block_of(i)[1] != "html":
            continue
        for m in id_re.finditer(ln):
            cls = re.search(r'class="([^"]*)"', m.group(0))
            f.write(f"{i}\t{m.group(1)}\t{m.group(2)}\t{cls.group(1)[:60] if cls else ''}\n")

col_re = re.compile(r"collection\(\s*['\"`]([\w-]+)['\"`]")
cols = defaultdict(list)
for i, ln in enumerate(lines, 1):
    for m in col_re.finditer(ln):
        cols[m.group(1)].append(i)
with (out / "colecoes.tsv").open("w", encoding="utf-8") as f:
    f.write("colecao\tocorrencias\tprimeira\tultima\n")
    for c, ls in sorted(cols.items(), key=lambda kv: -len(kv[1])):
        f.write(f"{c}\t{len(ls)}\t{ls[0]}\t{ls[-1]}\n")

print(f"linhas={len(lines)} blocos={len(blocks)} scripts={sum(1 for b in blocks if b[0]=='script')} "
      f"funcoes={len(fns)} nomes_unicos={len(byname)} redefinidos={sum(1 for v in byname.values() if len(v)>1)} "
      f"colecoes={len(cols)}")
