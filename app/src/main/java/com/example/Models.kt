package com.example

import com.squareup.moshi.JsonClass

/**
 * Representa os perfis disponíveis de publicadores na congregação.
 */
enum class PerfilPublicador {
    ANCIAO,
    SERVO_MINISTERIAL,
    IRMAO_BATIZADO,
    IRMAO_NAO_BATIZADO,
    IRMA
}

/**
 * Representa o gênero do publicador.
 */
enum class Genero {
    MASCULINO,
    FEMININO
}

/**
 * Representa o grau de parentesco de primeiro grau.
 */
enum class GrauParentesco {
    CONJUGE,
    PAI_FILHO, // Pai/Filha ou Mãe/Filho
    IRMAO_IRMA
}

/**
 * Estrutura para descrever relacionamentos familiares no banco de dados.
 */
@JsonClass(generateAdapter = true)
data class RelacaoParentesco(
    val parenteId: Int,
    val grau: GrauParentesco
)

/**
 * Representa o histórico de designações de um publicador.
 */
@JsonClass(generateAdapter = true)
data class RegistroHistorico(
    val semana: String,        // Formato "YYYY-WW" ou data da reunião
    val tipoParte: String,     // Ex: "TESOUROS_DISCURSO", "MINISTERIO_PARTE_1"
    val papel: String,         // Ex: "APRESENTADOR", "AJUDANTE", "DIRIGENTE", "LEITOR"
    val parceiroId: Int?       // Complemento de dupla se aplicável
)

/**
 * Representa o publicador cadastrado.
 */
@JsonClass(generateAdapter = true)
data class Publicador(
    val id: Int = 0,
    val nome: String,
    val genero: Genero,
    val perfil: PerfilPublicador,
    val servoDirigenteAprovado: Boolean = false, // Válido para Servos Ministeriais conduzirem o Estudo
    val parentescos: List<RelacaoParentesco> = emptyList(),
    val historicoPartes: List<RegistroHistorico> = emptyList()
)

/**
 * Formato ou tipo de apresentação de estudante no meio de semana.
 */
enum class FormatoParteEstudante {
    DEMONSTRACAO,
    DISCURSO,
    EXPLICANDO_CRENCAS_DISCURSO
}

/**
 * Parte de estudante definida na reunião.
 */
@JsonClass(generateAdapter = true)
data class ParteEstudanteConfig(
    val id: String,                  // Ex: "MINISTERIO_PARTE_1"
    val nomeAmigavel: String,         // Ex: "Iniciando Conversas (Vídeo)"
    val formato: FormatoParteEstudante
)

/**
 * Representa a programação preenchida de uma congregação em determinada semana.
 */
