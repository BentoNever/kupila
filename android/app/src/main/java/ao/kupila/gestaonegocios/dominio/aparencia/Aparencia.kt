package ao.kupila.gestaonegocios.dominio.aparencia

/** Temas da origem (presets em `kupila-1-0-161-first-paint-theme`, HTML linha 9). Cores em ARGB. */
enum class TemaVisual(val codigoOrigem: String, val nome: String, val primaria: Long, val destaque: Long) {
    KUPILA("EMERALD", "Kupila", 0xFF16A34A, 0xFFFACC15),
    OCEANO("OCEAN", "Oceano", 0xFF0284C7, 0xFF38BDF8),
    VIOLETA("VIOLET", "Violeta", 0xFF7C3AED, 0xFFC4B5FD),
    POR_DO_SOL("SUNSET", "Pôr-do-sol", 0xFFEA580C, 0xFFFBBF24),
    PERSONALIZADO("CUSTOM", "Cor personalizada", 0xFF16A34A, 0xFFFACC15);

    companion object { fun daOrigem(c: String?) = entries.firstOrNull { it.codigoOrigem == c } ?: KUPILA }
}

/** Paletas da Pesquisa/Vitrine (HTML 24670-24675, 1.0.197): sp/sa = pesquisa, hp/ha = vitrine. */
enum class PaletaVitrine(val nome: String, val pesquisaPrimaria: Long, val pesquisaDestaque: Long, val vitrinePrimaria: Long, val vitrineDestaque: Long) {
    AURORA("Aurora", 0xFF0F766E, 0xFF2DD4BF, 0xFF4338CA, 0xFFA78BFA),
    SAFIRA("Safira", 0xFF075985, 0xFF38BDF8, 0xFF1E3A8A, 0xFF60A5FA),
    LUXO("Luxo", 0xFF713F12, 0xFFF59E0B, 0xFF3F3F46, 0xFFD4AF37),
    CORAL("Coral", 0xFFBE123C, 0xFFFB7185, 0xFF7C3AED, 0xFFC084FC);

    companion object { fun daOrigem(c: String?) = entries.firstOrNull { it.name == c } ?: AURORA }
}

/** Claro / escuro / automático (`visualAppearance`). */
enum class ModoAparencia(val codigoOrigem: String) { CLARO("LIGHT"), ESCURO("DARK"), AUTOMATICO("AUTO") }

/**
 * Identidade visual vigente da plataforma, definida pelo Administrador e guardada localmente com versão
 * para aplicar já no 1.º frame (login e offline). O cliente nunca a altera para os outros.
 */
data class IdentidadeVisual(
    val versao: Long = 0,
    val tema: TemaVisual = TemaVisual.KUPILA,
    val primaria: Long = TemaVisual.KUPILA.primaria,
    val destaque: Long = TemaVisual.KUPILA.destaque,
    val vitrine: PaletaVitrine = PaletaVitrine.AURORA,
    val pesquisaPrimaria: Long = PaletaVitrine.AURORA.pesquisaPrimaria,
    val pesquisaDestaque: Long = PaletaVitrine.AURORA.pesquisaDestaque,
    val vitrinePrimaria: Long = PaletaVitrine.AURORA.vitrinePrimaria,
    val vitrineDestaque: Long = PaletaVitrine.AURORA.vitrineDestaque,
    val modoPlataforma: ModoAparencia = ModoAparencia.CLARO,
    /** Caminho local da cópia do logótipo configurado (null = marca "K" padrão). */
    val logoLocal: String? = null,
)
