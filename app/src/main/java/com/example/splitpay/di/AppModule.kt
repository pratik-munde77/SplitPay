package com.example.splitpay.di

import android.content.Context
import androidx.room.Room
import com.example.splitpay.BuildConfig
import com.example.splitpay.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun payments(client: OkHttpClient): com.example.splitpay.payment.PaymentApi = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client.newBuilder().readTimeout(180, TimeUnit.SECONDS).callTimeout(180, TimeUnit.SECONDS).build()).addConverterFactory(GsonConverterFactory.create()).build().create(com.example.splitpay.payment.PaymentApi::class.java)
    @Provides @Singleton fun database(@ApplicationContext context: Context): SplitPayDatabase =
        Room.databaseBuilder(context, SplitPayDatabase::class.java, "splitpay.db").addMigrations(object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE personal_expenses ADD COLUMN ownerId TEXT NOT NULL DEFAULT 'demo-pratik'") }
        }, object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS group_cache (accountId TEXT NOT NULL, cacheKey TEXT NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(accountId, cacheKey))") }
        }, object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) { db.execSQL("ALTER TABLE personal_expenses ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'OTHER'");db.execSQL("ALTER TABLE personal_expenses ADD COLUMN receiptUrl TEXT NOT NULL DEFAULT ''") }
        }).build()
    @Provides fun sync(database: SplitPayDatabase): PersonalSyncDao = database.sync()
    @Provides @Singleton fun personalApi(client: OkHttpClient): PersonalApi = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(PersonalApi::class.java)
    @Provides fun groups(database: SplitPayDatabase): GroupCacheDao = database.groups()
    @Provides @Singleton fun groupApi(client: OkHttpClient): GroupApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(GroupApi::class.java)
    @Provides fun account(session: SessionRepository): AccountProvider = object : AccountProvider { override val id = session.account }
    @Provides fun expenses(database: SplitPayDatabase): ExpenseDao = database.expenses()
    @Provides @Singleton fun client(interceptor: FirebaseTokenInterceptor): OkHttpClient {
        return OkHttpClient.Builder().addInterceptor(interceptor)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    }
    @Provides @Singleton fun api(client: OkHttpClient): SplitPayApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create())
        .build().create(SplitPayApi::class.java)
}
