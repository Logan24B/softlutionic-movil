package com.softlutionic.mobile
import com.google.gson.JsonParser
import com.softlutionic.mobile.data.mapper.toRecord
import org.junit.Assert.*
import org.junit.Test
class RecordMapperTest {
 @Test fun allTechnicalKeyStylesAreHiddenButPayloadAndRecordKeyRemain() {
  val data=JsonParser.parseString("""{"id":900001,"Id":900002,"ID":900003,"IdLog":900004,"ClienteId":900005,"other_id":900006,"IdRegistro":900007,"Descripcion":"Detalle útil"}""").asJsonObject
  val record=data.toRecord()
  assertEquals("900001",record.id)
  assertSame(data,record.payload)
  (900001..900007).forEach { assertFalse((record.title+record.detail).contains(it.toString())) }
  assertFalse(record.detail.contains("ClienteId")); assertTrue(record.detail.contains("Cliente: No disponible"))
 }
 @Test fun humanRelationsResolveFromEmbeddedData() {
  val data=JsonParser.parseString("""{"id":9001,"ClienteId":9002,"TipoContratoId":9003,"DepartamentoId":9004,"ServicioId":9005,"UsuarioId":9006,"cliente_detalle":{"id":9002,"Nombre":"Ana","Apellido":"Pérez"},"tipo_contrato_detalle":{"id":9003,"Nombre":"Mensual"},"departamento_detalle":{"id":9004,"Nombre":"Managua"},"servicio_detalle":{"id":9005,"Nombre_Servicio":"Soporte"},"usuario_detalle":{"id":9006,"Usuario":"operador"}}""").asJsonObject
  val record=data.toRecord()
  listOf("Ana Pérez","Mensual","Managua","Soporte","operador").forEach { assertTrue(record.detail.contains(it)) }
  (9001..9006).forEach { assertFalse((record.title+record.detail).contains(it.toString())) }
 }
 @Test fun cachedRelationsAndSchemaResolveGenericModulesIncludingInvoicesAndPayments() {
  fun json(value: String)=JsonParser.parseString(value).asJsonObject
  val catalogs=mapOf("facturas" to listOf(json("""{"id":9401,"CodigoFact":"FAC-2026-20"}""")),"usuarios" to listOf(json("""{"id":9402,"Usuario":"operador"}""")))
  val payment=json("""{"id":9403,"FacturaId":9401,"UsuarioId":9402,"NumeroRecibo":"REC-26"}""").toRecord(catalogs,mapOf("FacturaId" to "facturas","UsuarioId" to "usuarios"))
  assertEquals("REC-26",payment.title); assertTrue(payment.detail.contains("Factura: FAC-2026-20")); assertTrue(payment.detail.contains("Usuario: operador"))
  (9401..9403).forEach { assertFalse(payment.detail.contains(it.toString())) }
 }
 @Test fun businessReferencesAndIdentityDocumentsArePreserved() {
  val data=JsonParser.parseString("""{"id":9501,"CodigoFact":"FAC-001","NumeroRecibo":"REC-001","NumeroContrato":"CT-01","CodigoContrato":"SERV-A","ReferenciaPago":"BANCO-REF","Cedula":"001-ABC","Valid":true,"Grid":"A"}""").asJsonObject
  val record=data.toRecord()
  listOf("FAC-001","REC-001","CT-01","SERV-A","BANCO-REF","001-ABC","Valid: true","Grid: A").forEach { assertTrue(record.detail.contains(it)) }
  assertFalse(record.detail.contains("9501"))
 }
 @Test fun idOnlyRecordNeverInventsABusinessNumber() {
  val record=JsonParser.parseString("""{"id":9601}""").asJsonObject.toRecord()
  assertEquals("Registro",record.title); assertEquals("",record.detail); assertEquals("9601",record.id)
 }
 @Test fun invoiceLineDoesNotFallbackToServicePrimaryKey() {
  val record=JsonParser.parseString("""{"id":9701,"detalles":[{"id":9702,"ServicioId":9703,"Cantidad":2,"PrecioVenta":10,"Subtotal":20}]}""").asJsonObject.toRecord()
  assertTrue(record.detail.contains("Servicio no disponible")); assertFalse(record.detail.contains("9703"))
  assertEquals(9703,record.payload["detalles"].asJsonArray[0].asJsonObject["ServicioId"].asInt)
 }
 @Test fun centralRuleLabelsRelationsAndNeverHidesBusinessFields() {
  val rule=com.softlutionic.mobile.ui.RecordPresentation
  listOf("id","ID","ClienteId","Registro_id","IdLog","ServicioID").forEach { assertTrue(rule.isTechnicalIdField(it)) }
  listOf("NumeroFactura","CodigoFact","CodigoContrato","ReferenciaPago","Cedula","Valid","Grid").forEach { assertFalse(rule.isTechnicalIdField(it)) }
  assertEquals("Cliente",rule.label("ClienteId")); assertEquals("Usuario",rule.label("IdUsuario"))
 }
 @Test fun realClientJsonKeepsSpanishAndRemoteId() {
  val record=JsonParser.parseString("""{"id":42,"Nombre":"Ana","Apellido":"Muñoz","Telefono":"+505 8888 1111","departamento_detalle":{"id":1,"Nombre":"León"}}""").asJsonObject.toRecord()
  assertEquals("42",record.id)
  assertEquals("Ana Muñoz",record.title)
  assertTrue(record.detail.contains("Telefono: +505"))
  assertTrue(record.detail.contains("Departamento: León"))
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
 @Test fun departmentNameReplacesTechnicalIdWithoutChangingPayload() {
  val record=JsonParser.parseString("""{"id":42,"DepartamentoId":10,"departamento_detalle":{"id":10,"Nombre":"Managua"}}""").asJsonObject.toRecord()
  assertTrue(record.detail.contains("Departamento: Managua"))
  assertFalse(record.detail.contains("DepartamentoId:"))
  assertEquals(10,record.payload["DepartamentoId"].asInt)
 }
 @Test fun unresolvedDepartmentDoesNotDisplayANumberAsItsName() {
  val record=JsonParser.parseString("""{"id":42,"DepartamentoId":10}""").asJsonObject.toRecord()
  assertTrue(record.detail.contains("Departamento: No disponible"))
  assertFalse(record.detail.contains("DepartamentoId:"))
  assertEquals(10,record.payload["DepartamentoId"].asInt)
 }
}
