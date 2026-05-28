package com.example

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Entidade Room para armazenar os publicadores.
 */
@Entity(tableName = "publicadores")
data class PublicadorEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nome: String,
    val genero: String, // Genero string
    val perfil: String, // PerfilPublicador string
    val servoDirigenteAprovado: Boolean = false,
    val parentescosJson: String,      // List<RelacaoParentesco> como string JSON
    val historicoPartesJson: String   // List<RegistroHistorico> como string JSON
)

/**
 * Entidade Room para armazenar as configurações dinâmicas de regras do painel.
 */
@Entity(tableName = "regras_config")
data class RegrasConfigEntity(
    @PrimaryKey val id: Int = 1, // ID fixo para armazenar documento único
    val configJson: String        // PainelRegrasConfig como string JSON
)

/**
 * Conversores de tipo para o banco de dados Room utilizando o Moshi.
 */
class TypeConverters {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    
    // Parentescos
    private val parentescosType = Types.newParameterizedType(List::class.java, RelacaoParentesco::class.java)
    private val parentescosAdapter = moshi.adapter<List<RelacaoParentesco>>(parentescosType)

    // Histórico
    private val historicoType = Types.newParameterizedType(List::class.java, RegistroHistorico::class.java)
    private val historicoAdapter = moshi.adapter<List<RegistroHistorico>>(historicoType)

    @TypeConverter
    fun stringToParentescos(value: String?): List<RelacaoParentesco> {
        return if (value.isNullOrEmpty()) emptyList() else parentescosAdapter.fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun parentescosToString(list: List<RelacaoParentesco>): String {
        return parentescosAdapter.toJson(list)
    }

    @TypeConverter
    fun stringToHistorico(value: String?): List<RegistroHistorico> {
        return if (value.isNullOrEmpty()) emptyList() else historicoAdapter.fromJson(value) ?: emptyList()
    }

    @TypeConverter
    fun historicoToString(list: List<RegistroHistorico>): String {
        return historicoAdapter.toJson(list)
    }
}

/**
 * Interfaces de acesso a Dados (DAOs).
 */
@Dao
interface PublicadorDao {
    @Query("SELECT * FROM publicadores ORDER BY nome ASC")
    fun getAllPublicadores(): Flow<List<PublicadorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPublicador(publicador: PublicadorEntity): Long

    @Query("DELETE FROM publicadores WHERE id = :id")
    suspend fun deletePublicadorById(id: Int)
    
