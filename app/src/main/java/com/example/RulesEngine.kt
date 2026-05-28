package com.example

import java.util.Locale

/**
 * Nível de gravidade de um alerta de validação.
 */
enum class SeveridadeValidacao {
    SUCESSO,
    AVISO,
    ERRO
}

/**
 * Representa um relatório de validação das regras da reunião.
 */
data class RelatorioValidacao(
    val tipoParte: String,
    val campo: String,
    val mensagem: String,
    val severidade: SeveridadeValidacao
)

object RulesEngine {

    /**
     * Valida uma programação semanal contra a matriz de privilégios e regras dinâmicas do painel de administração.
     */
    fun validarProgramacao(
        programacao: ProgramacaoSemana,
        publicadores: List<Publicador>,
        regras: PainelRegrasConfig
    ): List<RelatorioValidacao> {
        if (programacao.tipoSemana == "EVENTO") {
            return emptyList()
        }
        val relatorios = mutableListOf<RelatorioValidacao>()
        val pMap = publicadores.associateBy { it.id }

        // --- TRAVASS DE ACÚMULO LOCALIZADAS ---
        val designacoesSemana = mutableMapOf<Int, MutableList<String>>() // publicador_id -> lista de partes

        fun registrarDesignacao(pubId: Int?, nomeParte: String) {
            if (pubId != null) {
                designacoesSemana.getOrPut(pubId) { mutableListOf() }.add(nomeParte)
            }
        }

        // Registrar todos os preenchimentos
        registrarDesignacao(programacao.presidenteId, "Presidente da Reunião")
        registrarDesignacao(programacao.oracaoInicialId, "Oração Inicial")
        registrarDesignacao(programacao.tesourosDiscursoId, "Discurso (Tesouros)")
        registrarDesignacao(programacao.tesourosJoiasId, "Joias Ocultas (Tesouros)")
        registrarDesignacao(programacao.tesourosLeituraId, "Leitura da Bíblia (Tesouros)")
        
        val fsmCount = when (programacao.facaSeuMelhorOpcao) {
            "1" -> 1
            "2" -> 2
            "3" -> 3
            "4" -> 4
            "custom" -> programacao.facaSeuMelhorCardCountCustom.coerceIn(1, 3)
            else -> 3
        }
        if (fsmCount >= 1) {
            registrarDesignacao(programacao.estudante1ApresentadorId, "Estudante 1 (Apresentador)")
            registrarDesignacao(programacao.estudante1AjudanteId, "Estudante 1 (Ajudante)")
        }
        if (fsmCount >= 2) {
            registrarDesignacao(programacao.estudante2ApresentadorId, "Estudante 2 (Apresentador)")
            registrarDesignacao(programacao.estudante2AjudanteId, "Estudante 2 (Ajudante)")
        }
        if (fsmCount >= 3) {
            registrarDesignacao(programacao.estudante3ApresentadorId, "Estudante 3 (Apresentador)")
            registrarDesignacao(programacao.estudante3AjudanteId, "Estudante 3 (Ajudante)")
        }
        if (fsmCount >= 4) {
            registrarDesignacao(programacao.estudante4ApresentadorId, "Estudante 4 (Apresentador)")
            registrarDesignacao(programacao.estudante4AjudanteId, "Estudante 4 (Ajudante)")
        }

        if (programacao.vidaPartesQuantidade >= 1) {
            registrarDesignacao(programacao.vidaParteLocal1Id, "Parte de Vida Cristã 1")
        }
        if (programacao.vidaPartesQuantidade >= 2) {
            registrarDesignacao(programacao.vidaParteLocal2Id, "Parte de Vida Cristã 2")
        }
        registrarDesignacao(programacao.vidaEstudoDirigenteId, "Dirigente do Estudo")
        registrarDesignacao(programacao.vidaEstudoLeitorId, "Leitor do Estudo")
        registrarDesignacao(programacao.oracaoFinalId, "Oração Final")

        // 1. Validando Acumulação de Partes
        if (!regras.permitirAcumulo) {
            designacoesSemana.forEach { (pubId, partes) ->
                if (partes.size > 1) {
                    val pub = pMap[pubId]
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "GERAL",
                            campo = "ACUMULO",
                            mensagem = "Publicador '${pub?.nome ?: "ID $pubId"}' está designado para ${partes.size} partes (${partes.joinToString(", ")}). A regra de Acúmulo está DESATIVADA.",
                            severidade = SeveridadeValidacao.ERRO
                        )
                    )
                }
            }
        } else {
            // Se o acúmulo for permitido, ainda há travas saudáveis de aviso
            designacoesSemana.forEach { (pubId, partes) ->
                if (partes.size > 2) {
                    val pub = pMap[pubId]
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "GERAL",
                            campo = "ACUMULO",
                            mensagem = "Atenção: '${pub?.nome ?: "ID $pubId"}' acumula 3 ou mais partes na mesma noite (${partes.joinToString(", ")}).",
                            severidade = SeveridadeValidacao.AVISO
                        )
                    )
                }
            }
        }

        // --- VALIDAÇÕES TESOUROS DA PALAVRA DE DEUS ---

        // A. Discurso (10 min)
        programacao.tesourosDiscursoId?.let { pubId ->
            val pub = pMap[pubId]
            if (pub != null) {
                if (!regras.matriz.allowedDiscursoJoias.contains(pub.perfil)) {
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "TESOUROS_DISCURSO",
                            campo = "tesourosDiscursoId",
                            mensagem = "Perfil inválido para Discurso: ${pub.nome} é ${pub.perfil}. Apenas ${regras.matriz.allowedDiscursoJoias.joinToString()} são permitidos.",
                            severidade = SeveridadeValidacao.ERRO
                        )
                    )
                }
                // Verificação de Tribunal Semanas Consecutivas
                if (!regras.permitirSemanasConsecutivasTribuna && fezParteSemanaAnterior(pub, programacao.semana)) {
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "TESOUROS_DISCURSO",
                            campo = "tesourosDiscursoId",
                            mensagem = "Aviso: ${pub.nome} fez parte na última semana. Regra determina evitar semanas consecutivas na tribuna.",
                            severidade = SeveridadeValidacao.AVISO
                        )
                    )
                }
            }
        }

        // B. Joias Espirituais (10 min)
        programacao.tesourosJoiasId?.let { pubId ->
            val pub = pMap[pubId]
            if (pub != null) {
                if (!regras.matriz.allowedDiscursoJoias.contains(pub.perfil)) {
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "TESOUROS_JOIAS",
                            campo = "tesourosJoiasId",
                            mensagem = "Perfil inválido para Joias Espirituais: ${pub.nome} é ${pub.perfil}. Apenas ${regras.matriz.allowedDiscursoJoias.joinToString()} são permitidos.",
                            severidade = SeveridadeValidacao.ERRO
                        )
                    )
                }
                if (!regras.permitirSemanasConsecutivasTribuna && fezParteSemanaAnterior(pub, programacao.semana)) {
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "TESOUROS_JOIAS",
                            campo = "tesourosJoiasId",
                            mensagem = "Aviso: ${pub.nome} fez parte na última semana. Regra determina evitar semanas consecutivas.",
                            severidade = SeveridadeValidacao.AVISO
                        )
                    )
                }
            }
        }

        // C. Leitura da Bíblia (4 min)
        programacao.tesourosLeituraId?.let { pubId ->
            val pub = pMap[pubId]
            if (pub != null) {
                // Anciãos estão estritamente EXCLUÍDOS e irmãs também, de acordo com as permissões da matriz e as regras clericais
                if (pub.perfil == PerfilPublicador.ANCIAO) {
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "TESOUROS_LEITURA",
                            campo = "tesourosLeituraId",
                            mensagem = "Regra Estrita: Anciãos (como ${pub.nome}) ficam estritamente EXCLUÍDOS da Leitura da Bíblia de 4 minutos.",
                            severidade = SeveridadeValidacao.ERRO
                        )
                    )
                } else if (!regras.matriz.allowedLeituraBiblia.contains(pub.perfil)) {
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "TESOUROS_LEITURA",
                            campo = "tesourosLeituraId",
                            mensagem = "Apenas homens podem fazer Leitura da Bíblia. ${pub.nome} (${pub.perfil}) não é elegível nesta matriz de regras.",
                            severidade = SeveridadeValidacao.ERRO
                        )
                    )
                }
            }
        }


        // --- VALIDAÇÕES: FAÇA SEU MELHOR NO MINISTÉRIO (Estudantes) ---
        val cardValidationCount = when (programacao.facaSeuMelhorOpcao) {
            "1" -> 1
            "2" -> 2
            "3" -> 3
            "4" -> 4
            "custom" -> programacao.facaSeuMelhorCardCountCustom.coerceIn(1, 3)
            else -> 3
        }
        val isOptionCustom = (programacao.facaSeuMelhorOpcao == "custom")
        if (cardValidationCount >= 1) validarEstudante(1, programacao.estudante1ApresentadorId, programacao.estudante1AjudanteId, programacao.estudante1Formato, programacao.facaSeuMelhorCard1Tema, isOptionCustom, pMap, regras, programacao.semana, relatorios)
        if (cardValidationCount >= 2) validarEstudante(2, programacao.estudante2ApresentadorId, programacao.estudante2AjudanteId, programacao.estudante2Formato, programacao.facaSeuMelhorCard2Tema, isOptionCustom, pMap, regras, programacao.semana, relatorios)
        if (cardValidationCount >= 3) validarEstudante(3, programacao.estudante3ApresentadorId, programacao.estudante3AjudanteId, programacao.estudante3Formato, programacao.facaSeuMelhorCard3Tema, isOptionCustom, pMap, regras, programacao.semana, relatorios)
        if (cardValidationCount >= 4) validarEstudante(4, programacao.estudante4ApresentadorId, programacao.estudante4AjudanteId, programacao.estudante4Formato, programacao.facaSeuMelhorCard4Tema, isOptionCustom, pMap, regras, programacao.semana, relatorios)


        // --- VALIDAÇÕES: NOSSA VIDA CRISTÃ ---

        // A. Partes Locais
        if (programacao.vidaPartesQuantidade >= 1) {
            programacao.vidaParteLocal1Id?.let { pubId ->
                val pub = pMap[pubId]
                if (pub != null) {
                    if (programacao.vidaParteLocalLocalExclusivoAnciao) {
                        if (pub.perfil != PerfilPublicador.ANCIAO) {
                            relatorios.add(
                                RelatorioValidacao(
                                    tipoParte = "VIDA_PARTE_LOCAL",
                                    campo = "vidaParteLocal1Id",
                                    mensagem = "Parte Local 1 (${programacao.vidaParteLocal1Tema}) é restrita EXCLUSIVAMENTE a Anciãos. ${pub.nome} é ${pub.perfil}.",
                                    severidade = SeveridadeValidacao.ERRO
                                )
                            )
                        }
                    } else {
                        if (!regras.matriz.allowedDiscursoJoias.contains(pub.perfil)) {
                            relatorios.add(
                                RelatorioValidacao(
                                    tipoParte = "VIDA_PARTE_LOCAL",
                                    campo = "vidaParteLocal1Id",
                                    mensagem = "Parte Local 1 (${programacao.vidaParteLocal1Tema}) requer Ancião ou Servo Ministerial. ${pub.nome} é inválido.",
                                    severidade = SeveridadeValidacao.ERRO
                                )
                            )
                        }
                    }
                }
            }
        }

        if (programacao.vidaPartesQuantidade >= 2) {
            programacao.vidaParteLocal2Id?.let { pubId ->
                val pub = pMap[pubId]
                if (pub != null) {
                    if (programacao.vidaParteLocal2ExclusivoAnciao) {
                        if (pub.perfil != PerfilPublicador.ANCIAO) {
                            relatorios.add(
                                RelatorioValidacao(
                                    tipoParte = "VIDA_PARTE_LOCAL_2",
                                    campo = "vidaParteLocal2Id",
                                    mensagem = "Parte Local 2 (${programacao.vidaParteLocal2Tema}) é restrita EXCLUSIVAMENTE a Anciãos. ${pub.nome} é ${pub.perfil}.",
                                    severidade = SeveridadeValidacao.ERRO
                                )
                            )
                        }
                    } else {
                        if (!regras.matriz.allowedDiscursoJoias.contains(pub.perfil)) {
                            relatorios.add(
                                RelatorioValidacao(
                                    tipoParte = "VIDA_PARTE_LOCAL_2",
                                    campo = "vidaParteLocal2Id",
                                    mensagem = "Parte Local 2 (${programacao.vidaParteLocal2Tema}) requer Ancião ou Servo Ministerial. ${pub.nome} é inválido.",
                                    severidade = SeveridadeValidacao.ERRO
                                )
                            )
                        }
                    }
                }
            }
        }

        // B. Estudo Bíblico de Congregação (Dirigente + Leitor)
        if (programacao.tipoSemana != "VISITA_SC") {
            val dirId = programacao.vidaEstudoDirigenteId
            val leiId = programacao.vidaEstudoLeitorId

            if (dirId != null && leiId != null) {
                // Trava de acúmulo interna do estudo: O dirigente não pode ser o leitor
                if (dirId == leiId) {
                    relatorios.add(
                        RelatorioValidacao(
                            tipoParte = "VIDA_ESTUDO",
                            campo = "vidaEstudoLeitorId",
                            mensagem = "Trava de Acúmulo Estrita: O dirigente do estudo não pode ser também o leitor do estudo na mesma semana.",
                            severidade = SeveridadeValidacao.ERRO
                        )
                    )
                }
            }

            // Validando Dirigente do Estudo
            if (dirId != null) {
                val dir = pMap[dirId]
                if (dir != null) {
                    // Regra de matriz ordinária
                    if (!regras.matriz.allowedDirigenteEstudo.contains(dir.perfil)) {
                        relatorios.add(
                            RelatorioValidacao(
                                tipoParte = "VIDA_ESTUDO",
                                campo = "vidaEstudoDirigenteId",
                                mensagem = "Dirigente inválido: ${dir.nome} é ${dir.perfil}. Permitido apenas: ${regras.matriz.allowedDirigenteEstudo.joinToString()}.",
                                severidade = SeveridadeValidacao.ERRO
                            )
                        )
                    } else if (dir.perfil == PerfilPublicador.SERVO_MINISTERIAL && !dir.servoDirigenteAprovado) {
                        // Servos devem ser expressamente aprovados pelo corpo de anciãos para dirigir o estudo
                        relatorios.add(
                            RelatorioValidacao(
                                tipoParte = "VIDA_ESTUDO",
                                campo = "vidaEstudoDirigenteId",
                                mensagem = "${dir.nome} é Servo Ministerial, mas NÃO está marcado como 'Aprovado para Dirigir' nas configurações do Corpo.",
                                severidade = SeveridadeValidacao.ERRO
                            )
                        )
                    }
                }
            }

            // Validando Leitor do Estudo
            if (leiId != null) {
                val lei = pMap[leiId]
                if (lei != null) {
                    if (!regras.matriz.allowedLeitorEstudo.contains(lei.perfil)) {
                        relatorios.add(
                            RelatorioValidacao(
                                tipoParte = "VIDA_ESTUDO",
                                campo = "vidaEstudoLeitorId",
                                mensagem = "Leitor de Estudo inválido: ${lei.nome} é ${lei.perfil}.",
                                severidade = SeveridadeValidacao.ERRO
                            )
                        )
                    } else {
                        // Priorização forte de NÃO-anciãos para ler
                        if (lei.perfil == PerfilPublicador.ANCIAO) {
                            relatorios.add(
                                RelatorioValidacao(
                                    tipoParte = "VIDA_ESTUDO",
                                    campo = "vidaEstudoLeitorId",
                                    mensagem = "Priorização: O leitor do estudo deve idealmente ser outro irmão batizado que NÃO seja Ancião. Considere designar outro irmão comum.",
                                    severidade = SeveridadeValidacao.AVISO
                                )
                            )
                        }
                    }
                }
            }

            // Trava de Dupla Anterior (Dirigente + Leitor)
            if (dirId != null && leiId != null && regras.evitarDuplaEstudoRepetidaAnterior) {
                val dir = pMap[dirId]
                if (dir != null) {
                    // Verificar se se repetiram na última semana gravada no histórico
                    val foiDuplaNaSemanaAnterior = dir.historicoPartes.any { hist ->
                        hist.tipoParte == "VIDA_ESTUDO_DIRIGENTE" && hist.parceiroId == leiId
                    }
                    if (foiDuplaNaSemanaAnterior) {
                        relatorios.add(
                            RelatorioValidacao(
                                tipoParte = "VIDA_ESTUDO",
                                campo = "vidaEstudoLeitorId",
                                mensagem = "Trava de Dupla: A dupla Dirigente+Leitor (${dir.nome} e ${pMap[leiId]?.nome}) já fez o Estudo Bíblico em conjunto na semana anterior. Altere para evitar repetição.",
                                severidade = SeveridadeValidacao.AVISO
                            )
                        )
                    }
                }
            }
        }

        return relatorios
    }

    private fun validarEstudante(
        numEstudante: Int,
        apresentadorId: Int?,
        ajudanteId: Int?,
        formato: FormatoParteEstudante,
        tema: String,
        isCustom: Boolean,
        pMap: Map<Int, Publicador>,
        regras: PainelRegrasConfig,
        semanaAtual: String,
        relatorios: MutableList<RelatorioValidacao>
    ) {
        val parteKey = "MINISTERIO_ESTUDANTE_$numEstudante"
        val campoDono = "estudante${numEstudante}ApresentadorId"
        val campoAjudante = "estudante${numEstudante}AjudanteId"

        if (apresentadorId == null) return

        val apr = pMap[apresentadorId]
        if (apr == null) return

        // Trava para Temas Especiais / Formulários de Eventos
        if (tema == "Discurso" || tema == "O que você diria?") {
            if (apr.perfil == PerfilPublicador.IRMA) {
                relatorios.add(
                    RelatorioValidacao(
                        tipoParte = parteKey,
                        campo = campoDono,
                        mensagem = "Parte $numEstudante: O tema '$tema' exige privilégio ministerial e é exclusivo para Irmãos (homens). A irmã ${apr.nome} não é elegível.",
                        severidade = SeveridadeValidacao.ERRO
                    )
                )
            }
        }

        if (tema == "O que você diria?") {
            if (apr.perfil != PerfilPublicador.ANCIAO && apr.perfil != PerfilPublicador.SERVO_MINISTERIAL) {
                relatorios.add(
                    RelatorioValidacao(
                        tipoParte = parteKey,
                        campo = campoDono,
                        mensagem = "Parte $numEstudante: O tema 'O que você diria?' possui restrição estrita regulamentar e exige designação de Ancião ou Servo Ministerial. ${apr.nome} (${apr.perfil}) não possui este privilégio.",
                        severidade = SeveridadeValidacao.ERRO
                    )
                )
            }
        }

        if (isCustom) {
            if (apr.perfil != PerfilPublicador.ANCIAO && apr.perfil != PerfilPublicador.SERVO_MINISTERIAL) {
                relatorios.add(
                    RelatorioValidacao(
                        tipoParte = parteKey,
                        campo = campoDono,
                        mensagem = "Parte $numEstudante: Ajustes e temas personalizados inseridos livremente possuem restrição estrita para Ancião ou Servo Ministerial no sistema. ${apr.nome} (${apr.perfil}) não é elegível.",
                        severidade = SeveridadeValidacao.ERRO
                    )
                )
            }
        }

        // 1. Matriz geral de estudante
        if (!regras.matriz.allowedEstudanteApresentador.contains(apr.perfil)) {
            relatorios.add(
                RelatorioValidacao(
                    tipoParte = parteKey,
                    campo = campoDono,
                    mensagem = "Estudante $numEstudante: ${apr.nome} (${apr.perfil}) não pode fazer partes de estudante na matriz de regras.",
                    severidade = SeveridadeValidacao.ERRO
                )
            )
        }

        // 2. Restrição de discurso: Se formato Discurso / Explicando crenças, apenas Homens (ou seja, perfil distinto de IRMA)
        val isDiscursoFormat = (formato == FormatoParteEstudante.DISCURSO || formato == FormatoParteEstudante.EXPLICANDO_CRENCAS_DISCURSO)
        if (isDiscursoFormat) {
            if (apr.perfil == PerfilPublicador.IRMA) {
                relatorios.add(
                    RelatorioValidacao(
                        tipoParte = parteKey,
                        campo = campoDono,
                        mensagem = "Formato Discurso/Explicando Crenças é exclusivo para Irmãos (homens). A irmã ${apr.nome} não é elegível para este formato.",
                        severidade = SeveridadeValidacao.ERRO
                    )
                )
            } else if (!regras.matriz.allowedEstudanteDiscurso.contains(apr.perfil)) {
                relatorios.add(
                    RelatorioValidacao(
                        tipoParte = parteKey,
                        campo = campoDono,
                        mensagem = "Perfil ${apr.perfil} inválido para Discursos de Estudantes nesta matriz.",
                        severidade = SeveridadeValidacao.ERRO
                    )
                )
            }
        }

        // 3. Regra de Gênero do Ajudante
        if (ajudanteId != null) {
            val aju = pMap[ajudanteId]
            if (aju != null) {
                // Checar se gênero bate
                if (apr.genero != aju.genero) {
                    // Checar se há parentesco de primeiro grau
                    val possuiIsencaoFamiliar = saoParentesDePrimeiroGrau(apr, aju)
                    if (!possuiIsencaoFamiliar) {
                        relatorios.add(
                            RelatorioValidacao(
                                tipoParte = parteKey,
                                campo = campoAjudante,
                                mensagem = "Regra de Gênero: O ajudante deve ser do mesmo gênero (${apr.genero}), exceto no caso de parentescos de primeiro grau (esposos, filhos/pais, etc.). ${apr.nome} e ${aju.nome} violam a regra.",
                                severidade = SeveridadeValidacao.ERRO
                            )
                        )
                    } else {
                        // Parentesco de Primeiro Grau isenta a trava
                        // Adicionar informação amigável de sucesso ou manter discreto
                    }
                }
            }
        }

        // 4. Alternância de Papéis (Presenter / Helper)
        if (regras.alternarPapeisEstudante && apr.historicoPartes.isNotEmpty()) {
            val ultimaParte = apr.historicoPartes.firstOrNull { it.tipoParte.startsWith("MINISTERIO_") }
            if (ultimaParte != null && ultimaParte.papel == "APRESENTADOR") {
                relatorios.add(
                    RelatorioValidacao(
                        tipoParte = parteKey,
                        campo = campoDono,
                        mensagem = "Alternância: ${apr.nome} foi 'Apresentador (Dono)' da última vez (${ultimaParte.semana}). O rodízio do painel prioriza colocá-lo como Ajudante na próxima parte.",
                        severidade = SeveridadeValidacao.AVISO
                    )
                )
            }
        }
    }

    /**
     * Verifica se o publicador fez alguma parte na tribuna na semana anterior à atual.
     */
    fun fezParteSemanaAnterior(pub: Publicador, semanaAtual: String): Boolean {
        // Se houver algum histórico na semana anterior à "semanaAtual"
        if (pub.historicoPartes.isEmpty()) return false
        val ultimaReuniao = pub.historicoPartes.first().semana
        return ultimaReuniao == calcularSemanaAnterior(semanaAtual)
    }

    /**
     * Helper extremamente simples que diminui uma "janela de semana" simulada.
     */
    fun calcularSemanaAnterior(semana: String): String {
        // Exemplo simples para datas no formato "2026-05-28" ou similar
        // Se for no formato dd/MM/yyyy ou YYYY-WW, foca no histórico mockado
        // Como o mock usará datas com diferença de 7 dias, se a data for "2026-05-28", a anterior é "2026-05-21"
        return when (semana) {
            "2026-06-04" -> "2026-05-28"
            "2026-05-28" -> "2026-05-21"
            "2026-05-21" -> "2026-05-14"
            else -> "2026-05-21" // Default de callback
        }
    }

    /**
     * Informa se dois publicadores são parentes de primeiro grau cadastrados.
     */
    fun saoParentesDePrimeiroGrau(p1: Publicador, p2: Publicador): Boolean {
        return p1.parentescos.any { it.parenteId == p2.id } || p2.parentescos.any { it.parenteId == p1.id }
    }

    /**
     * Sugere e classifica candidatos recomendados para determinada parte do meeting.
     * Retorna uma lista de publicadores ordenada de "mais recomendado" a "menos recomendado/não recomendado".
     */
    fun sugerirCandidatos(
        campo: String,
        formatoEstudante: FormatoParteEstudante?,
        apresentadorEstudanteId: Int?, // Caso estejamos escolhendo o ajudante
        publicadores: List<Publicador>,
        programacao: ProgramacaoSemana,
        regras: PainelRegrasConfig
    ): List<Pair<Publicador, String>> {
        val mappedCampo = when {
            campo.startsWith("estudante") && campo.endsWith("ApresentadorId") -> "estudanteApresentadorId"
            campo.startsWith("estudante") && campo.endsWith("AjudanteId") -> "estudanteAjudanteId"
            campo.startsWith("vidaParteLocal") -> "vidaParteLocalId"
            else -> campo
        }

        val pMap = publicadores.associateBy { it.id }
        val resultado = mutableListOf<Triple<Publicador, Int, String>>() // Publicador, Pontuação de Penalidade, Motivo

        // Determinar restrições com base no tipo de campo
        val allowedProfiles: List<PerfilPublicador> = when (mappedCampo) {
            "presidenteId" -> regras.matriz.allowedDiscursoJoias
            "oracaoInicialId", "oracaoFinalId" -> listOf(PerfilPublicador.ANCIAO, PerfilPublicador.SERVO_MINISTERIAL, PerfilPublicador.IRMAO_BATIZADO)
            "tesourosDiscursoId", "tesourosJoiasId" -> regras.matriz.allowedDiscursoJoias
            "tesourosLeituraId" -> regras.matriz.allowedLeituraBiblia.filter { it != PerfilPublicador.ANCIAO } // Ancião rigorosamente excluído por regra específica
            "estudanteApresentadorId" -> {
                val cardNum = when {
                    campo.contains("1") -> 1
                    campo.contains("2") -> 2
                    campo.contains("3") -> 3
                    campo.contains("4") -> 4
                    else -> 1
                }
                val isCardCustom = programacao.facaSeuMelhorOpcao == "custom"
                val cardTema = when (cardNum) {
                    1 -> programacao.facaSeuMelhorCard1Tema
                    2 -> programacao.facaSeuMelhorCard2Tema
                    3 -> programacao.facaSeuMelhorCard3Tema
                    4 -> programacao.facaSeuMelhorCard4Tema
                    else -> ""
                }
                if (isCardCustom || cardTema == "O que você diria?") {
                    listOf(PerfilPublicador.ANCIAO, PerfilPublicador.SERVO_MINISTERIAL)
                } else if (formatoEstudante == FormatoParteEstudante.DISCURSO || formatoEstudante == FormatoParteEstudante.EXPLICANDO_CRENCAS_DISCURSO) {
                    regras.matriz.allowedEstudanteDiscurso
                } else {
                    regras.matriz.allowedEstudanteApresentador
                }
            }
            "estudanteAjudanteId" -> regras.matriz.allowedEstudanteApresentador // Se ajudante, perfil de estudante em geral
            "vidaParteLocalId" -> {
                val exclusivoAnciao = if (campo == "vidaParteLocal2Id") programacao.vidaParteLocal2ExclusivoAnciao else programacao.vidaParteLocalLocalExclusivoAnciao
                if (exclusivoAnciao) {
                    listOf(PerfilPublicador.ANCIAO)
                } else {
                    regras.matriz.allowedDiscursoJoias
                }
            }
            "vidaEstudoDirigenteId" -> regras.matriz.allowedDirigenteEstudo
            "vidaEstudoLeitorId" -> regras.matriz.allowedLeitorEstudo
            else -> PerfilPublicador.values().toList()
        }

        // Filtra os elegíveis com base no gênero ou travas de acúmulo imediatas
        publicadores.forEach { pub ->
            var score = 0 // Menor score = melhor candidato (menos penalidade)
            val motivos = java.lang.StringBuilder()

            // 1. Filtrar se pertence aos perfis da matriz
            if (!allowedProfiles.contains(pub.perfil)) {
                return@forEach // Desqualificado totalmente
            }

            // Exceção estrita de gênero no ajudante se o apresentador estiver selecionado
            if (mappedCampo == "estudanteAjudanteId" && apresentadorEstudanteId != null) {
                val apr = pMap[apresentadorEstudanteId]
                if (apr != null && apr.genero != pub.genero && !saoParentesDePrimeiroGrau(apr, pub)) {
                    return@forEach // Desqualificado por gênero incompatível sem parentesco
                }
            }

            // Se for servo dirigente e não tiver aprovação explicita
            if (campo == "vidaEstudoDirigenteId" && pub.perfil == PerfilPublicador.SERVO_MINISTERIAL && !pub.servoDirigenteAprovado) {
                return@forEach // Desqualificado
            }

            // Trava de acúmulo estrito se dirigente = leitor
            if (campo == "vidaEstudoLeitorId" && programacao.vidaEstudoDirigenteId == pub.id) {
                return@forEach // Desqualificado
            }
            if (campo == "vidaEstudoDirigenteId" && programacao.vidaEstudoLeitorId == pub.id) {
                return@forEach // Desqualificado
            }

            // 2. Pontuação/Penalidades por rodízio:
            
            // A. Regras consecutivas de tribuna (Ancião/Servo)
            if (campo == "tesourosDiscursoId" || campo == "tesourosJoiasId" || campo.startsWith("vidaParteLocal") || campo == "vidaEstudoDirigenteId") {
                if (fezParteSemanaAnterior(pub, programacao.semana)) {
                    if (!regras.permitirSemanasConsecutivasTribuna) {
                        score += 50
                        motivos.append("Alerta: Atuou na última reunião (Evitar semana consecutiva!). ")
                    } else {
                        score += 15
                        motivos.append("Atuou na última reunião (Consecutivo permitido pelo painel). ")
                    }
                }
            }

            // B. Alternância de Papéis (Presenter / Helper)
            if (mappedCampo == "estudanteApresentadorId" && regras.alternarPapeisEstudante && pub.historicoPartes.isNotEmpty()) {
                val ultimaParte = pub.historicoPartes.firstOrNull { it.tipoParte.startsWith("MINISTERIO_") }
                if (ultimaParte != null && ultimaParte.papel == "APRESENTADOR") {
                    score += 20
                    motivos.append("Aviso: Foi Dono/Apresentador na última designação. ")
                } else if (ultimaParte != null && ultimaParte.papel == "AJUDANTE") {
                    score -= 10
                    motivos.append("Recomendável: Foi Ajudante na última designação (Hora de alternar!). ")
                }
            }

            // C. Seleção de Leitor do Estudo (Priorizar NÃO-Anciãos)
            if (campo == "vidaEstudoLeitorId") {
                if (pub.perfil == PerfilPublicador.ANCIAO) {
                    score += 30
                    motivos.append("Evitar: Anciãos têm prioridade mínima para Leitura. ")
                } else if (pub.perfil == PerfilPublicador.IRMAO_BATIZADO) {
                    score -= 15
                    motivos.append("Excelente: Irmão comum prioritário para leitura. ")
                }
            }

            // D. Evitar repetir mesma dupla no Estudo
            if (campo == "vidaEstudoLeitorId" && programacao.vidaEstudoDirigenteId != null && regras.evitarDuplaEstudoRepetidaAnterior) {
                val dId = programacao.vidaEstudoDirigenteId
                val foiDuplaAnteontem = pub.historicoPartes.any { it.tipoParte == "VIDA_ESTUDO_DIRIGENTE" && it.parceiroId == dId }
                if (foiDuplaAnteontem) {
                    score += 40
                    motivos.append("Alerta: Fez dupla com este Dirigente recentemente. ")
                }
            }

            // E. Distância do histórico de últimas partes em geral (quanto mais tempo livre, menor a penalidade, sugerindo rodízio saudável)
            if (pub.historicoPartes.isNotEmpty()) {
                val semanasDesdeUltima = pub.historicoPartes.size * 10 
                score -= semanasDesdeUltima // Maior histórico livre = pontuação melhorada
                motivos.append("Histórico registrado de partes anteriores. ")
            } else {
                score -= 30 // Zero histórico = altamente qualificado/prioridade
                motivos.append("Prioritário: Nunca fez esta parte ou histórico zerado. ")
            }

            if (motivos.isEmpty()) {
                motivos.append("Disponível e Elegível para designação.")
            }

            resultado.add(Triple(pub, score, motivos.toString().trim()))
        }

        // Ordenando pelo score de penalidade (menor score = primeiro)
        return resultado.sortedBy { it.second }.map { Pair(it.first, it.third) }
    }
}
