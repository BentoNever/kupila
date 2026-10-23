"""Extrai conteúdos estáticos do HTML de origem para ficheiros versionados do APK.

  python -I tools/extrair_conteudos.py

Gera em android/app/src/main/assets/:
  geografia-ao-v1.json   — `administrativeDivisions` (Lei n.º 14/24: 21 províncias, 326 municípios), ordem da origem
  conteudo/faq-v1.json   — perguntas/respostas do FAQ (`renderKupilaFaq`)
  conteudo/politicas-v1.json — secções do manual e das políticas (`modal-kupila-policies`)
"""
import json
import re
from html.parser import HTMLParser
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
SRC = RAIZ / "Kupila%201.0.239.html"
OUT = RAIZ / "android" / "app" / "src" / "main" / "assets"
L = SRC.read_text(encoding="utf-8").split("\n")
texto = "\n".join(L)

# --- Geografia ---------------------------------------------------------------
ini = texto.index("const administrativeDivisions = {")
fim = texto.index("};", ini)
corpo = texto[ini + len("const administrativeDivisions = "):fim + 1]
geo = json.loads(corpo)  # o literal da origem já é JSON válido (chaves e valores entre aspas)
total = sum(len(v) for v in geo.values())
assert len(geo) == 21, len(geo)
assert total == 326, total
OUT.mkdir(parents=True, exist_ok=True)
geo_json = json.dumps({
    "versao": 1,
    "fonte": "Kupila 1.0.239 · administrativeDivisions · Lei n.º 14/24, de 5 de Setembro",
    "provincias": [{"nome": p, "municipios": m} for p, m in geo.items()],
}, ensure_ascii=False, indent=1)
(OUT / "geografia-ao-v1.json").write_text(geo_json, encoding="utf-8")
# A mesma versão para o Central (validação de Província/Município no servidor).
dados_central = RAIZ / "backend" / "functions" / "src" / "dados"
dados_central.mkdir(parents=True, exist_ok=True)
(dados_central / "geografia-ao-v1.json").write_text(geo_json, encoding="utf-8")
print(f"geografia: {len(geo)} províncias, {total} municípios")


# --- Texto visível de um intervalo de linhas -----------------------------------
class Texto(HTMLParser):
    BLOCO = {"p", "li", "h1", "h2", "h3", "h4", "h5", "div", "section", "summary", "br", "tr"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.partes, self.skip = [], 0
        self.titulos = []
        self.em_titulo = None

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style", "button", "i", "svg"):
            self.skip += 1 if tag in ("script", "style", "svg") else 0
        cls = dict(attrs).get("class") or ""
        # Títulos da origem: <h3-5> ou <p class="… font-bold|font-black …">.
        if tag in ("h3", "h4", "h5") or (tag == "p" and re.search(r"\bfont-(bold|black)\b", cls)):
            self.em_titulo = ""
            self.tag_titulo = tag
        if tag in self.BLOCO:
            self.partes.append("\n")

    def handle_endtag(self, tag):
        if tag in ("script", "style", "svg"):
            self.skip = max(0, self.skip - 1)
        if self.em_titulo is not None and tag == getattr(self, "tag_titulo", None):
            self.partes.append(f"\n## {self.em_titulo.strip()}\n")
            self.em_titulo = None
        if tag in self.BLOCO:
            self.partes.append("\n")

    def handle_data(self, d):
        if self.skip:
            return
        if self.em_titulo is not None:
            self.em_titulo += d
            return
        self.partes.append(d)

    def resultado(self):
        t = "".join(self.partes)
        t = re.sub(r"[ \t\r\f\v]+", " ", t)
        t = re.sub(r" *\n *", "\n", t)
        return re.sub(r"\n{2,}", "\n\n", t).strip()


def texto_linhas(a, b):
    p = Texto()
    p.feed("\n".join(L[a - 1:b]))
    return p.resultado()


def seccoes(t):
    out, atual = [], {"titulo": "", "texto": []}
    for ln in t.split("\n"):
        if ln.startswith("## "):
            if atual["titulo"] or atual["texto"]:
                out.append(atual)
            atual = {"titulo": ln[3:].strip(), "texto": []}
        elif ln.strip():
            atual["texto"].append(ln.strip())
    out.append(atual)
    return [{"titulo": s["titulo"], "paragrafos": s["texto"]} for s in out if s["titulo"] or s["texto"]]


def linha_de(rx, desde=1):
    r = re.compile(rx)
    for i in range(desde - 1, len(L)):
        if r.search(L[i]):
            return i + 1
    raise ValueError(rx)


conteudo = OUT / "conteudo"
conteudo.mkdir(exist_ok=True)

# Políticas e manual: do início de modal-kupila-policies até ao modal seguinte.
pa = linha_de(r'id="modal-kupila-policies"')
pb = linha_de(r'id="modal-product-photo-viewer"', pa) - 1
pol = seccoes(texto_linhas(pa, pb))
# Reserva protegida e FAQ estático.
ra = linha_de(r'id="modal-reservation-protection"')
rb = linha_de(r'id="modal-kupila-faq"', ra) - 1
prot = seccoes(texto_linhas(ra, rb))
(conteudo / "politicas-v1.json").write_text(json.dumps({
    "versao": "2026.08.28-4", "fonte": f"Kupila 1.0.239 · linhas {pa}-{pb} e {ra}-{rb}",
    "manualPoliticas": pol, "reservaProtegida": prot,
}, ensure_ascii=False, indent=1), encoding="utf-8")
print(f"políticas: {len(pol)} secções, reserva protegida: {len(prot)} secções")

# FAQ: array de objectos {q,a} dentro de renderKupilaFaq ou constante próxima.
fa = linha_de(r"function renderKupilaFaq")
janela = "\n".join(L[fa - 40:fa + 5])
m = re.search(r"(?:const|let|var)\s+(\w*[Ff][Aa][Qq]\w*)\s*=\s*\[", "\n".join(L[:fa + 5]))
print("faq: candidato", m.group(1) if m else None)
