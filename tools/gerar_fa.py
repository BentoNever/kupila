"""Gera Fa.kt com os códigos dos ícones FontAwesome 6.4.0 USADOS no HTML de origem (paridade de ícones).
Uso: python -I tools/gerar_fa.py <all.min.css> <Kupila.html> <saida.kt>"""
import re
import sys

css, html, saida = sys.argv[1:4]
c = open(css, encoding='utf-8').read()
# .fa-house{--fa:"\f015"}  (FA 6.4: variáveis --fa) ou .fa-house:before{content:"\f015"}
mapa = {}
for m in re.finditer(r'((?:\.fa-[a-z0-9-]+(?::before)?,?)+)\{(?:--fa|content):"\\([0-9a-f]+)"', c):
    for nome in re.findall(r'\.fa-([a-z0-9-]+)', m.group(1)):
        mapa.setdefault(nome, m.group(2))
h = open(html, encoding='utf-8').read()
# Extras usados só pelas funções novas pedidas pelo utilizador (modo poupança, actualizador, copiar referência).
EXTRAS = {'copy', 'play', 'pause', 'download', 'leaf', 'lock-open', 'circle-exclamation'}
usados = sorted((set(re.findall(r'fa-([a-z0-9]+(?:-[a-z0-9]+)*)', h)) | EXTRAS) & set(mapa))
ignorar = {'solid', 'regular', 'brands', 'spin', 'pulse', 'fw', 'lg', 'xs', 'sm', 'xl', '2x', '3x', 'beat', 'shake', 'bounce', 'fade', 'flip'}
linhas = []
for n in usados:
    if n in ignorar:
        continue
    k = re.sub(r'[^A-Z0-9]', '_', n.upper())
    if k[0].isdigit():
        k = 'N' + k
    linhas.append(f'    const val {k} = "\\u{int(mapa[n], 16):04x}"' if int(mapa[n], 16) <= 0xFFFF else f'    val {k} = String(Character.toChars(0x{mapa[n]}))')
kt = '''package ao.kupila.gestaonegocios.ui.comum

// GERADO por tools/gerar_fa.py a partir de FontAwesome Free 6.4.0 (SIL OFL 1.1) e dos ícones usados no HTML de origem. Não editar.
object Fa {
''' + '\n'.join(linhas) + '\n}\n'
open(saida, 'w', encoding='utf-8').write(kt)
print(len(linhas), 'ícones')
