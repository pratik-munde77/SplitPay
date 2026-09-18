package com.example.splitpay
import com.example.splitpay.receipt.ReceiptParser
import org.junit.Assert.*
import org.junit.Test
class ReceiptParserTest {
 @Test fun grandTotalWinsOverSubtotalAndTax(){
  val result=ReceiptParser.parse("DOMINOS\n10/09/2026\nSubtotal 500.00\nTotal tax 75.00\nGrand Total INR 575.00\nCash 600.00")
  assertEquals("DOMINOS",result.merchant);assertEquals("575.00",result.amount);assertEquals("2026-09-10",result.date)
 }
 @Test fun neverTreatsPhoneNumberAsTotal(){
  assertEquals("",ReceiptParser.parse("Coffee Shop\nPhone 9876543210\nGST 12345").amount)
 }
}
