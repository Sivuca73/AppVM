package com.example

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxGenerator {
    fun exportarDocx(
        context: Context,
        progs: List<ProgramacaoSemana>,
        publicadores: List<Publicador>
    ): File {
        val cachePath = File(context.cacheDir, "documents")
        cachePath.mkdirs()
        val fileName = if (progs.size == 1) {
            "Programacao_Vida_Ministerio_${progs[0].semana.replace("/", "-")}.docx"
        } else {
            "Programacao_Completa_Vida_Ministerio.docx"
        }
        val file = File(cachePath, fileName)
        
        val zos = ZipOutputStream(FileOutputStream(file))

        // 1. [Content_Types].xml
        zos.putNextEntry(ZipEntry("[Content_Types].xml"))
        zos.write(getContentTypesXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 2. _rels/.rels
        zos.putNextEntry(ZipEntry("_rels/.rels"))
        zos.write(getRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 3. word/document.xml
        zos.putNextEntry(ZipEntry("word/document.xml"))
        zos.write(getDocumentXml(progs, publicadores).toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        // 4. word/_rels/document.xml.rels (Garante conformidade com o formato OpenXML)
        zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
        zos.write(getDocumentRelsXml().toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        zos.close()
        return file
    }

    private fun getContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
        </Types>
    """.trimIndent()

    private fun getRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/relationships/first-order">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    private fun getDocumentRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        </Relationships>
    """.trimIndent()

    private fun makeRow(parte: String, designado: String, colorHex: String = "1C1B1F", isBold: Boolean = false): String {
        return """
            <w:tr>
              <w:trPr>
                <w:cantSplit/>
              </w:trPr>
              <w:tc>
                <w:tcPr>
                  <w:tcW w:w="4200" w:type="dxa"/>
                  <w:shd w:fill="FFFFFF"/>
                </w:tcPr>
                <w:p>
                  <w:pPr>
                    <w:spacing w:before="80" w:after="80"/>
                  </w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
                      <w:b/>
                      <w:color w:val="$colorHex"/>
                      <w:sz w:val="22"/>
                    </w:rPr>
                    <w:t>$parte</w:t>
                  </w:r>
                </w:p>
              </w:tc>
              <w:tc>
                <w:tcPr>
                  <w:tcW w:w="5300" w:type="dxa"/>
                  <w:shd w:fill="FFFFFF"/>
                </w:tcPr>
                <w:p>
                  <w:pPr>
                    <w:spacing w:before="80" w:after="80"/>
                  </w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
                      ${if (isBold) "<w:b/>" else ""}
                      <w:sz w:val="22"/>
                      <w:color w:val="000000"/>
                    </w:rPr>
                    <w:t>$designado</w:t>
                  </w:r>
                </w:p>
              </w:tc>
            </w:tr>
        """.trimIndent()
    }

    private fun makeSectionRow(sectionTitle: String, colorHex: String): String {
        return """
            <w:tr>
              <w:trPr>
                <w:tblHeader/>
                <w:cantSplit/>
              </w:trPr>
              <w:tc>
                <w:tcPr>
                  <w:gridSpan w:val="2"/>
                  <w:shd w:fill="FFFFFF"/>
                </w:tcPr>
                <w:p>
                  <w:pPr>
                    <w:spacing w:before="140" w:after="100"/>
                  </w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
                      <w:b/>
                      <w:color w:val="$colorHex"/>
                      <w:sz w:val="24"/>
                    </w:rPr>
                    <w:t>$sectionTitle</w:t>
                  </w:r>
                </w:p>
              </w:tc>
            </w:tr>
        """.trimIndent()
    }

    private fun getDocumentXml(progs: List<ProgramacaoSemana>, publicadores: List<Publicador>): String {
        fun getNome(id: Int?): String {
            return publicadores.find { it.id == id }?.nome ?: "---"
        }

        val bodyMarkup = StringBuilder()

        progs.forEachIndexed { index, prog ->
            val rowsHtml = StringBuilder()

            // 1. TRIBUNA INTRODUÇÃO
            rowsHtml.append(makeSectionRow("TRIBUNA / INTRODUÇÃO", "6750A4"))
            rowsHtml.append(makeRow("🎙️ Presidente da Reunião", getNome(prog.presidenteId), "6750A4", isBold = true))
            rowsHtml.append(makeRow("🎙️ Oração Inicial", getNome(prog.oracaoInicialId), "6750A4"))

            // 2. TESOUROS
            rowsHtml.append(makeSectionRow("TESOUROS DA PALAVRA DE DEUS", "00695C"))
            rowsHtml.append(makeRow("🎙️ Discurso (10 min)", getNome(prog.tesourosDiscursoId), "00695C"))
            rowsHtml.append(makeRow("🎙️ Joias Espirituais (10 min)", getNome(prog.tesourosJoiasId), "00695C"))
            rowsHtml.append(makeRow("📖 Leitura da Bíblia (4 min)", getNome(prog.tesourosLeituraId), "00695C", isBold = true))

            // 3. FAÇA SEU MELHOR
            rowsHtml.append(makeSectionRow("FAÇA SEU MELHOR NO MINISTÉRIO", "E65100"))
            
            val parte1Tema = prog.facaSeuMelhorCard1Tema.ifBlank { "Parte 1" }
            rowsHtml.append(makeRow("👥 $parte1Tema", "Estudante: ${getNome(prog.estudante1ApresentadorId)}${if (prog.estudante1AjudanteId != null) " | Ajudante: " + getNome(prog.estudante1AjudanteId) else ""}", "E65100", isBold = true))
            
            val parte2Tema = prog.facaSeuMelhorCard2Tema.ifBlank { "Parte 2" }
            rowsHtml.append(makeRow("👥 $parte2Tema", "Estudante: ${getNome(prog.estudante2ApresentadorId)}${if (prog.estudante2AjudanteId != null) " | Ajudante: " + getNome(prog.estudante2AjudanteId) else ""}", "E65100", isBold = true))

            val optCount = prog.facaSeuMelhorOpcao.toIntOrNull() ?: 3
            if (optCount >= 3) {
                val parte3Tema = prog.facaSeuMelhorCard3Tema.ifBlank { "Parte 3" }
                rowsHtml.append(makeRow("👥 $parte3Tema", "Estudante: ${getNome(prog.estudante3ApresentadorId)}${if (prog.estudante3AjudanteId != null) " | Ajudante: " + getNome(prog.estudante3AjudanteId) else ""}", "E65100", isBold = true))
            }
            if (optCount >= 4) {
                val parte4Tema = prog.facaSeuMelhorCard4Tema.ifBlank { "Parte 4" }
                rowsHtml.append(makeRow("👥 $parte4Tema", "Estudante: ${getNome(prog.estudante4ApresentadorId)}${if (prog.estudante4AjudanteId != null) " | Ajudante: " + getNome(prog.estudante4AjudanteId) else ""}", "E65100", isBold = true))
            }

            // 4. VIDA CRISTÃ
            rowsHtml.append(makeSectionRow("NOSSA VIDA CRISTÃ", "C62828"))
            rowsHtml.append(makeRow("💬 Parte Local 1 (${prog.vidaParteLocal1DuracaoMin} min)", getNome(prog.vidaParteLocal1Id), "C62828"))
            if (prog.vidaPartesQuantidade >= 2) {
                rowsHtml.append(makeRow("💬 Parte Local 2 (${prog.vidaParteLocal2DuracaoMin} min)", getNome(prog.vidaParteLocal2Id), "C62828"))
            }

            if (prog.tipoSemana != "VISITA_SC") {
                rowsHtml.append(makeRow("💬 Estudo Bíblico (Dirigente)", getNome(prog.vidaEstudoDirigenteId), "C62828", isBold = true))
                rowsHtml.append(makeRow("💬 Estudo Bíblico (Leitor)", getNome(prog.vidaEstudoLeitorId), "C62828"))
            } else {
                rowsHtml.append(makeRow("🚗 Discurso de Serviço (Visita CO)", prog.visitaNomeViajante, "C62828", isBold = true))
                rowsHtml.append(makeRow("🚗 Tema do Discurso", prog.visitaTemaDiscurso, "C62828"))
            }

            rowsHtml.append(makeRow("💬 Oração Final", getNome(prog.oracaoFinalId), "C62828"))

            val pageBr = if (index < progs.size - 1) {
                "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"
            } else {
                ""
            }

            bodyMarkup.append("""
                <w:p>
                  <w:pPr>
                    <w:spacing w:after="120"/>
                    <w:jc w:val="center"/>
                  </w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
                      <w:b/>
                      <w:color w:val="6750A4"/>
                      <w:sz w:val="36"/>
                    </w:rPr>
                    <w:t>PROGRAMAÇÃO DA REUNIÃO</w:t>
                  </w:r>
                </w:p>
                <w:p>
                  <w:pPr>
                    <w:spacing w:after="240"/>
                    <w:jc w:val="center"/>
                  </w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
                      <w:b/>
                      <w:color w:val="49454F"/>
                      <w:sz w:val="28"/>
                    </w:rPr>
                    <w:t>Semana de ${prog.semana}</w:t>
                  </w:r>
                </w:p>
                
                <w:tbl>
                  <w:tblPr>
                    <w:tblW w:w="9500" w:type="dxa"/>
                    <w:jc w:val="center"/>
                    <w:tblBorders>
                      <w:top w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
                      <w:bottom w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
                      <w:left w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
                      <w:right w:val="single" w:sz="6" w:space="0" w:color="CCCCCC"/>
                      <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>
                      <w:insideV w:val="none"/>
                    </w:tblBorders>
                    <w:tblCellMar>
                      <w:top w:w="120" w:type="dxa"/>
                      <w:bottom w:w="120" w:type="dxa"/>
                      <w:left w:w="180" w:type="dxa"/>
                      <w:right w:w="180" w:type="dxa"/>
                    </w:tblCellMar>
                  </w:tblPr>
                  ${rowsHtml.toString()}
                </w:tbl>
                
                <w:p>
                  <w:pPr>
                    <w:spacing w:before="240" w:after="360"/>
                  </w:pPr>
                  <w:r>
                    <w:rPr>
                      <w:rFonts w:ascii="Arial" w:hAnsi="Arial"/>
                      <w:i/>
                      <w:sz w:val="18"/>
                      <w:color w:val="79747E"/>
                    </w:rPr>
                    <w:t>Documento oficial emitido em tempo real pelo sistema dinâmico AppVM.</w:t>
                  </w:r>
                </w:p>
                $pageBr
            """.trimIndent())
        }

        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                ${bodyMarkup.toString()}
              </w:body>
            </w:document>
        """.trimIndent()
    }
}
