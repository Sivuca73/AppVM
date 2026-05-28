package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializando o banco de dados Room e o repositório unificado
        val database = AppDatabase.getDatabase(this)
        repository = AppRepository(database)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Instanciando ViewModel com Fábrica Customizada
                val factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return AppVMViewModel(repository) as T
                    }
                }
                val viewModel: AppVMViewModel = viewModel(factory = factory)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    AppVMScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * ViewModel reativo do AppVM.
 */
class AppVMViewModel(private val repository: AppRepository) : ViewModel() {
    val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)

    // Fluxos reativos do Repositório Room
    val publicadores: StateFlow<List<Publicador>> = repository.publicadoresStream
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val regras: StateFlow<PainelRegrasConfig> = repository.regrasConfigStream
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), PainelRegrasConfig())

    // Armazenamento de todas as semanas preenchidas por data ("YYYY-MM-DD")
    val todasProgramacoes = MutableStateFlow<Map<String, ProgramacaoSemana>>(emptyMap())

    // Estado local da programação semanal de designação editável (Esboço ativo)
    val programacaoSemana = MutableStateFlow(
        ProgramacaoSemana(
            semana = "2026-05-26", // Terça-feira (dia oficial)
            presidenteId = null,
            oracaoInicialId = null,
            tesourosDiscursoId = null,
            tesourosJoiasId = null,
            tesourosLeituraId = null,
            estudante1ApresentadorId = null,
            estudante1AjudanteId = null,
            estudante1Formato = FormatoParteEstudante.DEMONSTRACAO,
            estudante2ApresentadorId = null,
            estudante2AjudanteId = null,
            estudante2Formato = FormatoParteEstudante.DEMONSTRACAO,
            estudante3ApresentadorId = null,
            estudante3AjudanteId = null,
            estudante3Formato = FormatoParteEstudante.DEMONSTRACAO,
            vidaParteLocal1Id = null,
            vidaParteLocal1DuracaoMin = 15,
            vidaParteLocalLocalExclusivoAnciao = false,
            vidaEstudoDirigenteId = null,
            vidaEstudoLeitorId = null,
            oracaoFinalId = null
        )
    )

    // Relatório reativo de erros e avisos de validação combinados em tempo real (UDF)
    val validacaoReport: StateFlow<List<RelatorioValidacao>> = combine(
        programacaoSemana,
        publicadores,
        regras
    ) { prog, pubs, rules ->
        RulesEngine.validarProgramacao(prog, pubs, rules)
    }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Inicializa opcionalmente o banco se estiver vazio
        coroutineScope.launch {
            val list = repository.publicadoresStream.first()
            if (list.isEmpty()) {
                repository.preencherCongregacaoExemplo()
            }
        }
    }

    fun setSemanaAtiva(semanaData: String) {
        val existente = todasProgramacoes.value[semanaData] ?: ProgramacaoSemana(semana = semanaData)
        programacaoSemana.value = existente
    }

    fun updateProgramacao(updater: (ProgramacaoSemana) -> ProgramacaoSemana) {
        val nova = updater(programacaoSemana.value)
        programacaoSemana.value = nova
        todasProgramacoes.value = todasProgramacoes.value + (nova.semana to nova)
    }

    fun preencherCongregacaoExemplo() {
        coroutineScope.launch {
            repository.preencherCongregacaoExemplo()
        }
    }

    fun saveRules(config: PainelRegrasConfig) {
        coroutineScope.launch {
            repository.saveRules(config)
        }
    }

    fun insertPublicador(pub: Publicador) {
        coroutineScope.launch {
            repository.savePublicadorWithMirroring(pub)
        }
    }

    fun deletePublicador(id: Int) {
        coroutineScope.launch {
            repository.deletePublicador(id)
        }
    }
}

/**
 * Tela principal do aplicativo AppVM com os 3 painéis dinâmicos.
 */
@Composable
fun AppVMScreen(
    viewModel: AppVMViewModel,
    modifier: Modifier = Modifier
) {
    val publicadores by viewModel.publicadores.collectAsState()
    val regras by viewModel.regras.collectAsState()
    val programacao by viewModel.programacaoSemana.collectAsState()
    val todasProgramacoes by viewModel.todasProgramacoes.collectAsState()
    val alertas by viewModel.validacaoReport.collectAsState()
    val context = LocalContext.current

    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Cabeçalho da aplicação (Material 3 Top App Bar Imersivo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.9f))
                .border(width = 1.dp, color = Color(0xFFEADDFF))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEADDFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "⚙️", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Painel AppVM",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1C1B1F)
                    )
                    Text(
                        text = "Gestão de Regras Dinâmicas",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
            }
            IconButton(
                onClick = {
                    Toast.makeText(context, "Sino clicado! Alerta do Gestor Inteligente em tempo real.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF7F2FA))
            ) {
                Text(text = "🔔", fontSize = 16.sp)
            }
        }

        // Conteúdo da Aba Ativa
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            when (selectedTab) {
                0 -> AgendaEsquemaTab(
                    programacao = programacao,
                    publicadores = publicadores,
                    regras = regras,
                    alertas = alertas,
                    todasProgramacoes = todasProgramacoes,
                    onUpdateProg = { viewModel.updateProgramacao(it) },
                    onPopulateMock = {
                        viewModel.preencherCongregacaoExemplo()
                        Toast.makeText(context, "Dados de exemplos restaurados!", Toast.LENGTH_SHORT).show()
                    },
                    onWeekSelected = { viewModel.setSemanaAtiva(it) }
                )
                1 -> CadastroPublicadoresTab(
                    publicadores = publicadores,
                    onAdd = { viewModel.insertPublicador(it) },
                    onDelete = { id ->
                        viewModel.deletePublicador(id)
                        Toast.makeText(context, "Publicador removido!", Toast.LENGTH_SHORT).show()
                    },
                    onRestore = {
                        viewModel.preencherCongregacaoExemplo()
                        Toast.makeText(context, "Base de exemplo restaurada!", Toast.LENGTH_SHORT).show()
                    }
                )
                2 -> ConfiguracoesPainelTab(
                    regras = regras,
                    onSave = {
                        viewModel.saveRules(it)
                        Toast.makeText(context, "Regras salvas e aplicadas em tempo real!", Toast.LENGTH_SHORT).show()
                    }
                )
                3 -> RelatorioOverviewTab(
                    publicadores = publicadores,
                    regras = regras,
                    alertas = alertas
                )
            }
        }

        // Barra de navegação inferior (Material 3 Imersivo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color(0xFFF3EDF7))
                .border(width = 1.dp, color = Color(0xFFEADDFF))
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = "📅",
                label = "Agenda",
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            BottomNavItem(
                icon = "👥",
                label = "Pessoas",
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
            BottomNavItem(
                icon = "🛠️",
                label = "Regras",
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 }
            )
            BottomNavItem(
                icon = "📊",
                label = "Relatório",
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 }
            )
        }
    }
}

// ==========================================
// ABA 1: AGENDA SEMANAL & VALIDATOR / SUGGESTIONS
// ==========================================
fun obterTercasFeirasDoMes(ano: Int, mes: Int): List<String> {
    val datas = mutableListOf<String>()
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.YEAR, ano)
    calendar.set(java.util.Calendar.MONTH, mes - 1)
    calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
    
    val maxDias = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    for (dia in 1..maxDias) {
        calendar.set(java.util.Calendar.DAY_OF_MONTH, dia)
        if (calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.TUESDAY) {
            val formatado = String.format("%04d-%02d-%02d", ano, mes, dia)
            datas.add(formatado)
        }
    }
    return datas
}

fun formatarDataExibicaoCompleta(dataString: String): String {
    return try {
        val partes = dataString.split("-")
        val ano = partes[0]
        val mesInt = partes[1].toInt()
        val dia = partes[2].toInt()
        val mesesNames = listOf(
            "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
        )
        "Terça-feira, $dia de ${mesesNames[mesInt - 1]} de $ano"
    } catch (e: Exception) {
        dataString
    }
}

fun gerarDesignacoesAutomaticamente(
    prev: ProgramacaoSemana,
    publicadores: List<Publicador>,
    regras: PainelRegrasConfig
): ProgramacaoSemana {
    var p = prev
    val assignedInWeek = mutableSetOf<Int>()

    fun sugerirEBuscarMelhor(campo: String, formato: FormatoParteEstudante? = null, apresentadorId: Int? = null): Int? {
        val candidatos = RulesEngine.sugerirCandidatos(campo, formato, apresentadorId, publicadores, p, regras)
        for (candPair in candidatos) {
            val cand = candPair.first
            if (!regras.permitirAcumulo && assignedInWeek.contains(cand.id)) continue
            return cand.id
        }
        return candidatos.firstOrNull()?.first?.id
    }

    val isCustomModeSelected = p.facaSeuMelhorOpcao == "custom"
    fun getFormatoForTema(tema: String): FormatoParteEstudante {
        return if (isCustomModeSelected || tema == "Discurso" || tema == "O que você diria?") {
            FormatoParteEstudante.DISCURSO
        } else {
            FormatoParteEstudante.DEMONSTRACAO
        }
    }

    // 1. Presidente
    sugerirEBuscarMelhor("presidenteId")?.let {
        p = p.copy(presidenteId = it)
        assignedInWeek.add(it)
    }

    // 2. Oração Inicial
    sugerirEBuscarMelhor("oracaoInicialId")?.let {
        p = p.copy(oracaoInicialId = it)
        assignedInWeek.add(it)
    }

    // 3. Discurso
    sugerirEBuscarMelhor("tesourosDiscursoId")?.let {
        p = p.copy(tesourosDiscursoId = it)
        assignedInWeek.add(it)
    }

    // 4. Joias
    sugerirEBuscarMelhor("tesourosJoiasId")?.let {
        p = p.copy(tesourosJoiasId = it)
        assignedInWeek.add(it)
    }

    // 5. Leitura
    sugerirEBuscarMelhor("tesourosLeituraId")?.let {
        p = p.copy(tesourosLeituraId = it)
        assignedInWeek.add(it)
    }

    // 6. Faça Seu Melhor no Ministério (Dynamic)
    val op = p.facaSeuMelhorOpcao
    val cardCount = when (op) {
        "1" -> 1
        "2" -> 2
        "3" -> 3
        "4" -> 4
        "custom" -> p.facaSeuMelhorCardCountCustom.coerceIn(1, 3)
        else -> 3
    }

    // Card 1
    if (cardCount >= 1) {
        val f1 = getFormatoForTema(p.facaSeuMelhorCard1Tema)
        val e1Dono = sugerirEBuscarMelhor("estudante1ApresentadorId", f1)
        if (e1Dono != null) {
            val e1Aju = if (f1 == FormatoParteEstudante.DEMONSTRACAO) sugerirEBuscarMelhor("estudante1AjudanteId", null, e1Dono) else null
            p = p.copy(estudante1ApresentadorId = e1Dono, estudante1AjudanteId = e1Aju, estudante1Formato = f1)
            assignedInWeek.add(e1Dono)
            if (e1Aju != null) assignedInWeek.add(e1Aju)
        }
    } else {
        p = p.copy(estudante1ApresentadorId = null, estudante1AjudanteId = null)
    }

    // Card 2
    if (cardCount >= 2) {
        val f2 = getFormatoForTema(p.facaSeuMelhorCard2Tema)
        val e2Dono = sugerirEBuscarMelhor("estudante2ApresentadorId", f2)
        if (e2Dono != null) {
            val e2Aju = if (f2 == FormatoParteEstudante.DEMONSTRACAO) sugerirEBuscarMelhor("estudante2AjudanteId", null, e2Dono) else null
            p = p.copy(estudante2ApresentadorId = e2Dono, estudante2AjudanteId = e2Aju, estudante2Formato = f2)
            assignedInWeek.add(e2Dono)
            if (e2Aju != null) assignedInWeek.add(e2Aju)
        }
    } else {
        p = p.copy(estudante2ApresentadorId = null, estudante2AjudanteId = null)
    }

    // Card 3
    if (cardCount >= 3) {
        val f3 = getFormatoForTema(p.facaSeuMelhorCard3Tema)
        val e3Dono = sugerirEBuscarMelhor("estudante3ApresentadorId", f3)
        if (e3Dono != null) {
            val e3Aju = if (f3 == FormatoParteEstudante.DEMONSTRACAO) sugerirEBuscarMelhor("estudante3AjudanteId", null, e3Dono) else null
            p = p.copy(estudante3ApresentadorId = e3Dono, estudante3AjudanteId = e3Aju, estudante3Formato = f3)
            assignedInWeek.add(e3Dono)
            if (e3Aju != null) assignedInWeek.add(e3Aju)
        }
    } else {
        p = p.copy(estudante3ApresentadorId = null, estudante3AjudanteId = null)
    }

    // Card 4
    if (cardCount >= 4) {
        val f4 = getFormatoForTema(p.facaSeuMelhorCard4Tema)
        val e4Dono = sugerirEBuscarMelhor("estudante4ApresentadorId", f4)
        if (e4Dono != null) {
            val e4Aju = if (f4 == FormatoParteEstudante.DEMONSTRACAO) sugerirEBuscarMelhor("estudante4AjudanteId", null, e4Dono) else null
            p = p.copy(estudante4ApresentadorId = e4Dono, estudante4AjudanteId = e4Aju, estudante4Formato = f4)
            assignedInWeek.add(e4Dono)
            if (e4Aju != null) assignedInWeek.add(e4Aju)
        }
    } else {
        p = p.copy(estudante4ApresentadorId = null, estudante4AjudanteId = null)
    }

    // 9. Vida Parte Local (Dynamic quantity 1 or 2)
    sugerirEBuscarMelhor("vidaParteLocal1Id")?.let {
        p = p.copy(vidaParteLocal1Id = it)
        assignedInWeek.add(it)
    }

    if (p.vidaPartesQuantidade == 2) {
        sugerirEBuscarMelhor("vidaParteLocal2Id")?.let {
            p = p.copy(vidaParteLocal2Id = it)
            assignedInWeek.add(it)
        }
    } else {
        p = p.copy(vidaParteLocal2Id = null)
    }

    // 10. Estudo Dirigente
    val dirId = sugerirEBuscarMelhor("vidaEstudoDirigenteId")
    if (dirId != null) {
        p = p.copy(vidaEstudoDirigenteId = dirId)
        assignedInWeek.add(dirId)
        // 11. Estudo Leitor
        val leiId = sugerirEBuscarMelhor("vidaEstudoLeitorId")
        if (leiId != null) {
            p = p.copy(vidaEstudoLeitorId = leiId)
            assignedInWeek.add(leiId)
        }
    }

    // 12. Oração Final
    sugerirEBuscarMelhor("oracaoFinalId")?.let {
        p = p.copy(oracaoFinalId = it)
        assignedInWeek.add(it)
    }

    return p
}

