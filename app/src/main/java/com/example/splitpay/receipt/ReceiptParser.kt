package com.example.splitpay.receipt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
data class ReceiptDraft(val merchant:String="",val amount:String="",val date:String=LocalDate.now().toString(),val text:String="",val image:String="",val category:String="Other")
object ReceiptParser {
 fun parse(text:String,image:String=""):ReceiptDraft {
  val lines=text.lines().map{it.trim()}.filter{it.isNotEmpty()}
  val merchant=lines.firstOrNull{it.any(Char::isLetter)&&!it.contains("invoice",true)&&!it.contains("receipt",true)&&!it.contains("GST",true)}?.take(200).orEmpty()
  val totals=lines.filter{(it.contains("total",true)||it.contains("amount payable",true)||it.contains("amount due",true))&&!Regex("sub\\s*total|tax|discount",RegexOption.IGNORE_CASE).containsMatchIn(it)}
  val totalLine=totals.firstOrNull{it.contains("grand",true)} ?: totals.lastOrNull()
  val amount=totalLine?.let{Regex("\\d[\\d,]*(?:\\.\\d{1,2})?").findAll(it).lastOrNull()?.value?.replace(",","")}?.toBigDecimalOrNull()?.takeIf{it.signum()>0}?.setScale(2)?.toPlainString().orEmpty()
  val patterns=listOf(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b") to "uuuu-MM-dd",Regex("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b") to "d/M/uuuu",Regex("\\b\\d{1,2}-\\d{1,2}-\\d{4}\\b") to "d-M-uuuu",Regex("\\b\\d{1,2}\\s+[A-Za-z]{3}\\s+\\d{4}\\b") to "d MMM uuuu")
  val date=patterns.firstNotNullOfOrNull{(regex,format)->regex.find(text)?.value?.let{runCatching{LocalDate.parse(it,DateTimeFormatter.ofPattern(format,java.util.Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)).toString()}.getOrNull()}} ?: LocalDate.now().toString()
  return ReceiptDraft(merchant,amount,date,text.take(20000),image)
 }
}
