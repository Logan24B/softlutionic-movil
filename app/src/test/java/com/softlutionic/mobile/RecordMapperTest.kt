package com.softlutionic.mobile
import com.google.gson.JsonParser
import com.softlutionic.mobile.data.mapper.toRecord
import org.junit.Assert.*
import org.junit.Test
class RecordMapperTest {
 @Test fun realClientJsonKeepsSpanishAndRemoteId() {
  val record=JsonParser.parseString("""{"id":42,"Nombre":"Ana","Apellido":"Muñoz","Telefono":"+505 8888 1111","departamento_detalle":{"id":1,"Nombre":"León"}}""").asJsonObject.toRecord()
  assertEquals("42",record.id)
  assertEquals("Ana Muñoz",record.title)
  assertTrue(record.detail.contains("Telefono: +505"))
  assertTrue(record.detail.contains("departamento: León"))
 }
 @Test fun auditUsesActualPrimaryKey() {
  val record=JsonParser.parseString("""{"IdLog":71,"Tabla":"Facturas","Accion":"UPDATE"}""").asJsonObject.toRecord()
  assertEquals("71",record.id)
  assertEquals("Facturas",record.title)
 }
 @Test fun invoiceDetailShowsServiceAndQuantity() {
  val record=JsonParser.parseString("""{"id":5,"CodigoFact":100,"detalles":[{"ServicioId":2,"Cantidad":3,"PrecioVenta":150,"Subtotal":450,"servicio_detalle":{"id":2,"Nombre_Servicio":"Soporte"}}]}""").asJsonObject.toRecord()
  assertTrue(record.detail.contains("Soporte"))
  assertTrue(record.detail.contains("Cantidad: 3"))
  assertTrue(record.detail.contains("Subtotal: 450"))
 }
 @Test fun passwordNeverAppearsInDetail() {
  val record=JsonParser.parseString("""{"id":1,"Usuario":"ana","password":"private","Contrasena":"private"}""").asJsonObject.toRecord()
  assertFalse(record.detail.contains("private"))
 }
}