@Composable
fun AgendaEsquemaTab(
    programacao: ProgramacaoSemana,
    publicadores: List<Publicador>,
    regras: PainelRegrasConfig,
    alertas: List<RelatorioValidacao>,
    todasProgramacoes: Map<String, ProgramacaoSemana> = emptyMap(),
    onUpdateProg: ((ProgramacaoSemana) -> ProgramacaoSemana) -> Unit,
    onPopulateMock: () -> Unit,
    onWeekSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedMonth by remember { mutableStateOf<Int?>(null) }
    var selectedWeekTuesdayDate by remember { mutableStateOf<String?>(null) }
    var showVisualizacaoQuadro by remember { mutableStateOf(false) }

    var dialogSlotToSuggest by remember { mutableStateOf<String?>(null) }
    var dialogEstudanteFormato by remember { mutableStateOf<FormatoParteEstudante?>(null) }
    var dialogEstudanteApresentadorId by remember { mutableStateOf<Int?>(null) }

    val mesesDoAno = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        if (selectedMonth == null && selectedWeekTuesdayDate == null) {
            // ==========================================
            // TELA INICIAL DA AGENDA: DIRETO DE MESES
            // ==========================================
            Text(
                "DIRETÓRIO DA AGENDA (2026)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1D192B),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF6750A4).copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, Color(0xFF6750A4).copy(alpha = 0.2f))
            ) {
                Text(
                    text = "Selecione o mês para gerenciar ou visualizar as designações semanais da congregação. O aplicativo calcula as semanas oficiais com base nas reuniões semanais de terça-feira.",
                    fontSize = 13.sp,
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.padding(14.dp),
                    lineHeight = 18.sp
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mesesDoAno.forEachIndexed { index, mName ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMonth = index + 1 },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFF6750A4).copy(alpha = 0.15f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📅", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(mName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1C1B1F))
                            }
                            Text("➔", fontSize = 14.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } 
        else if (selectedMonth != null && selectedWeekTuesdayDate == null) {
            // ==========================================
            // TELA DE SELEÇÃO DE SEMANAS DO MÊS
            // ==========================================
            Button(
                onClick = { selectedMonth = null },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(100.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⬅  Voltar aos Meses", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            val tMes = selectedMonth!!
            val listTercas = obterTercasFeirasDoMes(2026, tMes)

            Text(
                text = "Semanas de ${mesesDoAno[tMes - 1]} de 2026",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF1C1B1F),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listTercas.forEachIndexed { index, dataString ->
                    val dataExibicao = formatarDataExibicaoCompleta(dataString)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                selectedWeekTuesdayDate = dataString
                                onWeekSelected(dataString)
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFF6750A4).copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Semana ${index + 1}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF6750A4))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(dataExibicao, fontSize = 13.sp, color = Color(0xFF49454F))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Designar", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6750A4))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("➔", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        else {
            // ==========================================
            // FORMULÁRIO COMPLETO COLORIDO DA REUNIÃO
            // ==========================================
            val dataString = selectedWeekTuesdayDate!!
            val dataCompleta = formatarDataExibicaoCompleta(dataString)

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { selectedWeekTuesdayDate = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF49454F)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text("⬅ Voltar", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { showVisualizacaoQuadro = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text("👁️ Ver Quadro", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                Text(
                    text = "Semana: $dataString",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Programa de Designações",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1D192B)
            )
            Text(
                text = dataCompleta,
                fontSize = 13.sp,
                color = Color(0xFF6750A4),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Elegante menu horizontal com 3 seletores com ícones correspondentes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F0F4))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val options = listOf(
                    Triple("NORMAL", "Reunião Normal", "🗓️"),
                    Triple("VISITA_SC", "Visita do SC", "💼"),
                    Triple("EVENTO", "Evento", "🏛️")
                )
                options.forEach { (tipo, label, icon) ->
                    val isSelected = programacao.tipoSemana == tipo
                    val bg = if (isSelected) Color(0xFF6750A4) else Color.Transparent
                    val fg = if (isSelected) Color.White else Color(0xFF49454F)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable {
                                onUpdateProg { it.copy(tipoSemana = tipo) }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                color = fg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (programacao.tipoSemana == "EVENTO") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 40.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EAFF)),
                    border = BorderStroke(1.dp, Color(0xFF6750A4).copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFCEB1FC), Color(0xFF6750A4))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🏛️", fontSize = 38.sp)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "SEMANA ESPECIAL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF6750A4),
                            letterSpacing = 1.5.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Não há reunião nessa semana. Estaremos em uma Assembleia de Circuito, em um Congresso Regional ou realizaremos a Celebração da Morte de Jesus.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1C1B1F),
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            } else {
                // 1. CABEÇALHO GERAL (Card de Topo)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    border = BorderStroke(1.dp, Color(0xFFEADDFF)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        Text("👑", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PRESIDÊNCIA E ORAÇÃO INICIAL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Presidente de Reunião
                    ParteSelectorField(
                        title = "Presidente da Reunião",
                        subtitle = "Ancião ou Servo Ministerial",
                        selectedId = programacao.presidenteId,
                        publicadores = publicadores,
                        alertas = alertas,
                        campoKey = "presidenteId",
                        onSelect = { id -> onUpdateProg { it.copy(presidenteId = id) } },
                        onSuggest = {
                            dialogSlotToSuggest = "presidenteId"
                            dialogEstudanteFormato = null
                            dialogEstudanteApresentadorId = null
                        },
                        containerColor = Color.White
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Oração Inicial
                    ParteSelectorField(
                        title = "Oração Inicial",
                        subtitle = "Qualquer irmão batizado",
                        selectedId = programacao.oracaoInicialId,
                        publicadores = publicadores,
                        alertas = alertas,
                        campoKey = "oracaoInicialId",
                        onSelect = { id -> onUpdateProg { it.copy(oracaoInicialId = id) } },
                        onSuggest = {
                            dialogSlotToSuggest = "oracaoInicialId"
                            dialogEstudanteFormato = null
                            dialogEstudanteApresentadorId = null
                        },
                        containerColor = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

        // --- SEÇÃO A: TESOUROS DA PALAVRA DE DEUS ---
        SectionHeader(title = "TESOUROS DA PALAVRA DE DEUS", color = Color(0xFF34727D)) // Azul Petróleo
        
        ParteSelectorField(
            title = "Discurso (10 min)",
            subtitle = "Apenas Anciãos ou Servos Ministeriais",
            selectedId = programacao.tesourosDiscursoId,
            publicadores = publicadores,
            alertas = alertas,
            campoKey = "tesourosDiscursoId",
            onSelect = { id -> onUpdateProg { it.copy(tesourosDiscursoId = id) } },
            onSuggest = {
                dialogSlotToSuggest = "tesourosDiscursoId"
                dialogEstudanteFormato = null
                dialogEstudanteApresentadorId = null
            },
            containerColor = Color(0xFFE4F2F4)
        )

        ParteSelectorField(
            title = "Joias Espirituais (10 min)",
            subtitle = "Apenas Anciãos ou Servos Ministeriais",
            selectedId = programacao.tesourosJoiasId,
            publicadores = publicadores,
            alertas = alertas,
            campoKey = "tesourosJoiasId",
            onSelect = { id -> onUpdateProg { it.copy(tesourosJoiasId = id) } },
            onSuggest = {
                dialogSlotToSuggest = "tesourosJoiasId"
                dialogEstudanteFormato = null
                dialogEstudanteApresentadorId = null
            },
            containerColor = Color(0xFFE4F2F4)
        )

        ParteSelectorField(
            title = "Leitura da Bíblia (4 min)",
            subtitle = "Homens (Exclui Anciãos e Irmãs)",
            selectedId = programacao.tesourosLeituraId,
            publicadores = publicadores,
            alertas = alertas,
            campoKey = "tesourosLeituraId",
            onSelect = { id -> onUpdateProg { it.copy(tesourosLeituraId = id) } },
            onSuggest = {
                dialogSlotToSuggest = "tesourosLeituraId"
                dialogEstudanteFormato = null
                dialogEstudanteApresentadorId = null
            },
            containerColor = Color(0xFFE4F2F4)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- SEÇÃO B: FAÇA SEU MELHOR NO MINISTÉRIO ---
        SectionHeader(title = "FAÇA SEU MELHOR NO MINISTÉRIO", color = Color(0xFFBE9F67)) // Dourado Ouro

        // Selector for Quantity of Student Cards of Faça seu Melhor
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Partes:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
            listOf("3", "4", "custom").forEach { opt ->
                val selected = programacao.facaSeuMelhorOpcao == opt
                val label = when (opt) {
                    "3" -> "3"
                    "4" -> "4"
                    "custom" -> "⚙️+"
                    else -> opt
                }
                AssistChip(
                    onClick = {
                        onUpdateProg { current ->
                            val updated = current.copy(facaSeuMelhorOpcao = opt)
                            when (opt) {
                                "3" -> updated.copy(
                                    facaSeuMelhorCard1Tema = "Iniciando conversas",
                                    facaSeuMelhorCard2Tema = "Cultivando o interesse",
                                    facaSeuMelhorCard3Tema = "Fazendo discípulos"
                                )
                                "4" -> updated.copy(
                                    facaSeuMelhorCard1Tema = "Iniciando conversas",
                                    facaSeuMelhorCard2Tema = "Cultivando o interesse",
                                    facaSeuMelhorCard3Tema = "Fazendo discípulos",
                                    facaSeuMelhorCard4Tema = "Fazendo discípulos"
                                )
                                else -> updated
                            }
                        }
                    },
                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) Color(0xFFBE9F67) else Color.White,
                        labelColor = if (selected) Color.White else Color(0xFF3C4043)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) Color(0xFFBE9F67) else Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }
        }

        // Custom quantitative row if CUSTOM selected
        if (programacao.facaSeuMelhorOpcao == "custom") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Nº de partes customizadas (1-3):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3C4043))
                listOf(1, 2, 3).forEach { count ->
                    val selCount = programacao.facaSeuMelhorCardCountCustom == count
                    AssistChip(
                        onClick = {
                            onUpdateProg { it.copy(facaSeuMelhorCardCountCustom = count) }
                        },
                        label = { Text(count.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selCount) Color(0xFFBE9F67) else Color.White,
                            labelColor = if (selCount) Color.White else Color(0xFF3C4043)
                        )
                    )
                }
            }
        }

        // Identify dynamic theme options according to layout option
        val fsmOpcao = programacao.facaSeuMelhorOpcao
        val cardCountToRender = when (fsmOpcao) {
            "3" -> 3
            "4" -> 4
            "custom" -> programacao.facaSeuMelhorCardCountCustom.coerceIn(1, 3)
            else -> 3
        }

        val tema1Options = emptyList<String>() // Card 1 is always fixed "Iniciando conversas" and has NO dropdown
        
        val unifiedTemaOptions = listOf(
            "Iniciando conversas",
            "Cultivando o interesse",
            "Explicando suas crenças",
            "Fazendo discípulos",
            "Discurso",
            "O que você diria?"
        )

        val isCustomModeActive = fsmOpcao == "custom"

        if (cardCountToRender >= 1) {
            EstudanteCardItem(
                num = 1,
                apresentadorId = programacao.estudante1ApresentadorId,
                ajudanteId = programacao.estudante1AjudanteId,
                formato = programacao.estudante1Formato,
                publicadores = publicadores,
                alertas = alertas,
                onSelectDono = { id -> onUpdateProg { it.copy(estudante1ApresentadorId = id) } },
                onSelectAjudante = { id -> onUpdateProg { it.copy(estudante1AjudanteId = id) } },
                onSelectFormato = { form -> onUpdateProg { it.copy(estudante1Formato = form) } },
                onSuggestDono = {
                    dialogSlotToSuggest = "estudante1ApresentadorId"
                    dialogEstudanteFormato = programacao.estudante1Formato
                    dialogEstudanteApresentadorId = null
                },
                onSuggestAjudante = {
                    if (programacao.estudante1ApresentadorId == null) {
                        Toast.makeText(context, "Defina o apresentador primeiro para sugerir o ajudante compatível!", Toast.LENGTH_SHORT).show()
                    } else {
                        dialogSlotToSuggest = "estudante1AjudanteId"
                        dialogEstudanteFormato = null
                        dialogEstudanteApresentadorId = programacao.estudante1ApresentadorId
                    }
                },
                tema = programacao.facaSeuMelhorCard1Tema,
                onTemaChange = { t -> onUpdateProg { it.copy(facaSeuMelhorCard1Tema = t, estudante1Formato = if (isCustomModeActive) FormatoParteEstudante.DISCURSO else FormatoParteEstudante.DEMONSTRACAO) } },
                temaOptions = tema1Options,
                containerColor = Color(0xFFF0E9DC),
                isCustom = isCustomModeActive,
                titleOverride = "Parte 1"
            )
        }

        if (cardCountToRender >= 2) {
            EstudanteCardItem(
                num = 2,
                apresentadorId = programacao.estudante2ApresentadorId,
                ajudanteId = programacao.estudante2AjudanteId,
                formato = programacao.estudante2Formato,
                publicadores = publicadores,
                alertas = alertas,
                onSelectDono = { id -> onUpdateProg { it.copy(estudante2ApresentadorId = id) } },
                onSelectAjudante = { id -> onUpdateProg { it.copy(estudante2AjudanteId = id) } },
                onSelectFormato = { form -> onUpdateProg { it.copy(estudante2Formato = form) } },
                onSuggestDono = {
                    dialogSlotToSuggest = "estudante2ApresentadorId"
                    dialogEstudanteFormato = programacao.estudante2Formato
                    dialogEstudanteApresentadorId = null
                },
                onSuggestAjudante = {
                    if (programacao.estudante2ApresentadorId == null) {
                        Toast.makeText(context, "Defina o apresentador primeiro para sugerir o ajudante compatível!", Toast.LENGTH_SHORT).show()
                    } else {
                        dialogSlotToSuggest = "estudante2AjudanteId"
                        dialogEstudanteFormato = null
                        dialogEstudanteApresentadorId = programacao.estudante2ApresentadorId
                    }
                },
                tema = programacao.facaSeuMelhorCard2Tema,
                onTemaChange = { t ->
                    onUpdateProg { 
                        val fmt = if (isCustomModeActive || t == "Discurso" || t == "O que você diria?") FormatoParteEstudante.DISCURSO else FormatoParteEstudante.DEMONSTRACAO
                        it.copy(
                            facaSeuMelhorCard2Tema = t,
                            estudante2Formato = fmt
                        )
                    }
                },
                temaOptions = unifiedTemaOptions,
                containerColor = Color(0xFFF0E9DC),
                isCustom = isCustomModeActive,
                titleOverride = "Parte 2"
            )
        }

        if (cardCountToRender >= 3) {
            EstudanteCardItem(
                num = 3,
                apresentadorId = programacao.estudante3ApresentadorId,
                ajudanteId = programacao.estudante3AjudanteId,
                formato = programacao.estudante3Formato,
                publicadores = publicadores,
                alertas = alertas,
                onSelectDono = { id -> onUpdateProg { it.copy(estudante3ApresentadorId = id) } },
                onSelectAjudante = { id -> onUpdateProg { it.copy(estudante3AjudanteId = id) } },
                onSelectFormato = { form -> onUpdateProg { it.copy(estudante3Formato = form) } },
                onSuggestDono = {
                    dialogSlotToSuggest = "estudante3ApresentadorId"
                    dialogEstudanteFormato = programacao.estudante3Formato
                    dialogEstudanteApresentadorId = null
                },
                onSuggestAjudante = {
                    if (programacao.estudante3ApresentadorId == null) {
                        Toast.makeText(context, "Defina o apresentador primeiro para sugerir o ajudante compatível!", Toast.LENGTH_SHORT).show()
                    } else {
                        dialogSlotToSuggest = "estudante3AjudanteId"
                        dialogEstudanteFormato = null
                        dialogEstudanteApresentadorId = programacao.estudante3ApresentadorId
                    }
                },
                tema = programacao.facaSeuMelhorCard3Tema,
                onTemaChange = { t ->
                    onUpdateProg { 
                        val fmt = if (isCustomModeActive || t == "Discurso" || t == "O que você diria?") FormatoParteEstudante.DISCURSO else FormatoParteEstudante.DEMONSTRACAO
                        it.copy(
                            facaSeuMelhorCard3Tema = t,
                            estudante3Formato = fmt
                        )
                    }
                },
                temaOptions = unifiedTemaOptions,
                containerColor = Color(0xFFF0E9DC),
                isCustom = isCustomModeActive,
                titleOverride = "Parte 3"
            )
        }

        if (cardCountToRender >= 4) {
            EstudanteCardItem(
                num = 4,
                apresentadorId = programacao.estudante4ApresentadorId,
                ajudanteId = programacao.estudante4AjudanteId,
                formato = programacao.estudante4Formato,
                publicadores = publicadores,
                alertas = alertas,
                onSelectDono = { id -> onUpdateProg { it.copy(estudante4ApresentadorId = id) } },
                onSelectAjudante = { id -> onUpdateProg { it.copy(estudante4AjudanteId = id) } },
                onSelectFormato = { form -> onUpdateProg { it.copy(estudante4Formato = form) } },
                onSuggestDono = {
                    dialogSlotToSuggest = "estudante4ApresentadorId"
                    dialogEstudanteFormato = programacao.estudante4Formato
                    dialogEstudanteApresentadorId = null
                },
                onSuggestAjudante = {
                    if (programacao.estudante4ApresentadorId == null) {
                        Toast.makeText(context, "Defina o apresentador primeiro para sugerir o ajudante compatível!", Toast.LENGTH_SHORT).show()
                    } else {
                        dialogSlotToSuggest = "estudante4AjudanteId"
                        dialogEstudanteFormato = null
                        dialogEstudanteApresentadorId = programacao.estudante4ApresentadorId
                    }
                },
                tema = programacao.facaSeuMelhorCard4Tema,
                onTemaChange = { t ->
                    onUpdateProg { 
                        val fmt = if (isCustomModeActive || t == "Discurso" || t == "O que você diria?") FormatoParteEstudante.DISCURSO else FormatoParteEstudante.DEMONSTRACAO
                        it.copy(
                            facaSeuMelhorCard4Tema = t,
                            estudante4Formato = fmt
                        )
                    }
                },
                temaOptions = unifiedTemaOptions,
                containerColor = Color(0xFFF0E9DC),
                isCustom = isCustomModeActive,
                titleOverride = "Parte 4"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SEÇÃO C: NOSSA VIDA CRISTÃ ---
        SectionHeader(title = "NOSSA VIDA CRISTÃ", color = Color(0xFF912421)) // Vermelho Grená

        // Master Card: Partes Locais da Semana (Dynamic Quantity 1 or 2 parts)
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7DEDD)), // Grená clarinho
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Partes Locais da Semana",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF202124)
                )
                Text(
                    text = "Selecione a quantidade de partes de Vida Cristã desta semana:",
                    fontSize = 11.sp,
                    color = Color(0xFF5F6368),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Selector: [1 Parte] [2 Partes]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    listOf(1, 2).forEach { count ->
                        val selected = programacao.vidaPartesQuantidade == count
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateProg { it.copy(vidaPartesQuantidade = count) } },
                            label = { Text("$count ${if (count == 1) "Parte" else "Partes"}", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF912421),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                // Render Part 1 config & selector
                if (programacao.vidaPartesQuantidade >= 1) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            OutlinedTextField(
                                value = programacao.vidaParteLocal1Tema,
                                onValueChange = { t -> onUpdateProg { it.copy(vidaParteLocal1Tema = t) } },
                                label = { Text("Tema da Parte 1") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF912421),
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.2f),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Duração (min):", fontSize = 11.sp, color = Color(0xFF3C4043))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Row(
                                        modifier = Modifier.border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    ) {
                                        listOf(5, 10, 15).forEach { d ->
                                            val sel = programacao.vidaParteLocal1DuracaoMin == d
                                            Box(
                                                modifier = Modifier
                                                    .background(if (sel) Color(0xFF912421) else Color.White)
                                                    .clickable { onUpdateProg { it.copy(vidaParteLocal1DuracaoMin = d) } }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(d.toString(), fontSize = 10.sp, color = if (sel) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Só Ancião", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = programacao.vidaParteLocalLocalExclusivoAnciao,
                                        onCheckedChange = { exc -> onUpdateProg { it.copy(vidaParteLocalLocalExclusivoAnciao = exc) } },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF912421),
                                            checkedTrackColor = Color(0xFFF7DEDD)
                                        )
                                    )
                                }
                            }

                            ParteSelectorField(
                                title = "Orador - Parte 1",
                                subtitle = if (programacao.vidaParteLocalLocalExclusivoAnciao) "Exclusivamente Anciãos" else "Anciãos ou Servos Ministeriais",
                                selectedId = programacao.vidaParteLocal1Id,
                                publicadores = publicadores,
                                alertas = alertas,
                                campoKey = "vidaParteLocal1Id",
                                onSelect = { id -> onUpdateProg { it.copy(vidaParteLocal1Id = id) } },
                                onSuggest = {
                                    dialogSlotToSuggest = "vidaParteLocal1Id"
                                    dialogEstudanteFormato = null
                                    dialogEstudanteApresentadorId = null
                                },
                                containerColor = Color(0xFFF7DEDD)
                            )
                        }
                    }
                }

                // Render Part 2 config & selector
                if (programacao.vidaPartesQuantidade >= 2) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            OutlinedTextField(
                                value = programacao.vidaParteLocal2Tema,
                                onValueChange = { t -> onUpdateProg { it.copy(vidaParteLocal2Tema = t) } },
                                label = { Text("Tema da Parte 2") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF912421),
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.2f),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Duração (min):", fontSize = 11.sp, color = Color(0xFF3C4043))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Row(
                                        modifier = Modifier.border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    ) {
                                        listOf(5, 10, 15).forEach { d ->
                                            val sel = programacao.vidaParteLocal2DuracaoMin == d
                                            Box(
                                                modifier = Modifier
                                                    .background(if (sel) Color(0xFF912421) else Color.White)
                                                    .clickable { onUpdateProg { it.copy(vidaParteLocal2DuracaoMin = d) } }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            ) {
                                                Text(d.toString(), fontSize = 10.sp, color = if (sel) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Só Ancião", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Switch(
                                        checked = programacao.vidaParteLocal2ExclusivoAnciao,
                                        onCheckedChange = { exc -> onUpdateProg { it.copy(vidaParteLocal2ExclusivoAnciao = exc) } },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF912421),
                                            checkedTrackColor = Color(0xFFF7DEDD)
                                        )
                                    )
                                }
                            }

                            ParteSelectorField(
                                title = "Orador - Parte 2",
                                subtitle = if (programacao.vidaParteLocal2ExclusivoAnciao) "Exclusivamente Anciãos" else "Anciãos ou Servos Ministeriais",
                                selectedId = programacao.vidaParteLocal2Id,
                                publicadores = publicadores,
                                alertas = alertas,
                                campoKey = "vidaParteLocal2Id",
                                onSelect = { id -> onUpdateProg { it.copy(vidaParteLocal2Id = id) } },
                                onSuggest = {
                                    dialogSlotToSuggest = "vidaParteLocal2Id"
                                    dialogEstudanteFormato = null
                                    dialogEstudanteApresentadorId = null
                                },
                                containerColor = Color(0xFFF7DEDD)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (programacao.tipoSemana == "VISITA_SC") {
            // Card Especial: Discurso de serviço (30 min)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7DEDD)), // Grená clarinho
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF912421).copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💼", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Discurso de serviço (30 min)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF912421)
                        )
                    }
                    Text(
                        text = "Parte especial conduzida pelo Superintendente de Circuito durante a sua visita.",
                        fontSize = 11.sp,
                        color = Color(0xFF5F6368),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tema do Discurso
                    OutlinedTextField(
                        value = programacao.visitaTemaDiscurso,
                        onValueChange = { t -> onUpdateProg { it.copy(visitaTemaDiscurso = t) } },
                        label = { Text("Tema do Discurso de Serviço", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF912421),
                            focusedLabelColor = Color(0xFF912421)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nome do Superintendente
                    OutlinedTextField(
                        value = programacao.visitaNomeViajante,
                        onValueChange = { n -> onUpdateProg { it.copy(visitaNomeViajante = n) } },
                        label = { Text("Nome do Superintendente de Circuito", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF912421),
                            focusedLabelColor = Color(0xFF912421)
                        )
                    )
                }
            }
        } else {
            // Estudo Bíblico de Congregação (30 min)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF7DEDD)), // Grená clarinho
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Estudo Bíblico de Congregação (30 min)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF202124))
                    Text("Dirigente e Leitor - Fortes travas de dupla e acúmulo", fontSize = 11.sp, color = Color(0xFF5F6368))
                    
                    Spacer(modifier = Modifier.height(10.dp))

                    ParteSelectorField(
                        title = "Dirigente",
                        subtitle = "Ancião ou Servo aprovado",
                        selectedId = programacao.vidaEstudoDirigenteId,
                        publicadores = publicadores,
                        alertas = alertas,
                        campoKey = "vidaEstudoDirigenteId",
                        onSelect = { id -> onUpdateProg { it.copy(vidaEstudoDirigenteId = id) } },
                        onSuggest = {
                            dialogSlotToSuggest = "vidaEstudoDirigenteId"
                            dialogEstudanteFormato = null
                            dialogEstudanteApresentadorId = null
                        },
                        containerColor = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    ParteSelectorField(
                        title = "Leitor",
                        subtitle = "Homens batizados (Forte prioridade não-anciãos)",
                        selectedId = programacao.vidaEstudoLeitorId,
                        publicadores = publicadores,
                        alertas = alertas,
                        campoKey = "vidaEstudoLeitorId",
                        onSelect = { id -> onUpdateProg { it.copy(vidaEstudoLeitorId = id) } },
                        onSuggest = {
                            dialogSlotToSuggest = "vidaEstudoLeitorId"
                            dialogEstudanteFormato = null
                            dialogEstudanteApresentadorId = null
                        },
                        containerColor = Color.White
                    )
                }
            }
        }

        // 5. RODAPÉ GERAL (Oração Final, auto-generation button, e simulador de preenchimento)
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            border = BorderStroke(1.dp, Color(0xFFEADDFF)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("🙏", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ORAÇÃO DE ENCERRAMENTO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF49454F),
                        letterSpacing = 0.5.sp
                    )
                }

                // Oração Final
                ParteSelectorField(
                    title = "Oração Final",
                    subtitle = "Qualquer irmão batizado",
                    selectedId = programacao.oracaoFinalId,
                    publicadores = publicadores,
                    alertas = alertas,
                    campoKey = "oracaoFinalId",
                    onSelect = { id -> onUpdateProg { it.copy(oracaoFinalId = id) } },
                    onSuggest = {
                        dialogSlotToSuggest = "oracaoFinalId"
                        dialogEstudanteFormato = null
                        dialogEstudanteApresentadorId = null
                    },
                    containerColor = Color.White
                )
            }
        }

        // --- TRAVA DE SEGURANÇA NO BOTÃO GERADOR ---
        val fsmCountVal = when (programacao.facaSeuMelhorOpcao) {
            "1" -> 1
            "2" -> 2
            "3" -> 3
            "4" -> 4
            "custom" -> programacao.facaSeuMelhorCardCountCustom.coerceIn(1, 3)
            else -> 3
        }

        val fsmPreenchido = when (fsmCountVal) {
            1 -> programacao.estudante1ApresentadorId != null &&
                 (programacao.estudante1Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante1AjudanteId != null)
            2 -> programacao.estudante1ApresentadorId != null &&
                 (programacao.estudante1Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante1AjudanteId != null) &&
                 programacao.estudante2ApresentadorId != null &&
                 (programacao.estudante2Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante2AjudanteId != null)
            3 -> programacao.estudante1ApresentadorId != null &&
                 (programacao.estudante1Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante1AjudanteId != null) &&
                 programacao.estudante2ApresentadorId != null &&
                 (programacao.estudante2Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante2AjudanteId != null) &&
                 programacao.estudante3ApresentadorId != null &&
                 (programacao.estudante3Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante3AjudanteId != null)
            4 -> programacao.estudante1ApresentadorId != null &&
                 (programacao.estudante1Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante1AjudanteId != null) &&
                 programacao.estudante2ApresentadorId != null &&
                 (programacao.estudante2Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante2AjudanteId != null) &&
                 programacao.estudante3ApresentadorId != null &&
                 (programacao.estudante3Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante3AjudanteId != null) &&
                 programacao.estudante4ApresentadorId != null &&
                 (programacao.estudante4Formato != FormatoParteEstudante.DEMONSTRACAO || programacao.estudante4AjudanteId != null)
            else -> false
        }

        val vidaPreenchido = if (programacao.tipoSemana == "VISITA_SC") {
            true
        } else if (programacao.vidaPartesQuantidade == 1) {
            programacao.vidaParteLocal1Id != null
        } else {
            programacao.vidaParteLocal1Id != null && programacao.vidaParteLocal2Id != null
        }

        val todosCamposPreenchidos = if (programacao.tipoSemana == "EVENTO") {
            true
        } else {
            programacao.presidenteId != null &&
            programacao.oracaoInicialId != null &&
            programacao.tesourosDiscursoId != null &&
            programacao.tesourosJoiasId != null &&
            programacao.tesourosLeituraId != null &&
            fsmPreenchido &&
            vidaPreenchido &&
            (programacao.tipoSemana == "VISITA_SC" || (programacao.vidaEstudoDirigenteId != null && programacao.vidaEstudoLeitorId != null)) &&
            programacao.oracaoFinalId != null
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!todosCamposPreenchidos) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECEFF1)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "⚠️ Trava de Segurança: Complete todos os campos deste formulário para ativar o preenchimento automático inteligente.",
                        fontSize = 11.sp,
                        color = Color(0xFF37474F),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Botão de simulação rápida para desbloquear a trava facilmente
            Button(
                onClick = {
                    val rascunhada = gerarDesignacoesAutomaticamente(programacao, publicadores, regras)
                    onUpdateProg { rascunhada }
                    Toast.makeText(context, "Todos os campos preenchidos automaticamente para simulação!", Toast.LENGTH_SHORT).show()
                    showVisualizacaoQuadro = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4).copy(alpha = 0.12f),
                    contentColor = Color(0xFF6750A4)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Text("⚡ Simular Preenchimento Completo Rápido", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Botão Oficial com Trava
            Button(
                onClick = {
                    if (todosCamposPreenchidos) {
                        val otimizada = gerarDesignacoesAutomaticamente(programacao, publicadores, regras)
                        onUpdateProg { otimizada }
                        Toast.makeText(context, "Designações calculadas e otimizadas seguindo as regras de rodízio e perfil!", Toast.LENGTH_SHORT).show()
                        showVisualizacaoQuadro = true
                    }
                },
                enabled = todosCamposPreenchidos,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    disabledContainerColor = Color(0xFFCAC4D0).copy(alpha = 0.4f),
                    contentColor = Color.White,
                    disabledContentColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("✦ Gerar Designações Automaticamente", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        }
    }
}

    // Modal de Sugestão de Candidatos Inteligentes (Pre-calcula fila de rodízio)
    dialogSlotToSuggest?.let { campo ->
        CandidatosDialog(
            campo = campo,
            formatoEstudante = dialogEstudanteFormato,
            apresentadorEstudanteId = dialogEstudanteApresentadorId,
            publicadores = publicadores,
            programacao = programacao,
            regras = regras,
            onDismiss = {
                dialogSlotToSuggest = null
                dialogEstudanteFormato = null
                dialogEstudanteApresentadorId = null
            },
            onSelect = { pubId ->
                onUpdateProg { current ->
                    when (campo) {
                        "presidenteId" -> current.copy(presidenteId = pubId)
                        "oracaoInicialId" -> current.copy(oracaoInicialId = pubId)
                        "tesourosDiscursoId" -> current.copy(tesourosDiscursoId = pubId)
                        "tesourosJoiasId" -> current.copy(tesourosJoiasId = pubId)
                        "tesourosLeituraId" -> current.copy(tesourosLeituraId = pubId)
                        "estudante1ApresentadorId" -> current.copy(estudante1ApresentadorId = pubId)
                        "estudante1AjudanteId" -> current.copy(estudante1AjudanteId = pubId)
                        "estudante2ApresentadorId" -> current.copy(estudante2ApresentadorId = pubId)
                        "estudante2AjudanteId" -> current.copy(estudante2AjudanteId = pubId)
                        "estudante3ApresentadorId" -> current.copy(estudante3ApresentadorId = pubId)
                        "estudante3AjudanteId" -> current.copy(estudante3AjudanteId = pubId)
                        "estudante4ApresentadorId" -> current.copy(estudante4ApresentadorId = pubId)
                        "estudante4AjudanteId" -> current.copy(estudante4AjudanteId = pubId)
                        "vidaParteLocal1Id" -> current.copy(vidaParteLocal1Id = pubId)
                        "vidaParteLocal2Id" -> current.copy(vidaParteLocal2Id = pubId)
                        "vidaEstudoDirigenteId" -> current.copy(vidaEstudoDirigenteId = pubId)
                        "vidaEstudoLeitorId" -> current.copy(vidaEstudoLeitorId = pubId)
                        "oracaoFinalId" -> current.copy(oracaoFinalId = pubId)
                        else -> current
                    }
                }
                dialogSlotToSuggest = null
                dialogEstudanteFormato = null
                dialogEstudanteApresentadorId = null
            }
        )
    }

    if (showVisualizacaoQuadro) {
        VisualizacaoQuadroDialog(
            programacao = programacao,
            publicadores = publicadores,
            todasProgramacoes = todasProgramacoes,
            onDismiss = { showVisualizacaoQuadro = false }
        )
    }
}

