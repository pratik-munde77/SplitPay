package com.example.splitpay.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.http.GET
import java.io.IOException
import javax.inject.Inject

data class HealthResponse(val status: String)
interface SplitPayApi {
    @GET("api/health") suspend fun health(): HealthResponse
    @GET("api/users/me") suspend fun me(): UserProfile
    @retrofit2.http.PUT("api/users/me") suspend fun updateMe(@retrofit2.http.Body profile: UserProfile): UserProfile
}
data class UserProfile(val id: String = "", val name: String = "", val email: String = "", val phone: String = "", val defaultCurrency: String = "INR", val upiId: String = "", val photoUrl: String = "")

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val message: String, val statusCode: Int? = null) : ApiResult<Nothing>
}

class BackendRepository @Inject constructor(private val api: SplitPayApi) {
    suspend fun health(): ApiResult<HealthResponse> = try { ApiResult.Success(api.health()) }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { ApiResult.Failure(e.userMessage(), (e as? HttpException)?.code()) }
}

class FirebaseTokenInterceptor @Inject constructor(private val session: SessionRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val owner=session.account.value
        val expected=chain.request().header("X-Local-Account")
        if(expected!=null&&expected!=owner)throw IOException("Account changed. Retry after signing in.")
        val request = chain.request().newBuilder().removeHeader("X-Local-Account")
                val token = try { runBlocking { session.token() } }
                catch (e: Exception) { throw IOException("Could not authenticate. Please sign in again.", e) }
                if(owner!=session.account.value)throw IOException("Account changed. Retry after signing in.")
                if (token != null) request.header("Authorization", "Bearer $token")
        return chain.proceed(request.build())
    }
}

fun Throwable.userMessage(): String = when (this) {
    is HttpException -> backendMessage() ?: when (code()) {
        401 -> "Your session expired. Please sign in again."
        403 -> "You do not have permission for this action."
        404 -> "This item no longer exists."
        409 -> "This item changed. Refresh and try again."
        422, 400 -> "Check your information and try again."
        else -> "The server could not complete the request. Try again."
    }
    is java.net.SocketTimeoutException -> "The request timed out. Try again."
    is IOException -> "Cannot reach the backend. Check Wi-Fi and the server address."
    is IllegalArgumentException -> message ?: "Check your information."
    else -> "Something went wrong. Please try again."
}
private fun HttpException.backendMessage():String? = try {
    response()?.errorBody()?.string()?.let { com.google.gson.JsonParser.parseString(it).asJsonObject.get("message")?.asString?.take(500) }
} catch(_:Exception){null}
