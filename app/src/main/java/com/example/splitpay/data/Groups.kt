package com.example.splitpay.data

import androidx.room.*
import androidx.room.Query
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import retrofit2.http.*
import javax.inject.Inject
import javax.inject.Singleton

data class SharedGroup(val id: String, val name: String, val description: String, val currency: String, val createdBy: String, val memberIds: Set<String>, val version: Long = 0)
data class ExpenseShare(val amount: String, val weight: String)
data class SharedExpense(val id: String, val groupId: String, val description: String, val amount: String, val payerId: String, val category: String, val date: String, val notes: String, val splitType: String, val splits: Map<String, ExpenseShare>, val createdBy: String, val version: Long)
data class Transfer(val fromUser: String, val toUser: String, val amount: String)
data class SettlementInput(val requestId: String,val receiverUserId: String,val amount: String,val method: String)
data class Settlement(val id: String,val groupId: String,val payerUserId: String,val receiverUserId: String,val amount: String,val status: String,val method: String)
data class GroupActivity(val id: String, val type: String, val description: String, val createdAt: String)
data class GroupDetails(val group: SharedGroup, val members: List<UserProfile>, val expenses: List<SharedExpense>, val balances: Map<String,String>, val simplified: List<Transfer>, val activity: List<GroupActivity>,val direct:List<Transfer>?=null,val settlements:List<Settlement>?=null)
data class GroupInput(val name: String, val description: String)
data class SharedExpenseInput(val description: String, val amount: String, val payerId: String, val category: String, val date: String, val notes: String, val splitType: String, val participants: Map<String,String>, val version: Long? = null)
interface GroupApi {
 @POST("api/groups/{id}/settlements") suspend fun settle(@Path("id") id: String,@Body input: SettlementInput): Settlement
 @GET("api/groups") suspend fun groups(): List<SharedGroup>
 @POST("api/groups") suspend fun create(@Body input: GroupInput): SharedGroup
 @GET("api/groups/{id}") suspend fun details(@Path("id") id: String): GroupDetails
 @PUT("api/groups/{id}") suspend fun update(@Path("id") id: String, @Body input: GroupInput): SharedGroup
 @DELETE("api/groups/{id}") suspend fun delete(@Path("id") id: String)
 @POST("api/groups/{id}/members") suspend fun addMember(@Path("id") id: String, @Body input: Map<String,String>): SharedGroup
 @DELETE("api/groups/{id}/members/{userId}") suspend fun removeMember(@Path("id") id: String, @Path("userId") userId: String)
 @POST("api/groups/{id}/expenses") suspend fun createExpense(@Path("id") id: String,@Body input: SharedExpenseInput): SharedExpense
 @PUT("api/expenses/{id}") suspend fun updateExpense(@Path("id") id: String,@Body input: SharedExpenseInput): SharedExpense
 @DELETE("api/expenses/{id}") suspend fun deleteExpense(@Path("id") id: String)
}
@Entity(tableName="group_cache", primaryKeys=["accountId","cacheKey"])
data class GroupCache(val accountId: String,val cacheKey: String,val payload: String,val updatedAt: Long=System.currentTimeMillis())
@Dao interface GroupCacheDao {
 @Query("SELECT * FROM group_cache WHERE accountId=:accountId AND cacheKey=:cacheKey")
 fun observe(accountId: String,cacheKey: String): Flow<GroupCache?>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(cache: GroupCache)
 @Query("DELETE FROM group_cache WHERE accountId=:accountId AND cacheKey=:cacheKey") suspend fun delete(accountId: String,cacheKey: String)
}
@Singleton @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GroupRepository @Inject constructor(val api: GroupApi, private val cache: GroupCacheDao, private val session: SessionRepository, private val client:OkHttpClient) {
 val updates:Flow<String> = session.account.flatMapLatest { owner ->
  if(owner.isEmpty()) emptyFlow() else callbackFlow {
   val request=Request.Builder().url(com.example.splitpay.BuildConfig.API_BASE_URL+"api/live").build()
   val socket=client.newWebSocket(request,object:WebSocketListener(){
    override fun onMessage(webSocket:WebSocket,text:String){runCatching{com.google.gson.JsonParser.parseString(text).asJsonObject.get("groupId").asString}.getOrNull()?.let{trySend(it)}}
    override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?){close(t)}
    override fun onClosed(webSocket:WebSocket,code:Int,reason:String){close(java.io.IOException("Live connection closed"))}
   })
   awaitClose {socket.cancel()}
  }.retryWhen { _,attempt -> delay(minOf(30000L,2000L*(attempt+1)));true }
 }
 private val gson=Gson()
 private val refreshMutex=Mutex()
 val groups: Flow<List<SharedGroup>> = session.account.flatMapLatest { owner ->
  cache.observe(owner,"groups").map { row -> row?.let { gson.fromJson<List<SharedGroup>>(it.payload,object:TypeToken<List<SharedGroup>>(){}.type) } ?: emptyList() }
 }
 fun details(id: String): Flow<GroupDetails?> = session.account.flatMapLatest { owner ->
  cache.observe(owner,"group:"+id).map { row -> row?.let { gson.fromJson(it.payload,GroupDetails::class.java) } }
 }
 suspend fun refresh() = refreshMutex.withLock {
  val owner=session.account.value
  val result=api.groups()
  check(owner==session.account.value){"Account changed"}
  cache.save(GroupCache(owner,"groups",gson.toJson(result)))
 }
 suspend fun refresh(id: String) = refreshMutex.withLock {
  val owner=session.account.value
  val result=api.details(id)
  check(owner==session.account.value){"Account changed"}
  cache.save(GroupCache(owner,"group:"+id,gson.toJson(result)))
 }
 suspend fun removeCache(id: String) { cache.delete(session.account.value,"group:"+id) }
}