// ==========================================
// COMPONENTES AUXILIARES DE ABA 1
// ==========================================
@Composable
fun SectionHeader(title: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(20.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ParteSelectorField(
    title: String,
    subtitle: String,
    selectedId: Int?,
    publicadores: List<Publicador>,
    alertas: List<RelatorioValidacao>,
    campoKey: String,
    onSelect: (Int?) -> Unit,
    onSuggest: () -> Unit,
    containerColor: Color = Color.Unspecified
) {
    val pubMap = publicadores.associateBy { it.id }
    val selecionado = pubMap[selectedId]

    // Procura por alertas do campo específico
    val alertasDoCampo = alertas.filter { it.campo == campoKey }
    val piorAlerta = alertasDoCampo.maxByOrNull { it.severidade.ordinal }

    val statusColor = when (piorAlerta?.severidade) {
        SeveridadeValidacao.ERRO -> Color(0xFFE53935)   // Vermelho estrito
        SeveridadeValidacao.AVISO -> Color(0xFFFFB300)  // Amarelo atenção
        else -> if (selecionado != null) Color(0xFF43A047) else Color.Gray.copy(alpha = 0.5f) // Verde ou Neutro
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (containerColor != Color.Unspecified) containerColor 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, fontSize = 10.sp, color = Color.Gray)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Círculo indicador de conformidade das travas
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Botão Sugestor Inteligente
                    IconButton(
                        onClick = onSuggest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Sugestão inteligente",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Selector customizado que abre um simples dialog de escolha
            var showSelectDialog by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { showSelectDialog = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selecionado?.nome ?: "Selecionar irmão...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selecionado != null) MaterialTheme.colorScheme.onSurface else Color.Gray
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }

            // Exibir erro localizado da trava
            piorAlerta?.let { alert ->
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (alert.severidade == SeveridadeValidacao.ERRO) Icons.Default.Close else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = alert.mensagem,
                        fontSize = 10.sp,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 12.sp
                    )
                }
            }

            if (showSelectDialog) {
                SimpleSelectDialog(
                    title = title,
                    publicadores = publicadores,
                    onDismiss = { showSelectDialog = false },
                    onSelect = { selectedId ->
                        onSelect(selectedId)
                        showSelectDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun EstudanteCardItem(
    num: Int,
    apresentadorId: Int?,
    ajudanteId: Int?,
    formato: FormatoParteEstudante,
    publicadores: List<Publicador>,
    alertas: List<RelatorioValidacao>,
    onSelectDono: (Int?) -> Unit,
    onSelectAjudante: (Int?) -> Unit,
    onSelectFormato: (FormatoParteEstudante) -> Unit,
    onSuggestDono: () -> Unit,
    onSuggestAjudante: () -> Unit,
    tema: String,
    onTemaChange: (String) -> Unit,
    temaOptions: List<String> = emptyList(),
    containerColor: Color = Color.Unspecified,
    isCustom: Boolean = false,
    titleOverride: String? = null
) {
    var showSelectDono by remember { mutableStateOf(false) }
    var showSelectAjudante by remember { mutableStateOf(false) }
    var showThemeDropdown by remember { mutableStateOf(false) }

    val pubMap = publicadores.associateBy { it.id }
    val dono = pubMap[apresentadorId]
    val ajudante = pubMap[ajudanteId]

    val parteKey = "MINISTERIO_ESTUDANTE_$num"
    val campoDono = "estudante${num}ApresentadorId"
    val campoAju = "estudante${num}AjudanteId"

    val alertaDono = alertas.find { it.tipoParte == parteKey && it.campo == campoDono }
    val alertaAju = alertas.find { it.tipoParte == parteKey && it.campo == campoAju }

    val resolvedColor = if (containerColor != Color.Unspecified) containerColor else MaterialTheme.colorScheme.surface

    // Determinação automática do formato e banner baseado no tema/customização
    val isDiscurso = tema == "Discurso"
    val isOQueVoceDiria = tema == "O que você diria?"
    val bannerText = when {
        isCustom || isOQueVoceDiria -> "Consideração"
        isDiscurso -> "Discurso direto"
        else -> "Demonstração"
    }
    val bannerBg = when (bannerText) {
        "Consideração" -> Color(0xFFF3EAFF)
        "Discurso direto" -> Color(0xFFEADDFF)
        else -> Color(0xFFE3F2FD)
    }
    val bannerFg = when (bannerText) {
        "Consideração" -> Color(0xFF6750A4)
        "Discurso direto" -> Color(0xFF21005D)
        else -> Color(0xFF0D47A1)
    }
    val requerAjudante = bannerText == "Demonstração"

    // Limpar o ajudante via efeito colateral se o formato passar a não requerer ajudante
    LaunchedEffect(requerAjudante) {
        if (!requerAjudante && ajudanteId != null) {
            onSelectAjudante(null)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = resolvedColor),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleOverride ?: "Estudante $num",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF202124)
                )

                // Banner de Formato Dinâmico Elegante (Unificado e sem submenus)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bannerBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    val bannerIcon = when (bannerText) {
                        "Demonstração" -> "👥"
                        "Discurso direto" -> "🗣️"
                        "Consideração" -> "💬"
                        else -> "📝"
                    }
                    Text(
                        text = "$bannerIcon $bannerText",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = bannerFg
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tema Dropdown or Custom Theme input
            if (isCustom) {
                OutlinedTextField(
                    value = tema,
                    onValueChange = onTemaChange,
                    label = { Text("Tema Personalizado da Parte") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFBE9F67),
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    singleLine = true
                )
            } else if (temaOptions.isNotEmpty()) {
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .clickable { showThemeDropdown = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tema.isEmpty()) "Definir Tema da Parte..." else "Tema: $tema",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF202124)
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray)
                    }

                    DropdownMenu(
                        expanded = showThemeDropdown,
                        onDismissRequest = { showThemeDropdown = false }
                    ) {
                        temaOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, fontSize = 13.sp) },
                                onClick = {
                                    onTemaChange(option)
                                    showThemeDropdown = false
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Tema: $tema",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF202124),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Campo Apresentador (Dono)
            Text("Estudante (Apresentador)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3C4043))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color.White)
                        .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .clickable { showSelectDono = true }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(dono?.nome ?: "Selecionar estudante...", fontSize = 13.sp, color = if (dono != null) Color(0xFF202124) else Color.Gray)
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onSuggestDono, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFF34727D), modifier = Modifier.size(18.dp))
                }
            }
            alertaDono?.let {
                Text(it.mensagem, fontSize = 9.sp, color = if (it.severidade == SeveridadeValidacao.ERRO) Color(0xFFE53935) else Color(0xFFFFB300), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Campo Ajudante
            if (requerAjudante) {
                Text("Ajudante (Mesmo gênero ou cônjuge/parente)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF3C4043))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White)
                            .border(1.dp, Color.Gray.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .clickable { showSelectAjudante = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ajudante?.nome ?: "Selecionar ajudante...", fontSize = 13.sp, color = if (ajudante != null) Color(0xFF202124) else Color.Gray)
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onSuggestAjudante, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, null, tint = Color(0xFF34727D), modifier = Modifier.size(18.dp))
                    }
                }
                alertaAju?.let {
                    Text(it.mensagem, fontSize = 9.sp, color = if (it.severidade == SeveridadeValidacao.ERRO) Color(0xFFE53935) else Color(0xFFFFB300), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
                
                // Indicação de parentesco isento aprovado
                if (dono != null && ajudante != null && dono.genero != ajudante.genero && RulesEngine.saoParentesDePrimeiroGrau(dono, ajudante)) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, null, tint = Color(0xFF912421), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Isenção Aplicada: Parentesco de 1º grau cadastrado (${dono.genero} e ${ajudante.genero}).", fontSize = 9.sp, color = Color(0xFF912421), fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Parte em formato Solo ($bannerText). Não requer ajudante.",
                        fontSize = 11.sp,
                        color = Color(0xFF5F6368),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }

    if (showSelectDono) {
        SimpleSelectDialog("Dono da Parte $num", publicadores, onDismiss = { showSelectDono = false }) { onSelectDono(it) }
    }
    if (showSelectAjudante) {
        SimpleSelectDialog("Ajudante Estudante $num", publicadores, onDismiss = { showSelectAjudante = false }) { onSelectAjudante(it) }
    }
}

@Composable
fun FormatoTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Painel informativo central agrupando todos os alertas de travas configurados no Admin
 */
@Composable
fun AlertaStatusCard(alertas: List<RelatorioValidacao>) {
    val erros = alertas.filter { it.severidade == SeveridadeValidacao.ERRO }
    val avisos = alertas.filter { it.severidade == SeveridadeValidacao.AVISO }

    val statusColor = when {
        erros.isNotEmpty() -> Color(0xFFD32F2F)   // Vermelho estrito
        avisos.isNotEmpty() -> Color(0xFFF57C00)  // Amarelo atenção
        else -> Color(0xFF388E3C)                 // Verde
    }

    val statusTitle = when {
        erros.isNotEmpty() -> "Violou travamentos da escala (${erros.size} erros)"
        avisos.isNotEmpty() -> "Programação em conformidade com avisos (${avisos.size} alertas)"
        else -> "Programação em conformidade perfeita!"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = when {
                        erros.isNotEmpty() -> Icons.Default.Close
                        avisos.isNotEmpty() -> Icons.Default.Warning
                        else -> Icons.Default.CheckCircle
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = statusTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                    Text(
                        text = if (erros.isEmpty() && avisos.isEmpty()) "Parabéns! Nenhuma regra ou restrição do Painel Administrativo foi violada." else "Verifique os detalhes das validações inteligentes abaixo:",
                        fontSize = 11.sp,
                        color = Color.DarkGray
                    )
                }
            }

            if (alertas.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = statusColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                alertas.forEach { alert ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (alert.severidade == SeveridadeValidacao.ERRO) Color.Red else Color(0xFFFFA000))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = alert.mensagem,
                            fontSize = 10.sp,
                            color = Color.DarkGray,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SELEÇÃO MANUAL - DIALOG SIMPLES
// ==========================================
@Composable
fun SimpleSelectDialog(
    title: String,
    publicadores: List<Publicador>,
    onDismiss: () -> Unit,
    onSelect: (Int?) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Designar: $title",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(null) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Clear, null, tint = Color.Gray)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("DESMARCAR DESIGNADO", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    items(publicadores) { pub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(pub.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pub.nome, fontSize = 14.sp)
                            
                            // Badge de Perfil
                            ProfileSimpleBadge(perfil = pub.perfil)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

// Badge auxiliar
@Composable
fun ProfileSimpleBadge(perfil: PerfilPublicador) {
    val bColor = when (perfil) {
        PerfilPublicador.ANCIAO -> Color(0xFF1E88E5)
        PerfilPublicador.SERVO_MINISTERIAL -> Color(0xFF03A9F4)
        PerfilPublicador.IRMAO_BATIZADO -> Color(0xFF4CAF50)
        PerfilPublicador.IRMAO_NAO_BATIZADO -> Color(0xFF8BC34A)
        PerfilPublicador.IRMA -> Color(0xFFE91E63)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = when (perfil) {
                PerfilPublicador.ANCIAO -> "Ancião"
                PerfilPublicador.SERVO_MINISTERIAL -> "Servo M."
                PerfilPublicador.IRMAO_BATIZADO -> "Irmão Bat."
                PerfilPublicador.IRMAO_NAO_BATIZADO -> "Não Bat."
                PerfilPublicador.IRMA -> "Irmã"
            },
            fontSize = 9.sp,
            color = bColor,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// SUGESTOR INTELIGENTE - DIALOG POPUP
// ==========================================
@Composable
fun CandidatosDialog(
    campo: String,
    formatoEstudante: FormatoParteEstudante?,
    apresentadorEstudanteId: Int?,
    publicadores: List<Publicador>,
    programacao: ProgramacaoSemana,
    regras: PainelRegrasConfig,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val sugestoes = remember(campo, formatoEstudante, apresentadorEstudanteId, publicadores, programacao, regras) {
        RulesEngine.sugerirCandidatos(
            campo = campo,
            formatoEstudante = formatoEstudante,
            apresentadorEstudanteId = apresentadorEstudanteId,
            publicadores = publicadores,
            programacao = programacao,
            regras = regras
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 450.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, "Sugestor", tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Ordem de Sugestão e Rodízio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Abaixo estão os irmãos elegíveis qualificados, calculados por prioridade automática de rodízio.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
                Divider()
                Spacer(modifier = Modifier.height(10.dp))

                if (sugestoes.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Abominação: Ninguém atende às travas para este perfil no Corpo registrado!", fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(sugestoes) { pair ->
                            val (pub, motivo) = pair
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onSelect(pub.id) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(pub.nome, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            ProfileSimpleBadge(perfil = pub.perfil)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = motivo,
                                            fontSize = 9.sp,
                                            color = if (motivo.contains("Alerta") || motivo.contains("Evitar")) Color(0xFFC62828) else Color(0xFF2E7D32),
                                            lineHeight = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Selecionar",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}

// ==========================================
// ABA 2: CADASTRO DO CORPO DE PUBLICADORES
// ==========================================
@Composable
fun CadastroPublicadoresTab(
    publicadores: List<Publicador>,
    onAdd: (Publicador) -> Unit,
    onDelete: (Int) -> Unit,
    onRestore: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var publicadorToEdit by remember { mutableStateOf<Publicador?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Banco de Publicadores (${publicadores.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row {
                IconButton(onClick = onRestore, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, "Zerar e povoar", tint = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { 
                        publicadorToEdit = null
                        showAddDialog = true 
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adicionar", fontSize = 12.sp)
                }
            }
        }

        if (publicadores.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Nenhum publicador cadastrado.", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = onRestore) {
                        Text("Carregar Lista de Exemplo")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(publicadores) { pub ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                publicadorToEdit = pub
                                showAddDialog = true
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(pub.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    ProfileSimpleBadge(perfil = pub.perfil)
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Gênero: ${pub.genero.name} | Partes no Histórico: ${pub.historicoPartes.size}", fontSize = 11.sp, color = Color.Gray)
                                
                                // Servo aprovação
                                if (pub.perfil == PerfilPublicador.SERVO_MINISTERIAL) {
                                    val statusString = if (pub.servoDirigenteAprovado) "Aprovado para dirigir estudo" else "Apenas leitura e joias"
                                    ListItemTag(label = "Servo: $statusString", color = if (pub.servoDirigenteAprovado) Color(0xFF2E7D32) else Color.Gray)
                                }

                                // Parentescos cadastrados
                                if (pub.parentescos.isNotEmpty()) {
                                    val relativeNames = pub.parentescos.map { rel ->
                                        val parente = publicadores.find { it.id == rel.parenteId }
                                        val grauTxt = when (rel.grau) {
                                            GrauParentesco.CONJUGE -> "Cônjuge"
                                            GrauParentesco.PAI -> "Pai"
                                            GrauParentesco.MAE -> "Mãe"
                                            GrauParentesco.FILHO_FILHA -> "Filho(a)"
                                            GrauParentesco.IRMAO_IRMA -> "Irmão/Irmã"
                                        }
                                        "${parente?.nome ?: "ID ${rel.parenteId}"} ($grauTxt)"
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text("Familiar (1º Grau): ${relativeNames.joinToString(", ")}", fontSize = 10.sp, color = Color(0xFFC2185B), fontWeight = FontWeight.SemiBold)
                                }
                            }

                            IconButton(onClick = { onDelete(pub.id) }) {
                                Icon(Icons.Default.Delete, "Remover", tint = Color(0xFFC62828))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPublicadorDialog(
            publicadores = publicadores,
            publicadorToEdit = publicadorToEdit,
            onDismiss = { 
                showAddDialog = false
                publicadorToEdit = null
            },
            onSave = { pub ->
                onAdd(pub)
                showAddDialog = false
                publicadorToEdit = null
            }
        )
    }
}

@Composable
fun ListItemTag(label: String, color: Color) {
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

// ==========================================
// CADASTRAR/EDITAR PUBLICADOR - DIALOG POPUP (MÚLTIPLOS REBALSES)
// ==========================================
@Composable
fun AddPublicadorDialog(
    publicadores: List<Publicador>,
    publicadorToEdit: Publicador? = null,
    onDismiss: () -> Unit,
    onSave: (Publicador) -> Unit
) {
    var nome by remember { mutableStateOf(publicadorToEdit?.nome ?: "") }
    var genero by remember { mutableStateOf(publicadorToEdit?.genero ?: Genero.MASCULINO) }
    var perfil by remember { mutableStateOf(publicadorToEdit?.perfil ?: PerfilPublicador.IRMAO_BATIZADO) }
    var servoDirigente by remember { mutableStateOf(publicadorToEdit?.servoDirigenteAprovado ?: false) }
    
    // Lista dinâmica de vínculos múltiplos
    var parentescosList by remember { mutableStateOf(publicadorToEdit?.parentescos ?: emptyList<RelacaoParentesco>()) }
    
    // Formulário inline expansível para adicionar novo vínculo
    var showFormAddRelacao by remember { mutableStateOf(false) }
    var novoParenteId by remember { mutableStateOf<Int?>(null) }
    var novoGrau by remember { mutableStateOf(GrauParentesco.CONJUGE) }
    
    var showParenteDropdown by remember { mutableStateOf(false) }

    fun getGrauLabel(grau: GrauParentesco): String {
        return when (grau) {
            GrauParentesco.CONJUGE -> "Cônjuge"
            GrauParentesco.PAI -> "Pai"
            GrauParentesco.MAE -> "Mãe"
            GrauParentesco.FILHO_FILHA -> "Filho(a)"
            GrauParentesco.IRMAO_IRMA -> "Irmão/Irmã"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (publicadorToEdit != null) "Editar Publicador" else "Cadastrar Publicador",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome Completo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text("Gênero", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = genero == Genero.MASCULINO, onClick = { genero = Genero.MASCULINO })
                    Text("Masculino", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = genero == Genero.FEMININO, onClick = { genero = Genero.FEMININO })
                    Text("Feminino (Irmã)", fontSize = 13.sp)
                }

                // Auto ajustar perfil se feminino
                LaunchedEffect(genero) {
                    if (genero == Genero.FEMININO) {
                        perfil = PerfilPublicador.IRMA
                    } else if (perfil == PerfilPublicador.IRMA) {
                        perfil = PerfilPublicador.IRMAO_BATIZADO
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Spiritual Profile", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (genero == Genero.MASCULINO) {
                    Column {
                        PerfilPublicador.values().filter { it != PerfilPublicador.IRMA }.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = perfil == p, onClick = { perfil = p })
                                Text(p.name, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("Perfil automático para Irmã.", fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    }
                }

                if (perfil == PerfilPublicador.SERVO_MINISTERIAL) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = servoDirigente, onCheckedChange = { servoDirigente = it })
                        Text("Aprovado pelo Corpo para conduzir Estudo Bíblico", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // VÍNCULOS FAMILIARES MÚLTIPLOS (SEÇÃO DINÂMICA)
                Text("Vínculos Familiares Múltiplos (1º Grau)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFC2185B))
                Spacer(modifier = Modifier.height(6.dp))

                if (parentescosList.isEmpty()) {
                    Text(
                        text = "Nenhum vínculo familiar cadastrado. Adicione para evitar conflitos de reunião.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        parentescosList.forEach { rel ->
                            val parente = publicadores.find { it.id == rel.parenteId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFFF0F2))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("🤝", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(parente?.nome ?: "ID: ${rel.parenteId}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                                        Text(getGrauLabel(rel.grau), fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        parentescosList = parentescosList.filter { it.parenteId != rel.parenteId }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover Vínculo", tint = Color(0xFFC62828), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                // Expandir formulário de novo vínculo
                if (!showFormAddRelacao) {
                    OutlinedButton(
                        onClick = { showFormAddRelacao = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFFC2185B)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC2185B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar Vínculo Familiar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF5F7)),
                        border = BorderStroke(1.dp, Color(0xFFFFD1DC))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Novo Vínculo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showParenteDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    val selecionado = publicadores.find { it.id == novoParenteId }
                                    Text(selecionado?.nome ?: "Selecionar Parente", fontSize = 12.sp)
                                }
                                
                                val elegiveis = publicadores.filter { 
                                    it.id != (publicadorToEdit?.id ?: 0) && parentescosList.none { rel -> rel.parenteId == it.id }
                                }
                                
                                DropdownMenu(expanded = showParenteDropdown, onDismissRequest = { showParenteDropdown = false }) {
                                    if (elegiveis.isEmpty()) {
                                        DropdownMenuItem(text = { Text("Nenhum irmão elegível") }, onClick = {})
                                    } else {
                                        elegiveis.forEach { pub ->
                                            DropdownMenuItem(
                                                text = { Text(pub.nome) },
                                                onClick = {
                                                    novoParenteId = pub.id
                                                    showParenteDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Grau de Parentesco", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            
                            Column {
                                GrauParentesco.values().forEach { gp ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { novoGrau = gp }
                                    ) {
                                        RadioButton(
                                            selected = novoGrau == gp,
                                            onClick = { novoGrau = gp },
                                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFC2185B))
                                        )
                                        Text(getGrauLabel(gp), fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { 
                                        showFormAddRelacao = false
                                        novoParenteId = null
                                    }
                                ) {
                                    Text("Cancelar", fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (novoParenteId != null) {
                                            parentescosList = parentescosList + RelacaoParentesco(novoParenteId!!, novoGrau)
                                            novoParenteId = null
                                            showFormAddRelacao = false
                                        }
                                    },
                                    enabled = novoParenteId != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC2185B)),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Confirmar", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (nome.isNotEmpty()) {
                                onSave(
                                    Publicador(
                                        id = publicadorToEdit?.id ?: 0,
                                        nome = nome,
                                        genero = genero,
                                        perfil = perfil,
                                        servoDirigenteAprovado = servoDirigente,
                                        parentescos = parentescosList,
                                        historicoPartes = publicadorToEdit?.historicoPartes ?: emptyList()
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

// ==========================================
// ABA 3: PAINEL DE CONFIGURAÇÃO DE REGRAS (SISTEMA DINÂMICO DE REGRAS PERSONALIZADAS E EXCLUSÃO)
// ==========================================
@Composable
fun RuleCardItem(
    title: String,
    subtitle: String,
    isActive: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    isCustom: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCustom) Color(0xFFF7F2FA) else Color.White),
        border = BorderStroke(1.dp, if (isCustom) Color(0xFF6750A4).copy(alpha = 0.3f) else Color(0xFFEADDFF).copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isCustom) Color(0xFFE8DEF8) else Color(0xFFECE6F0),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isCustom) "PERSONALIZADA" else "PADRÃO",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCustom) Color(0xFF21005D) else Color(0xFF49454F)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF1C1B1F))
                Text(subtitle, fontSize = 11.sp, color = Color(0xFF49454F), lineHeight = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = isActive,
                    onCheckedChange = onActiveChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6750A4),
                        uncheckedThumbColor = Color(0xFF938F99),
                        uncheckedTrackColor = Color(0xFFE7E0EC)
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Excluir Regra",
                        tint = Color(0xFFB3261E)
                    )
                }
            }
        }
    }
}

@Composable
fun ConfiguracoesPainelTab(
    regras: PainelRegrasConfig,
    onSave: (PainelRegrasConfig) -> Unit
) {
    var permAcumulo by remember { mutableStateOf(regras.permitirAcumulo) }
    var permConsecutivo by remember { mutableStateOf(regras.permitirSemanasConsecutivasTribuna) }
    var altPapeis by remember { mutableStateOf(regras.alternarPapeisEstudante) }
    var evitDupla by remember { mutableStateOf(regras.evitarDuplaEstudoRepetidaAnterior) }
    var regraGeneroAtiva by remember { mutableStateOf(regras.regraGeneroAtiva) }

    // Listas dinâmicas de regras
    var regrasPersonalizadas by remember { mutableStateOf(regras.regrasPersonalizadas) }
    var regrasPadraoOcultadas by remember { mutableStateOf(regras.regrasPadraoOcultadas) }

    // Matriz de Privilégios Dinâmica
    var mcDiscurso by remember { mutableStateOf(regras.matriz.allowedDiscursoJoias) }
    var mcLeitura by remember { mutableStateOf(regras.matriz.allowedLeituraBiblia) }
    var mcEstudante by remember { mutableStateOf(regras.matriz.allowedEstudanteApresentador) }
    var mcEstudanteDisc by remember { mutableStateOf(regras.matriz.allowedEstudanteDiscurso) }
    var mcDirigente by remember { mutableStateOf(regras.matriz.allowedDirigenteEstudo) }
    var mcLeitor by remember { mutableStateOf(regras.matriz.allowedLeitorEstudo) }

    val scrollState = rememberScrollState()

    // Dialog form state
    var showCreateDialog by remember { mutableStateOf(false) }
    var ruleNome by remember { mutableStateOf("") }
    var selectedParteKey by remember { mutableStateOf("tesourosLeituraId") }
    var selectedParteBloqueadaKey by remember { mutableStateOf("tesourosDiscursoId") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var dropdownBlockedExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Sincronizar ao carregar
    LaunchedEffect(regras) {
        permAcumulo = regras.permitirAcumulo
        permConsecutivo = regras.permitirSemanasConsecutivasTribuna
        altPapeis = regras.alternarPapeisEstudante
        evitDupla = regras.evitarDuplaEstudoRepetidaAnterior
        regraGeneroAtiva = regras.regraGeneroAtiva
        regrasPersonalizadas = regras.regrasPersonalizadas
        regrasPadraoOcultadas = regras.regrasPadraoOcultadas
        mcDiscurso = regras.matriz.allowedDiscursoJoias
        mcLeitura = regras.matriz.allowedLeituraBiblia
        mcEstudante = regras.matriz.allowedEstudanteApresentador
        mcEstudanteDisc = regras.matriz.allowedEstudanteDiscurso
        mcDirigente = regras.matriz.allowedDirigenteEstudo
        mcLeitor = regras.matriz.allowedLeitorEstudo
    }

    val partesDisponiveis = listOf(
        "presidenteId" to "Presidente da Reunião",
        "oracaoInicialId" to "Oração Inicial",
        "tesourosDiscursoId" to "Discurso (Tesouros)",
        "tesourosJoiasId" to "Joias Espirituais",
        "tesourosLeituraId" to "Leitura da Bíblia",
        "estudante1ApresentadorId" to "Estudante 1 (Apresentador)",
        "estudante1AjudanteId" to "Estudante 1 (Ajudante)",
        "estudante2ApresentadorId" to "Estudante 2 (Apresentador)",
        "estudante2AjudanteId" to "Estudante 2 (Ajudante)",
        "estudante3ApresentadorId" to "Estudante 3 (Apresentador)",
        "estudante3AjudanteId" to "Estudante 3 (Ajudante)",
        "estudante4ApresentadorId" to "Estudante 4 (Apresentador)",
        "estudante4AjudanteId" to "Estudante 4 (Ajudante)",
        "vidaParteLocal1Id" to "Parte Local 1 (Vida Cristã)",
        "vidaParteLocal2Id" to "Parte Local 2 (Vida Cristã)",
        "vidaEstudoDirigenteId" to "Estudo Bíblico (Dirigente)",
        "vidaEstudoLeitorId" to "Estudo Bíblico (Leitor)",
        "oracaoFinalId" to "Oração Final"
    )

    val perfisDisponiveis = listOf(
        PerfilPublicador.ANCIAO to "Ancião",
        PerfilPublicador.SERVO_MINISTERIAL to "Servo Ministerial",
        PerfilPublicador.IRMAO_BATIZADO to "Irmão Batizado",
        PerfilPublicador.IRMAO_NAO_BATIZADO to "Irmão Não Batizado",
        PerfilPublicador.IRMA to "Irmã"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // --- CABEÇALHO & BOTÃO DE NOVA REGRA ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Controle das Regras de Negócio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                Text(
                    text = "Toggles avançados, exclusão e regras personalizadas.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            Button(
                onClick = {
                    ruleNome = ""
                    selectedParteKey = "tesourosLeituraId"
                    selectedParteBloqueadaKey = "tesourosDiscursoId"
                    showCreateDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Criar Regra", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- SEÇÃO 1: REGRAS EXISTENTES ---
        Text(
            text = "Lista de Regras Ativas",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF6750A4)
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Regra de Acúmulo
        if (!regrasPadraoOcultadas.contains("std_acumulo")) {
            RuleCardItem(
                title = "Regra de Acúmulo Saudável",
                subtitle = "Limita que o mesmo publicador faça mais de uma parte na mesma noite.",
                isActive = !permAcumulo,
                onActiveChange = { permAcumulo = !it },
                onDelete = {
                    regrasPadraoOcultadas = regrasPadraoOcultadas + "std_acumulo"
                    Toast.makeText(context, "Regra de acúmulo desativada e excluída.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 2. Regra de Consecutivas na Tribuna
        if (!regrasPadraoOcultadas.contains("std_consecutivo")) {
            RuleCardItem(
                title = "Semana Consecutiva na Tribuna",
                subtitle = "Evita que anciãos/servos sejam escalados de maneira consecutiva.",
                isActive = !permConsecutivo,
                onActiveChange = { permConsecutivo = !it },
                onDelete = {
                    regrasPadraoOcultadas = regrasPadraoOcultadas + "std_consecutivo"
                    Toast.makeText(context, "Regra de tribuna consecutiva excluída.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 3. Regra de Alternância
        if (!regrasPadraoOcultadas.contains("std_alternancia")) {
            RuleCardItem(
                title = "Alternância de Trabalho de Estudante",
                subtitle = "Prioriza alternar quem foi Apresentador para ser Ajudante na reunião seguinte.",
                isActive = altPapeis,
                onActiveChange = { altPapeis = it },
                onDelete = {
                    regrasPadraoOcultadas = regrasPadraoOcultadas + "std_alternancia"
                    Toast.makeText(context, "Regra de alternância excluída.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 4. Regra de Dupla
        if (!regrasPadraoOcultadas.contains("std_dupla")) {
            RuleCardItem(
                title = "Dupla de Estudo Saudável",
                subtitle = "Evita repetir a relação Dirigente + Leitor do estudo da semana anterior.",
                isActive = evitDupla,
                onActiveChange = { evitDupla = it },
                onDelete = {
                    regrasPadraoOcultadas = regrasPadraoOcultadas + "std_dupla"
                    Toast.makeText(context, "Regra de dupla excluída.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // 5. Regra de Gênero
        if (!regrasPadraoOcultadas.contains("std_genero")) {
            RuleCardItem(
                title = "Gênero Uniforme de Estudantes/Ajudantes",
                subtitle = "Exige que o ajudante seja do mesmo gênero do estudante (isenta parentesco próximo).",
                isActive = regraGeneroAtiva,
                onActiveChange = { regraGeneroAtiva = it },
                onDelete = {
                    regrasPadraoOcultadas = regrasPadraoOcultadas + "std_genero"
                    Toast.makeText(context, "Regra de gênero excluída.", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // --- REGRAS PERSONALIZADAS ---
        regrasPersonalizadas.forEachIndexed { index, rule ->
            val parteLabel = partesDisponiveis.find { it.first == rule.parte }?.second ?: rule.parte
            val parteBloqueadaLabel = partesDisponiveis.find { it.first == rule.parteBloqueada }?.second ?: rule.parteBloqueada
            val desc = "Se designado para '$parteLabel' -> Não pode fazer '$parteBloqueadaLabel' na mesma semana."
            
            RuleCardItem(
                title = rule.nome,
                subtitle = desc,
                isActive = rule.ativa,
                onActiveChange = { checked ->
                    regrasPersonalizadas = regrasPersonalizadas.toMutableList().apply {
                        this[index] = rule.copy(ativa = checked)
                    }
                },
                onDelete = {
                    regrasPersonalizadas = regrasPersonalizadas.toMutableList().apply {
                        removeAt(index)
                    }
                    Toast.makeText(context, "Regra personalizada excluída.", Toast.LENGTH_SHORT).show()
                },
                isCustom = true
            )
        }

        // Se houver regras padrão ocultadas, exibe botão de restauração
        if (regrasPadraoOcultadas.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    regrasPadraoOcultadas = emptyList()
                    Toast.makeText(context, "Regras padrão restauradas!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restaurar Regras Padrão Excluídas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Divider()
        Spacer(modifier = Modifier.height(12.dp))

        // --- GESTÃO DINÂMICA DA MATRIZ DE PERFIS ---
        Text(
            text = "Matriz de Privilégios (Mapeamento Base)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F)
        )
        Text("Gerencie quais perfis espirituais são válidos por parte da reunião. Se desativado, gera erros.", fontSize = 11.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(8.dp))

        MatrixPartRow(
            partName = "Discurso / Joias de Tesouros",
            allowed = mcDiscurso,
            onToggle = { p -> mcDiscurso = toggleProfile(mcDiscurso, p) }
        )

        MatrixPartRow(
            partName = "Leitura da Bíblia (Exclui Ancião)",
            allowed = mcLeitura,
            onToggle = { p -> mcLeitura = toggleProfile(mcLeitura, p) }
        )

        MatrixPartRow(
            partName = "Estudante (Geral / Demonstração)",
            allowed = mcEstudante,
            onToggle = { p -> mcEstudante = toggleProfile(mcEstudante, p) }
        )

        MatrixPartRow(
            partName = "Estudante (Discurso Solo)",
            allowed = mcEstudanteDisc,
            onToggle = { p -> mcEstudanteDisc = toggleProfile(mcEstudanteDisc, p) }
        )

        MatrixPartRow(
            partName = "Dirigente de Estudo",
            allowed = mcDirigente,
            onToggle = { p -> mcDirigente = toggleProfile(mcDirigente, p) }
        )

        MatrixPartRow(
            partName = "Leitor de Estudo",
            allowed = mcLeitor,
            onToggle = { p -> mcLeitor = toggleProfile(mcLeitor, p) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                onSave(
                    PainelRegrasConfig(
                        permitirAcumulo = permAcumulo,
                        permitirSemanasConsecutivasTribuna = permConsecutivo,
                        alternarPapeisEstudante = altPapeis,
                        evitarDuplaEstudoRepetidaAnterior = evitDupla,
                        matriz = MatrizPermissoes(
                            allowedDiscursoJoias = mcDiscurso,
                            allowedLeituraBiblia = mcLeitura,
                            allowedEstudanteApresentador = mcEstudante,
                            allowedEstudanteDiscurso = mcEstudanteDisc,
                            allowedDirigenteEstudo = mcDirigente,
                            allowedLeitorEstudo = mcLeitor
                        ),
                        regrasPersonalizadas = regrasPersonalizadas,
                        regrasPadraoOcultadas = regrasPadraoOcultadas,
                        regraGeneroAtiva = regraGeneroAtiva
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("SALVAR CONFIGURAÇÃO DAS REGRAS", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }

    // --- DIALOG DE CRIAR REGRA PERSONALIZADA ---
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Nova Regra Personalizada",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1B1F)
                    )
                    Text(
                        text = "Evite que o mesmo publicador faça duas partes específicas na mesma reunião.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Campo 1: Nome
                    OutlinedTextField(
                        value = ruleNome,
                        onValueChange = { ruleNome = it },
                        label = { Text("Nome da Regra (Ex: Trava Leitura + Discurso)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Campo 2: Dropdown de Parte 1
                    Text(
                        text = "Se a parte for:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownExpanded = true },
                            border = BorderStroke(1.dp, Color(0xFF79747E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = partesDisponiveis.find { it.first == selectedParteKey }?.second ?: "Selecione a parte...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Black
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expandir"
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            partesDisponiveis.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedParteKey = key
                                        dropdownExpanded = false
                                    }
                               )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Campo 3: Dropdown de Parte 2 (Não pode ser)
                    Text(
                        text = "Não pode ser:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dropdownBlockedExpanded = true },
                            border = BorderStroke(1.dp, Color(0xFF79747E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = partesDisponiveis.find { it.first == selectedParteBloqueadaKey }?.second ?: "Selecione a parte impedida...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Black
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expandir"
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = dropdownBlockedExpanded,
                            onDismissRequest = { dropdownBlockedExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            partesDisponiveis.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedParteBloqueadaKey = key
                                        dropdownBlockedExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Botões de Ação do Dialog
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Fechar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (ruleNome.isBlank()) {
                                    Toast.makeText(context, "Insira um nome para a regra.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (selectedParteKey == selectedParteBloqueadaKey) {
                                    Toast.makeText(context, "A parte de origem e a de destino não podem ser as mesmas.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val novaRegra = CustomRule(
                                    id = java.util.UUID.randomUUID().toString(),
                                    nome = ruleNome,
                                    parte = selectedParteKey,
                                    parteBloqueada = selectedParteBloqueadaKey,
                                    ativa = true
                                )
                                regrasPersonalizadas = regrasPersonalizadas + novaRegra
                                showCreateDialog = false
                                Toast.makeText(context, "Regra personalizada criada!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Adicionar Regra")
                        }
                    }
                }
            }
        }
    }
}

private fun toggleProfile(current: List<PerfilPublicador>, profile: PerfilPublicador): List<PerfilPublicador> {
    return if (current.contains(profile)) {
        current.filter { it != profile }
    } else {
        current + profile
    }
}

@Composable
fun ConfigToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEADDFF).copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                Text(subtitle, fontSize = 10.sp, color = Color(0xFF49454F), lineHeight = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF6750A4),
                    uncheckedThumbColor = Color(0xFF938F99),
                    uncheckedTrackColor = Color(0xFFE7E0EC)
                )
            )
        }
    }
}

@Composable
fun MatrixPartRow(
    partName: String,
    allowed: List<PerfilPublicador>,
    onToggle: (PerfilPublicador) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEADDFF).copy(alpha = 0.8f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(partName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
            Spacer(modifier = Modifier.height(8.dp))
            
            // Chips para cada Perfil
            Column {
                val profiles = PerfilPublicador.values()
                Row(modifier = Modifier.fillMaxWidth()) {
                    profiles.take(3).forEach { p ->
                        val isAllowed = allowed.contains(p)
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAllowed) Color(0xFF6750A4) else Color(0xFFE7E0EC).copy(alpha = 0.6f))
                                .clickable { onToggle(p) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.name,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAllowed) Color.White else Color(0xFF49454F)
                            )
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    profiles.drop(3).forEach { p ->
                        val isAllowed = allowed.contains(p)
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .weight(1.5f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAllowed) Color(0xFF6750A4) else Color(0xFFE7E0EC).copy(alpha = 0.6f))
                                .clickable { onToggle(p) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.name,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAllowed) Color.White else Color(0xFF49454F)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavItem(
    icon: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE8DEF8))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp, modifier = Modifier.alpha(0.5f))
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color(0xFF1D192B) else Color(0xFF1D192B).copy(alpha = 0.5f)
        )
    }
}

@Composable
fun RelatorioOverviewTab(
    publicadores: List<Publicador>,
    regras: PainelRegrasConfig,
    alertas: List<RelatorioValidacao>
) {
    val scrollState = rememberScrollState()
    
    val qtdAnciaos = publicadores.count { it.perfil == PerfilPublicador.ANCIAO }
    val qtdServos = publicadores.count { it.perfil == PerfilPublicador.SERVO_MINISTERIAL }
    val qtdIrmaos = publicadores.count { it.perfil == PerfilPublicador.IRMAO_BATIZADO || it.perfil == PerfilPublicador.IRMAO_NAO_BATIZADO }
    val qtdIrmas = publicadores.count { it.perfil == PerfilPublicador.IRMA }
    
    val qtdMulheres = publicadores.count { it.genero == Genero.FEMININO }
    val qtdHomens = publicadores.count { it.genero == Genero.MASCULINO }

    val erros = alertas.filter { it.severidade == SeveridadeValidacao.ERRO }
    val avisos = alertas.filter { it.severidade == SeveridadeValidacao.AVISO }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card de Visão Geral Segurança
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEADDFF))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MATRIZ DE CONFORMIDADE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4),
                        letterSpacing = 1.sp
                    )
                    
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (erros.isNotEmpty()) Color(0xFFFDD835).copy(alpha = 0.2f) else Color(0xFFE8DEF8),
                                shape = RoundedCornerShape(100.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (erros.isNotEmpty()) "Ajuste pendente" else "Conformidade OK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (erros.isNotEmpty()) Color(0xFFF57F17) else Color(0xFF21005D)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Erros Ativos", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "${erros.size}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (erros.isNotEmpty()) Color(0xFFD32F2F) else Color(0xFF388E3C)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Avisos Emitidos", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "${avisos.size}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (avisos.isNotEmpty()) Color(0xFFF57C00) else Color(0xFF43A047)
                        )
                    }
                }
                
                if (erros.isNotEmpty() || avisos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Existem pendências de conflito ou acúmulo de tarefas na Agenda que violam as regras ativas de rodízio e equidade.",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F),
                        lineHeight = 15.sp
                    )
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Grade de escalonamento 100% otimizada! Nenhuma trava ativa violada na designação de hoje.",
                        fontSize = 11.sp,
                        color = Color(0xFF388E3C),
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Card de Estatísticas da Congregação (Pessoas / Equity Index)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEADDFF))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "VISÃO DA CONGREGAÇÃO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.1f)) {
                        Text("Perfis (Geral)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        StatRowLabelValue("Anciãos (ANC)", "$qtdAnciaos")
                        StatRowLabelValue("Servos Min. (SM)", "$qtdServos")
                        StatRowLabelValue("Irmãos comuns", "$qtdIrmaos")
                        StatRowLabelValue("Irmãs (IRM)", "$qtdIrmas")
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(modifier = Modifier.weight(0.9f)) {
                        Text("Gênero", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        StatRowLabelValue("Homens (Masc)", "$qtdHomens")
                        StatRowLabelValue("Mulheres (Fem)", "$qtdMulheres")
                        StatRowLabelValue("Total", "${publicadores.size}")
                    }
                }
            }
        }

        // Card de Parâmetros de Regras de Distribuição Ativas
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "REGRAS ATIVAS NO DISTRIBUIDOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                RuleStatusIndicator("Permitir acúmulo de tarefas na mesma noite", regras.permitirAcumulo)
                RuleStatusIndicator("Permitir semanas consecutivas na tribuna (Rodízio)", regras.permitirSemanasConsecutivasTribuna)
                RuleStatusIndicator("Inverter papéis de Dono da Parte / Ajudante", regras.alternarPapeisEstudante)
                RuleStatusIndicator("Bloquear de duplas repetidas anteriores", regras.evitarDuplaEstudoRepetidaAnterior)
            }
        }
    }
}

@Composable
fun StatRowLabelValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF49454F))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C1B1F))
    }
}

@Composable
fun RuleStatusIndicator(label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF49454F),
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (active) Color(0xFF43A047) else Color(0xFFB0BEC5))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (active) "Sim" else "Não",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) Color(0xFF43A047) else Color(0xFF78909C)
            )
        }
    }
}

// =========================================================================
// TELA E RECURSOS DE VISUALIZAÇÃO DA FOLHA DO QUADRO E EXPORTAÇÃO (PDF / MS WORD)
// =========================================================================

fun exportarPdfAgenda(
    context: android.content.Context,
    prog: ProgramacaoSemana,
    publicadores: List<Publicador>
) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        
        val paint = Paint()
        paint.isAntiAlias = true
        
        // Background white
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 0f, 595f, 842f, paint)
        
        // Helper to find names
        fun getNome(id: Int?): String = publicadores.find { it.id == id }?.nome ?: "---"
        
        // 1. Draw Centered Titles
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#6750A4")
            textSize = 20f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("PROGRAMAÇÃO DA REUNIÃO", 297.5f, 55f, titlePaint)
        
        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#49454F")
            textSize = 14f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Semana de ${prog.semana}", 297.5f, 78f, subtitlePaint)
        
        var currentY = 110f
        
        // Helpers for rows
        fun drawSection(title: String, colorHex: String) {
            val bgPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = android.graphics.Color.parseColor(colorHex)
                alpha = 30 // ~12% opacity
            }
            val rect = android.graphics.RectF(40f, currentY - 14f, 555f, currentY + 12f)
            canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
            
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor(colorHex)
                textSize = 10f
                isFakeBoldText = true
            }
            canvas.drawText(title, 48f, currentY + 3f, textPaint)
            currentY += 34f
        }
        
        fun drawRow(emoji: String, label: String, value: String, colorHex: String) {
            // Draw circle background
            val circlePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = android.graphics.Color.parseColor(colorHex)
                alpha = 25 // 10% opacity
            }
            canvas.drawCircle(52f, currentY - 4f, 10f, circlePaint)
            
            // Draw Emoji string
            val emojiPaint = Paint().apply {
                isAntiAlias = true
                textSize = 10f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(emoji, 52f, currentY - 1f, emojiPaint)
            
            // Draw Label
            val labelPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#37474F")
                textSize = 10f
                isFakeBoldText = true
            }
            
            val displayLabel = if (label.length > 55) label.substring(0, 52) + "..." else label
            canvas.drawText(displayLabel, 68f, currentY, labelPaint)
            
            // Draw Value (Right Aligned)
            val valuePaint = Paint().apply {
                isAntiAlias = true
                color = if (value == "---") android.graphics.Color.GRAY else android.graphics.Color.parseColor(colorHex)
                textSize = 11f
                isFakeBoldText = true
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(value, 555f, currentY, valuePaint)
            
            // Divider
            val divPaint = Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#EEEEEE")
                strokeWidth = 0.5f
            }
            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, divPaint)
            
            currentY += 24f
        }
        
        // A. INTRODUÇÃO
        drawRow("🎙️", "Presidente da Reunião", getNome(prog.presidenteId), "#008080")
        drawRow("🎙️", "Oração Inicial", getNome(prog.oracaoInicialId), "#008080")
        
        // B. TESOUROS
        drawSection("TESOUROS DA PALAVRA DE DEUS", "#008080")
        drawRow("🎙️", "Discurso (10 min)", getNome(prog.tesourosDiscursoId), "#008080")
        drawRow("🎙️", "Joias Espirituais (10 min)", getNome(prog.tesourosJoiasId), "#008080")
        drawRow("📖", "Leitura da Bíblia (4 min)", getNome(prog.tesourosLeituraId), "#4682B4")
        
        // C. FAÇA SEU MELHOR
        drawSection("FAÇA SEU MELHOR NO MINISTÉRIO", "#DAA520")
        val opt = prog.facaSeuMelhorOpcao.toIntOrNull() ?: 3
        
        val edit1Label = prog.facaSeuMelhorCard1Tema.ifBlank { "Parte 1" }
        val edit1Val = if (prog.estudante1AjudanteId != null) "${getNome(prog.estudante1ApresentadorId)} | ${getNome(prog.estudante1AjudanteId)}" else getNome(prog.estudante1ApresentadorId)
        drawRow("👥", edit1Label, edit1Val, "#DAA520")
        
        val edit2Label = prog.facaSeuMelhorCard2Tema.ifBlank { "Parte 2" }
        val edit2Val = if (prog.estudante2AjudanteId != null) "${getNome(prog.estudante2ApresentadorId)} | ${getNome(prog.estudante2AjudanteId)}" else getNome(prog.estudante2ApresentadorId)
        drawRow("👥", edit2Label, edit2Val, "#DAA520")
        
        if (opt >= 3) {
            val edit3Label = prog.facaSeuMelhorCard3Tema.ifBlank { "Parte 3" }
            val edit3Val = if (prog.estudante3AjudanteId != null) "${getNome(prog.estudante3ApresentadorId)} | ${getNome(prog.estudante3AjudanteId)}" else getNome(prog.estudante3ApresentadorId)
            drawRow("👥", edit3Label, edit3Val, "#DAA520")
        }
        if (opt >= 4) {
            val edit4Label = prog.facaSeuMelhorCard4Tema.ifBlank { "Parte 4" }
            val edit4Val = if (prog.estudante4AjudanteId != null) "${getNome(prog.estudante4ApresentadorId)} | ${getNome(prog.estudante4AjudanteId)}" else getNome(prog.estudante4ApresentadorId)
            drawRow("👥", edit4Label, edit4Val, "#DAA520")
        }
        
        // D. VIDA CRISTÃ
        drawSection("NOSSA VIDA CRISTÃ", "#8B0000")
        
        val local1Label = "Parte Local 1 (${prog.vidaParteLocal1DuracaoMin} min)${if (prog.vidaParteLocal1Tema.isNotBlank()) ": " + prog.vidaParteLocal1Tema else ""}"
        drawRow("💬", local1Label, getNome(prog.vidaParteLocal1Id), "#8B0000")
        
        if (prog.vidaPartesQuantidade >= 2) {
            val local2Label = "Parte Local 2 (${prog.vidaParteLocal2DuracaoMin} min)${if (prog.vidaParteLocal2Tema.isNotBlank()) ": " + prog.vidaParteLocal2Tema else ""}"
            drawRow("💬", local2Label, getNome(prog.vidaParteLocal2Id), "#8B0000")
        }
        
        if (prog.tipoSemana != "VISITA_SC") {
            drawRow("💬", "Estudo Bíblico (Dirigente)", getNome(prog.vidaEstudoDirigenteId), "#8B0000")
            drawRow("💬", "Estudo Bíblico (Leitor)", getNome(prog.vidaEstudoLeitorId), "#8B0000")
        } else {
            drawRow("🚗", "Discurso de Serviço (CO)", prog.visitaNomeViajante, "#1565C0")
            drawRow("🚗", "Tema do Discurso", prog.visitaTemaDiscurso, "#1565C0")
        }
        
        drawRow("💬", "Oração Final", getNome(prog.oracaoFinalId), "#8B0000")
        
        // Bottom Stamp
        val stampPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#79747E")
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Documento oficial para o Quadro de Avisos emitido digitalmente pelo AppVM.", 297.5f, 815f, stampPaint)
        
        pdfDocument.finishPage(page)
        
        val cachePath = File(context.cacheDir, "documents")
        cachePath.mkdirs()
        val file = File(cachePath, "Programacao_Vida_Ministerio_${prog.semana.replace("/", "-")}.pdf")
        val fileOutputStream = FileOutputStream(file)
        pdfDocument.writeTo(fileOutputStream)
        pdfDocument.close()
        fileOutputStream.close()
        
        // Share Intent
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Folha de Designações (PDF) - Reunião ${prog.semana}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Salvar/Compartilhar PDF"))
    } catch(e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun exportarWordAgenda(
    context: android.content.Context,
    prog: ProgramacaoSemana,
    publicadores: List<Publicador>,
    todasProgramacoes: Map<String, ProgramacaoSemana> = emptyMap()
) {
    try {
        val progsList = if (todasProgramacoes.isNotEmpty()) {
            todasProgramacoes.values.sortedBy { it.semana }
        } else {
            listOf(prog)
        }
        val file = DocxGenerator.exportarDocx(context, progsList, publicadores)
        
        // Share Intent
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Folha de Designações (Word DOCX) - Reunião ${prog.semana}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Salvar/Compartilhar Word Document (.docx)"))
    } catch(e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Erro ao gerar arquivo Word (.docx): ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PremiumQuadroRow(
    iconEmoji: String,
    iconColor: Color,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Balão colorido ao redor do emoji
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color = iconColor.copy(alpha = 0.1f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconEmoji, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF37474F)
            )
        }
        
        Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (value != "---") iconColor else Color.Gray
            )
        }
    }
    Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
}

@Composable
fun PremiumQuadroEstudanteRow(
    nomeAmigavel: String,
    estudante: String,
    ajudante: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color = Color(0xFFDAA520).copy(alpha = 0.1f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "👥", fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nomeAmigavel,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF37474F)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row {
                Text(text = "Estudante: ", fontSize = 11.sp, color = Color.Gray)
                Text(text = estudante, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDAA520))
            }
            if (ajudante.isNotBlank() && ajudante != "---") {
                Spacer(modifier = Modifier.height(1.dp))
                Row {
                    Text(text = "Ajudante: ", fontSize = 11.sp, color = Color.Gray)
                    Text(text = ajudante, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDAA520))
                }
            }
        }
    }
    Divider(color = Color(0xFFEEEEEE), thickness = 0.5.dp)
}

@Composable
fun PremiumQuadroSectionHeader(title: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Box(
            modifier = Modifier
                .background(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun VisualizacaoQuadroDialog(
    programacao: ProgramacaoSemana,
    publicadores: List<Publicador>,
    todasProgramacoes: Map<String, ProgramacaoSemana> = emptyMap(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    fun getNome(id: Int?): String {
        return publicadores.find { it.id == id }?.nome ?: "---"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top header bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                    }
                    
                    Text(
                        text = "Folha do Quadro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1C1B1F)
                    )
                    
                    Spacer(modifier = Modifier.width(36.dp))
                }

                // Document Sheet Area (with embedded Expanding Floating Action Button)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE5E5E5)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            // Header simulated paper sheet
                            Text(
                                text = "PROGRAMAÇÃO DA REUNIÃO",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF6750A4),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Semana de ${programacao.semana}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            // Presidência & Introdução (🎙️ Teal)
                            PremiumQuadroRow("🎙️", Color(0xFF008080), "Presidente da Reunião", getNome(programacao.presidenteId))
                            PremiumQuadroRow("🎙️", Color(0xFF008080), "Oração Inicial", getNome(programacao.oracaoInicialId))
                            
                            // Section 1: TESOUROS (🎙️ Teal & Leitura 📖 Light Blue)
                            PremiumQuadroSectionHeader("TESOUROS DA PALAVRA DE DEUS", Color(0xFF008080))
                            PremiumQuadroRow("🎙️", Color(0xFF008080), "Discurso (10 min)", getNome(programacao.tesourosDiscursoId))
                            PremiumQuadroRow("🎙️", Color(0xFF008080), "Joias Espirituais (10 min)", getNome(programacao.tesourosJoiasId))
                            PremiumQuadroRow("📖", Color(0xFF4682B4), "Leitura da Bíblia (4 min)", getNome(programacao.tesourosLeituraId))
                            
                            // Section 2: FAÇA SEU MELHOR (👥 Gold)
                            PremiumQuadroSectionHeader("FAÇA SEU MELHOR NO MINISTÉRIO", Color(0xFFDAA520))
                            val opt = programacao.facaSeuMelhorOpcao.toIntOrNull() ?: 3
                            
                            val pt1 = programacao.facaSeuMelhorCard1Tema.ifBlank { "Parte 1" }
                            PremiumQuadroEstudanteRow(pt1, getNome(programacao.estudante1ApresentadorId), getNome(programacao.estudante1AjudanteId))
                            
                            val pt2 = programacao.facaSeuMelhorCard2Tema.ifBlank { "Parte 2" }
                            PremiumQuadroEstudanteRow(pt2, getNome(programacao.estudante2ApresentadorId), getNome(programacao.estudante2AjudanteId))
                            
                            if (opt >= 3) {
                                val pt3 = programacao.facaSeuMelhorCard3Tema.ifBlank { "Parte 3" }
                                PremiumQuadroEstudanteRow(pt3, getNome(programacao.estudante3ApresentadorId), getNome(programacao.estudante3AjudanteId))
                            }
                            if (opt >= 4) {
                                val pt4 = programacao.facaSeuMelhorCard4Tema.ifBlank { "Parte 4" }
                                PremiumQuadroEstudanteRow(pt4, getNome(programacao.estudante4ApresentadorId), getNome(programacao.estudante4AjudanteId))
                            }
                            
                            // Section 3: NOSSA VIDA CRISTÃ (💬 Garnet Red & Visita 🚗 Car)
                            PremiumQuadroSectionHeader("NOSSA VIDA CRISTÃ", Color(0xFF8B0000))
                            
                            val local1Label = "Parte Local 1 (${programacao.vidaParteLocal1DuracaoMin} min)" + if (programacao.vidaParteLocal1Tema.isNotBlank()) ": ${programacao.vidaParteLocal1Tema}" else ""
                            PremiumQuadroRow("💬", Color(0xFF8B0000), local1Label, getNome(programacao.vidaParteLocal1Id))
                            
                            if (programacao.vidaPartesQuantidade >= 2) {
                                val local2Label = "Parte Local 2 (${programacao.vidaParteLocal2DuracaoMin} min)" + if (programacao.vidaParteLocal2Tema.isNotBlank()) ": ${programacao.vidaParteLocal2Tema}" else ""
                                PremiumQuadroRow("💬", Color(0xFF8B0000), local2Label, getNome(programacao.vidaParteLocal2Id))
                            }
                            
                            if (programacao.tipoSemana != "VISITA_SC") {
                                PremiumQuadroRow("💬", Color(0xFF8B0000), "Estudo Bíblico (Dirigente)", getNome(programacao.vidaEstudoDirigenteId))
                                PremiumQuadroRow("💬", Color(0xFF8B0000), "Estudo Bíblico (Leitor)", getNome(programacao.vidaEstudoLeitorId))
                            } else {
                                PremiumQuadroRow("🚗", Color(0xFF1565C0), "Discurso de Serviço (Visita CO)", programacao.visitaNomeViajante)
                                PremiumQuadroRow("🚗", Color(0xFF1565C0), "Tema do Discurso", programacao.visitaTemaDiscurso)
                            }
                            
                            PremiumQuadroRow("💬", Color(0xFF8B0000), "Oração Final", getNome(programacao.oracaoFinalId))
                        }
                    }

                    // Bottom-Right Expanding FAB
                    var fabExpanded by remember { mutableStateOf(false) }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // PDF Option Button
                                FloatingActionButton(
                                    onClick = {
                                        fabExpanded = false
                                        exportarPdfAgenda(context, programacao, publicadores)
                                    },
                                    containerColor = Color(0xFFB3261E),
                                    contentColor = Color.White,
                                    modifier = Modifier.height(44.dp),
                                    shape = RoundedCornerShape(22.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Exportar PDF",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Exportar PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }

                                // Word Option Button
                                FloatingActionButton(
                                    onClick = {
                                        fabExpanded = false
                                        exportarWordAgenda(context, programacao, publicadores, todasProgramacoes)
                                    },
                                    containerColor = Color(0xFF0F8F46),
                                    contentColor = Color.White,
                                    modifier = Modifier.height(44.dp),
                                    shape = RoundedCornerShape(22.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Exportar Word (.docx)",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Exportar Word (.docx)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }

                        // Main Trigger FAB
                        FloatingActionButton(
                            onClick = { fabExpanded = !fabExpanded },
                            containerColor = Color(0xFF6750A4),
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = if (fabExpanded) Icons.Default.Close else Icons.Default.Share,
                                contentDescription = "Expandir Opções de Exportação",
                                modifier = Modifier.size(24.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