    @Query("DELETE FROM publicadores")
    suspend fun clearAll()
}

@Dao
interface RegrasConfigDao {
    @Query("SELECT * FROM regras_config WHERE id = 1 LIMIT 1")
    fun getRegrasConfig(): Flow<RegrasConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRegrasConfig(entity: RegrasConfigEntity)
}

/**
 * Banco de dados Room central.
 */
@Database(entities = [PublicadorEntity::class, RegrasConfigEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun publicadorDao(): PublicadorDao
    abstract fun regrasConfigDao(): RegrasConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "appvm_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Repositório Unificado (Repository Pattern).
 * Faz parsing e mapeamento entre as Entidades do Banco e as Models Kotlin.
 */
class AppRepository(private val db: AppDatabase) {
    private val publicadorDao = db.publicadorDao()
    private val regrasConfigDao = db.regrasConfigDao()
    
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    
    // Mapeadores do publicador
    private val parentescosType = Types.newParameterizedType(List::class.java, RelacaoParentesco::class.java)
    private val parentescosAdapter = moshi.adapter<List<RelacaoParentesco>>(parentescosType)
    private val historicoType = Types.newParameterizedType(List::class.java, RegistroHistorico::class.java)
    private val historicoAdapter = moshi.adapter<List<RegistroHistorico>>(historicoType)
    private val rulesAdapter = moshi.adapter(PainelRegrasConfig::class.java)

    // Fluxo reativo de publicadores convertidos para domain models
    val publicadoresStream: Flow<List<Publicador>> = publicadorDao.getAllPublicadores().combine(regrasConfigDao.getRegrasConfig()) { entityList, _ ->
        entityList.map { entity ->
            Publicador(
                id = entity.id,
                nome = entity.nome,
                genero = try { Genero.valueOf(entity.genero) } catch(e: Exception) { Genero.MASCULINO },
                perfil = try { PerfilPublicador.valueOf(entity.perfil) } catch(e: Exception) { PerfilPublicador.IRMAO_BATIZADO },
                servoDirigenteAprovado = entity.servoDirigenteAprovado,
                parentescos = try { parentescosAdapter.fromJson(entity.parentescosJson) ?: emptyList() } catch(e: Exception) { emptyList() },
                historicoPartes = try { historicoAdapter.fromJson(entity.historicoPartesJson) ?: emptyList() } catch(e: Exception) { emptyList() }
            )
        }
    }

    // Fluxo reativo de regras
    val regrasConfigStream: Flow<PainelRegrasConfig> = AtivarConfigReal()

    private fun AtivarConfigReal(): Flow<PainelRegrasConfig> {
        return regrasConfigDao.getRegrasConfig().combine(publicadoresStream) { entity, _ ->
            if (entity != null) {
                try {
                    rulesAdapter.fromJson(entity.configJson) ?: PainelRegrasConfig()
                } catch(e: Exception) {
                    PainelRegrasConfig()
                }
            } else {
                PainelRegrasConfig()
            }
        }
    }

    suspend fun saveRules(config: PainelRegrasConfig) {
        val json = rulesAdapter.toJson(config)
        regrasConfigDao.saveRegrasConfig(RegrasConfigEntity(configJson = json))
    }

    suspend fun insertPublicador(pub: Publicador) {
        val entity = PublicadorEntity(
            id = pub.id,
            nome = pub.nome,
            genero = pub.genero.name,
            perfil = pub.perfil.name,
            servoDirigenteAprovado = pub.servoDirigenteAprovado,
            parentescosJson = parentescosAdapter.toJson(pub.parentescos),
            historicoPartesJson = historicoAdapter.toJson(pub.historicoPartes)
        )
        publicadorDao.insertPublicador(entity)
    }

    suspend fun deletePublicador(id: Int) {
        publicadorDao.deletePublicadorById(id)
    }
    
    suspend fun clearAllPublicadores() {
        publicadorDao.clearAll()
    }

    /**
     * Preenche a congregação com publicadores padrão contendo perfis variados, parentesco e históricos
     */
    suspend fun preencherCongregacaoExemplo() {
        clearAllPublicadores()

        // Cadastros base de teste:
        // 1. Ancião: Marcos Oliveira (Masc). Histórico recente de Discurso e Dirigente de Estudo
        val marcos = Publicador(
            id = 1,
            nome = "Marcos Oliveira",
            genero = Genero.MASCULINO,
            perfil = PerfilPublicador.ANCIAO,
            historicoPartes = listOf(
                RegistroHistorico("2026-05-21", "VIDA_ESTUDO_DIRIGENTE", "DIRIGENTE", 3), // Dirigiu semana passada com Lucas de Leitor
                RegistroHistorico("2026-05-07", "TESOUROS_DISCURSO", "DISCURSISTA", null)
            )
        )

        // 2. Ancião: Sergio Santos (Masc). Histórico de Joias Ocultas
        val sergio = Publicador(
            id = 2,
            nome = "Sérgio Santos",
            genero = Genero.MASCULINO,
            perfil = PerfilPublicador.ANCIAO,
            historicoPartes = listOf(
                RegistroHistorico("2026-05-14", "TESOUROS_JOIAS", "DISCURSISTA", null)
            )
        )

        // 3. Servo Ministerial (Não aprovado dirigente): Lucas Silva (Masc)
        val lucas = Publicador(
            id = 3,
            nome = "Lucas Silva",
            genero = Genero.MASCULINO,
            perfil = PerfilPublicador.SERVO_MINISTERIAL,
            servoDirigenteAprovado = false,
            historicoPartes = listOf(
                RegistroHistorico("2026-05-21", "VIDA_ESTUDO_LEITOR", "LEITOR", 1) // Fez dupla com Marcos na última reunião
            )
        )

        // 4. Servo Ministerial (Aprovado): Renato Costa (Masc). Casado com Priscila Costa (Irmã)
        // Guardando o id de Priscila (ID = 8), faremos referência mútua de parentesco
        val renato = Publicador(
            id = 4,
            nome = "Renato Costa",
            genero = Genero.MASCULINO,
            perfil = PerfilPublicador.SERVO_MINISTERIAL,
            servoDirigenteAprovado = true,
            parentescos = listOf(RelacaoParentesco(8, GrauParentesco.CONJUGE)),
            historicoPartes = listOf(
                RegistroHistorico("2026-05-28", "TESOUROS_LEITURA", "LEITOR", null)
            )
        )

        // 5. Irmão Batizado Comum: Roberto Almeida (Masc).
        val roberto = Publicador(
            id = 5,
            nome = "Roberto Almeida",
            genero = Genero.MASCULINO,
            perfil = PerfilPublicador.IRMAO_BATIZADO,
            historicoPartes = listOf(
                RegistroHistorico("2026-05-14", "MINISTERIO_ESTUDANTE_1", "APRESENTADOR", 6) // Apresentador do estudante 1 duas semanas atrás
            )
        )

        // 6. Irmão Não Batizado: André Souza (Masc, jovem). Filho de Alice Souza (Irmã, ID = 9)
        val andre = Publicador(
            id = 6,
            nome = "André Souza",
            genero = Genero.MASCULINO,
            perfil = PerfilPublicador.IRMAO_NAO_BATIZADO,
            parentescos = listOf(RelacaoParentesco(9, GrauParentesco.PAI_FILHO)),
            historicoPartes = emptyList()
        )

        // 7. Irmã Batizada: Alice Souza (Fem, mãe do André)
        val alice = Publicador(
            id = 9,
            nome = "Alice Souza",
            genero = Genero.FEMININO,
            perfil = PerfilPublicador.IRMA,
            parentescos = listOf(RelacaoParentesco(6, GrauParentesco.PAI_FILHO)), // Mútua do André
            historicoPartes = listOf(
                RegistroHistorico("2026-05-28", "MINISTERIO_ESTUDANTE_2", "AJUDANTE", 10) // Foi ajudante semana passada
            )
        )

        // 8. Irmã Batizada: Priscila Costa (Fem, esposa do Renato Costa)
        val priscila = Publicador(
            id = 8,
            nome = "Priscila Costa",
            genero = Genero.FEMININO,
            perfil = PerfilPublicador.IRMA,
            parentescos = listOf(RelacaoParentesco(4, GrauParentesco.CONJUGE)), // Mútua de Renato
            historicoPartes = listOf(
                RegistroHistorico("2026-05-14", "MINISTERIO_ESTUDANTE_2", "APRESENTADOR", 10)
            )
        )

        // 9. Irmã Batizada: Mariana Dias (Fem).
        val mariana = Publicador(
            id = 10,
            nome = "Mariana Dias",
            genero = Genero.FEMININO,
            perfil = PerfilPublicador.IRMA,
            historicoPartes = emptyList()
        )

        // Salvando no Banco de dados
        insertPublicador(marcos)
        insertPublicador(sergio)
        insertPublicador(lucas)
        insertPublicador(renato)
        insertPublicador(roberto)
        insertPublicador(andre)
        insertPublicador(alice)
        insertPublicador(priscila)
        insertPublicador(mariana)

        // Salvar painel padrão
        saveRules(PainelRegrasConfig())
    }
}