@JsonClass(generateAdapter = true)
data class ProgramacaoSemana(
    val semana: String, // Data da reunião, e.g., "2026-06-01"
    
    // Cabeçalho Geral (Card de Topo)
    val presidenteId: Int? = null,
    val oracaoInicialId: Int? = null,
    
    // Tesouros da Palavra de Deus
    val tesourosDiscursoId: Int? = null,
    val tesourosJoiasId: Int? = null,
    val tesourosLeituraId: Int? = null,
    
    // Faça Seu Melhor no Ministério (Designações de Estudantes)
    val estudante1ApresentadorId: Int? = null,
    val estudante1AjudanteId: Int? = null,
    val estudante1Formato: FormatoParteEstudante = FormatoParteEstudante.DEMONSTRACAO,
    
    val estudante2ApresentadorId: Int? = null,
    val estudante2AjudanteId: Int? = null,
    val estudante2Formato: FormatoParteEstudante = FormatoParteEstudante.DEMONSTRACAO,
    
    val estudante3ApresentadorId: Int? = null,
    val estudante3AjudanteId: Int? = null,
    val estudante3Formato: FormatoParteEstudante = FormatoParteEstudante.DEMONSTRACAO,

    val estudante4ApresentadorId: Int? = null,
    val estudante4AjudanteId: Int? = null,
    val estudante4Formato: FormatoParteEstudante = FormatoParteEstudante.DEMONSTRACAO,

    val facaSeuMelhorOpcao: String = "3",
    val facaSeuMelhorCardCountCustom: Int = 1,
    val facaSeuMelhorCard1Tema: String = "Iniciando conversas",
    val facaSeuMelhorCard2Tema: String = "Cultivando o interesse",
    val facaSeuMelhorCard3Tema: String = "Fazendo discípulos",
    val facaSeuMelhorCard4Tema: String = "Explicando suas crenças",
    
    // Nossa Vida Cristã
    val vidaPartesQuantidade: Int = 1,
    val vidaParteLocal1Id: Int? = null,
    val vidaParteLocal1DuracaoMin: Int = 15,
    val vidaParteLocalLocalExclusivoAnciao: Boolean = false, // Se marcado de "Necessidades Locais" / "Contas"
    val vidaParteLocal1Tema: String = "Parte Local 1",

    val vidaParteLocal2Id: Int? = null,
    val vidaParteLocal2DuracaoMin: Int = 15,
    val vidaParteLocal2ExclusivoAnciao: Boolean = false,
    val vidaParteLocal2Tema: String = "Parte Local 2",
    
    val vidaEstudoDirigenteId: Int? = null,
    val vidaEstudoLeitorId: Int? = null,
    
    // Rodapé Geral
    val oracaoFinalId: Int? = null
)

/**
 * Matriz de Perfis: Mapeia cada tipo de parte para a lista de Perfis que podem realizá-la.
 */
@JsonClass(generateAdapter = true)
data class MatrizPermissoes(
    val allowedDiscursoJoias: List<PerfilPublicador> = listOf(PerfilPublicador.ANCIAO, PerfilPublicador.SERVO_MINISTERIAL),
    val allowedLeituraBiblia: List<PerfilPublicador> = listOf(PerfilPublicador.SERVO_MINISTERIAL, PerfilPublicador.IRMAO_BATIZADO, PerfilPublicador.IRMAO_NAO_BATIZADO),
    val allowedEstudanteApresentador: List<PerfilPublicador> = listOf(PerfilPublicador.IRMAO_BATIZADO, PerfilPublicador.IRMAO_NAO_BATIZADO, PerfilPublicador.IRMA),
    val allowedEstudanteDiscurso: List<PerfilPublicador> = listOf(PerfilPublicador.IRMAO_BATIZADO, PerfilPublicador.IRMAO_NAO_BATIZADO),
    val allowedDirigenteEstudo: List<PerfilPublicador> = listOf(PerfilPublicador.ANCIAO, PerfilPublicador.SERVO_MINISTERIAL),
    val allowedLeitorEstudo: List<PerfilPublicador> = listOf(PerfilPublicador.SERVO_MINISTERIAL, PerfilPublicador.IRMAO_BATIZADO)
)

/**
 * Estrutura do Painel de Gerenciamento de Regras do AppVM.
 */
@JsonClass(generateAdapter = true)
data class PainelRegrasConfig(
    // Chaves Booleanas de Regras de Distribuição
    val permitirAcumulo: Boolean = true,                     // Um publicador pode ter duas designações na mesma noite (ex: Leitura + Leitor do Estudo)
    val permitirSemanasConsecutivasTribuna: Boolean = true,  // Anciãos/Servos podem fazer partes consecutivas devido ao número de irmãos
    val alternarPapeisEstudante: Boolean = true,             // Estreitar prioridade para quem foi apresentador ser ajudante da próxima vez e vice-versa
    val evitarDuplaEstudoRepetidaAnterior: Boolean = true,   // Evitar repetir o par Dirigente+Leitor do Estudo da semana anterior
    
    // Matriz de Privilégios Dinâmica
    val matriz: MatrizPermissoes = MatrizPermissoes()
)
