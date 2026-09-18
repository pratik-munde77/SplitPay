package com.example.splitpay.data
import androidx.room.*
import androidx.room.Query
import retrofit2.http.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

data class PersonalRecord(val id:String,val merchant:String,val amount:String,val category:String,val date:String,val source:String,val paymentMethod:String,val notes:String,val receiptUrl:String)
data class BudgetInput(val month:String,val category:String,val amount:String)
data class BudgetStatus(val id:String,val category:String,val amount:String,val spent:String,val remaining:String,val percent:Int)
data class MonthlyAnalytics(val month:String,val total:String,val personalTotal:String,val sharedTotal:String,val previousTotal:String,val youOwe:String,val youAreOwed:String,val categories:Map<String,String>,val daily:Map<String,String>,val topMerchants:Map<String,String>,val budgets:List<BudgetStatus>)
interface PersonalApi {
 @POST("api/devices") suspend fun registerDevice(@Body input:Map<String,String>)
 @GET("api/notifications") suspend fun notifications():List<AppNotification>
 @POST("api/notifications/{id}/read") suspend fun readNotification(@Path("id") id:String)
 @POST("api/receipts/classify") suspend fun classify(@Body input:Map<String,String>):Classification
 @GET("api/personal-expenses") suspend fun list(@Header("X-Local-Account") owner:String):List<PersonalRecord>
 @POST("api/personal-expenses") suspend fun create(@Body input:PersonalRecord,@Header("X-Local-Account") owner:String):PersonalRecord
 @PUT("api/personal-expenses/{id}") suspend fun update(@Path("id")id:String,@Body input:PersonalRecord,@Header("X-Local-Account") owner:String):PersonalRecord
 @DELETE("api/personal-expenses/{id}") suspend fun delete(@Path("id")id:String,@Header("X-Local-Account") owner:String)
 @GET("api/analytics/monthly") suspend fun analytics():MonthlyAnalytics
 @POST("api/budgets") suspend fun budget(@Body input:BudgetInput)
}
data class Classification(val merchant:String,val category:String,val confidence:Double)
data class AppNotification(val id:String,val groupId:String?,val message:String,val createdAt:String,val readAt:String?)
@Dao interface PersonalSyncDao {
 @Query("SELECT * FROM personal_expenses WHERE ownerId=:owner") suspend fun all(owner:String):List<PersonalExpense>
 @Upsert suspend fun put(row:PersonalExpense)
 @Query("DELETE FROM personal_expenses WHERE id=:id AND ownerId=:owner") suspend fun delete(id:String,owner:String)
 @Query("DELETE FROM personal_expenses WHERE ownerId=:owner AND syncState='SYNCED'") suspend fun deleteSynced(owner:String)
 @Transaction suspend fun merge(owner:String,rows:List<PersonalExpense>) {
  val pending=all(owner).filter{it.syncState!="SYNCED"}.map{it.id}.toSet()
  deleteSynced(owner);rows.filter{it.id !in pending}.forEach{put(it)}
 }
}
@Singleton class PersonalSyncRepository @Inject constructor(private val api:PersonalApi,private val dao:PersonalSyncDao,private val session:SessionRepository,private val local:ExpenseRepository) {
 private val mutex=Mutex()
 private fun PersonalExpense.remote()=PersonalRecord(id,merchant,BigDecimal.valueOf(amountMinor,2).toPlainString(),category,date,source,paymentMethod,notes,receiptUrl)
 suspend fun sync()=mutex.withLock {
  val owner=session.account.value;if(owner.isEmpty())return@withLock
  for(row in dao.all(owner).filter{it.syncState!="SYNCED"}) {
   check(session.account.value==owner){"Account changed"}
   if(row.syncState=="PENDING_DELETE"){api.delete(row.id,owner);dao.delete(row.id,owner);continue}
   if(row.syncState=="PENDING_UPDATE") {
    try {api.update(row.id,row.remote(),owner)} catch(e:retrofit2.HttpException){if(e.code()==404)api.create(row.remote(),owner) else throw e}
   } else api.create(row.remote(),owner)
   dao.put(row.copy(syncState="SYNCED"))
  }
  val result=api.list(owner);check(session.account.value==owner){"Account changed"}
  dao.merge(owner,result.map{PersonalExpense(id=it.id,merchant=it.merchant,amountMinor=it.amount.toBigDecimal().movePointRight(2).longValueExact(),category=it.category,date=it.date,notes=it.notes,source=it.source,syncState="SYNCED",ownerId=owner,paymentMethod=it.paymentMethod,receiptUrl=it.receiptUrl)})
 }
 suspend fun edit(id:String,merchant:String,amount:String,category:String,date:String,notes:String,paymentMethod:String)=mutex.withLock {
  val existing=dao.all(session.account.value).find{it.id==id} ?: throw IllegalArgumentException("Expense not found")
  val row=local.draft(merchant,amount,category,date,notes,existing.source,paymentMethod,existing.receiptUrl).copy(id=id,syncState=if(existing.syncState=="PENDING_CREATE")"PENDING_CREATE" else "PENDING_UPDATE")
  dao.put(row)
 }
 suspend fun delete(id:String)=mutex.withLock {
  val owner=session.account.value
  val row=dao.all(owner).find{it.id==id} ?: return@withLock
  dao.put(row.copy(syncState="PENDING_DELETE"))
 }
}
