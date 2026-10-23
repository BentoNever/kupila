"""Resumo compacto da UI estática do HTML de origem (corpo fora dos <script>/<style>).

Para cada contentor com id (modal-*, tab-*, section, *-overlay) lista:
  H  títulos (h1-h4)          F  campos (input/select/textarea: id, type, placeholder, opções)
  B  botões/links com acção   (texto curto + onclick/onchange)
Uso: python -I tools/html_ui_map.py <ini> <fim>
"""
import re
import sys
from html.parser import HTMLParser
from pathlib import Path

SRC = Path(__file__).resolve().parent.parent / "Kupila%201.0.239.html"
L = SRC.read_text(encoding="utf-8", errors="replace").split("\n")
a, b = int(sys.argv[1]), int(sys.argv[2])
CONTAINER = re.compile(r"^(modal-|tab-|kupila-[\w-]*overlay|payment-step|sim-|settings-|policy-|publish-|testimonial-)")
VOID = {"input", "img", "br", "hr", "meta", "link", "source", "wbr"}


def out(s):
    sys.stdout.buffer.write((s + "\n").encode("utf-8", "replace"))


class P(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []  # (tag, id)
        self.capture = None  # (kind, attrs, text)
        self.select = None
        self.skip = 0

    def ctx(self):
        return "/".join(i for _, i in self.stack if i)

    def handle_starttag(self, tag, attrs):
        d = dict(attrs)
        line = self.getpos()[0] + a - 1
        if tag in ("script", "style"):
            self.skip += 1
            return
        i = d.get("id", "")
        if i and CONTAINER.match(i) and tag in ("div", "section", "form", "aside"):
            out(f"\n[{line}] <{tag} #{i}> {('.' + d.get('class', '')[:50]) if d.get('class') else ''}")
        if tag not in VOID:
            self.stack.append((tag, i if (i and CONTAINER.match(i)) else ""))
        if tag in ("h1", "h2", "h3", "h4"):
            self.capture = ["H", d, "", line]
        elif tag in ("button", "a") and (d.get("onclick") or d.get("href", "").startswith("#")):
            self.capture = ["B", d, "", line]
        elif tag == "label":
            self.capture = ["L", d, "", line]
        elif tag in ("input", "textarea"):
            t = d.get("type", "text") if tag == "input" else "textarea"
            if t == "hidden":
                return
            extra = " ".join(f"{k}={d[k]!r}" for k in ("placeholder", "accept", "min", "max", "step", "maxlength", "required", "onchange", "oninput", "value") if k in d and d[k] is not None)
            out(f"  F {line} {d.get('id', '?')} [{t}] {extra[:160]}")
        elif tag == "select":
            self.select = [d.get("id", "?"), [], line, d.get("onchange", "")]
        elif tag == "option" and self.select is not None:
            self.capture = ["O", d, "", line]

    def handle_endtag(self, tag):
        if tag in ("script", "style"):
            self.skip = max(0, self.skip - 1)
            return
        if self.capture and ((self.capture[0] == "H" and tag in ("h1", "h2", "h3", "h4")) or
                             (self.capture[0] == "B" and tag in ("button", "a")) or
                             (self.capture[0] == "L" and tag == "label") or
                             (self.capture[0] == "O" and tag == "option")):
            k, d, txt, line = self.capture
            txt = re.sub(r"\s+", " ", txt).strip()
            if k == "H":
                out(f"  H {line} {txt[:90]}")
            elif k == "B":
                act = d.get("onclick") or d.get("href")
                out(f"  B {line} {txt[:50]!r} -> {act[:120] if act else ''}")
            elif k == "L" and txt:
                out(f"  L {line} {txt[:90]}")
            elif k == "O":
                self.select[1].append(txt[:40] + (f"={d.get('value')}" if d.get("value") not in (None, txt) else ""))
            self.capture = None
        if tag == "select" and self.select:
            sid, opts, line, ch = self.select
            out(f"  F {line} {sid} [select] {('onchange=' + ch[:80]) if ch else ''} opts: {' | '.join(opts)[:400]}")
            self.select = None
        for j in range(len(self.stack) - 1, -1, -1):
            if self.stack[j][0] == tag:
                del self.stack[j:]
                break

    def handle_data(self, data):
        if self.capture and not self.skip:
            self.capture[2] += data


P().feed("\n".join(L[a - 1:b]))
