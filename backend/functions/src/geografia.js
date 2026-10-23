'use strict';
// Catálogo geográfico versionado (extraído da origem por tools/extrair_conteudos.py; igual ao do APK).
const geo = require('./dados/geografia-ao-v1.json');

const mapa = new Map(geo.provincias.map((p) => [p.nome, new Set(p.municipios)]));

function provinciaEMunicipioValidos(provincia, municipio) {
  const m = mapa.get(provincia);
  return Boolean(m && m.has(municipio));
}

module.exports = { provinciaEMunicipioValidos, versao: geo.versao, provincias: () => [...mapa.keys()] };
