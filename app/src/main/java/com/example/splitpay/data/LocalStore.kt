package com.example.splitpay.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "personal_expenses")
data class PersonalExpense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val merchant: String,
    val amountMinor: Long,
    val category: String,
    val date: String = LocalDate.now().toString(),
    val notes: String = "",
    val source: String = "MANUAL",
    val syncState: String = "PENDING_CREATE",
    @ColumnInfo(defaultValue = "'demo-pratik'") val ownerId: String = "demo-pratik",
    @ColumnInfo(defaultValue = "'OTHER'") val paymentMethod: String = "OTHER",
    @ColumnInfo(defaultValue = "''") val receiptUrl: String = ""
)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM personal_expenses WHERE ownerId = :ownerId AND syncState != 'PENDING_DELETE' ORDER BY date DESC, rowid DESC")
    fun observe(ownerId: String): Flow<List<PersonalExpense>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: PersonalExpense)
    @Query("DELETE FROM personal_expenses WHERE id = :id AND ownerId = :ownerId")
    suspend fun delete(id: String, ownerId: String)
}

@Database(entities = [PersonalExpense::class, GroupCache::class], version = 4, exportSchema = true)
abstract class SplitPayDatabase : RoomDatabase() { abstract fun expenses(): ExpenseDao; abstract fun groups(): GroupCacheDao; abstract fun sync(): PersonalSyncDao }

@Singleton
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ExpenseRepository @Inject constructor(private val dao: ExpenseDao, private val account: AccountProvider) {
    val expenses = account.id.flatMapLatest { dao.observe(it) }
    suspend fun save(merchant: String, amount: String, category: String, date: String, notes: String, source: String="MANUAL", paymentMethod: String="OTHER", receiptUrl: String="") {
        dao.insert(draft(merchant,amount,category,date,notes,source,paymentMethod,receiptUrl))
    }
    fun draft(merchant: String, amount: String, category: String, date: String, notes: String, source: String="MANUAL", paymentMethod: String="OTHER", receiptUrl: String=""): PersonalExpense {
        require(merchant.isNotBlank()) { "Enter a merchant or description." }
        val minor = try { amount.toBigDecimal().movePointRight(2).longValueExact() }
        catch (_: Exception) { throw IllegalArgumentException("Enter a valid amount with at most two decimal places.") }
        require(minor in 1..99999999999L) { "Amount must be between ₹0.01 and ₹999,999,999.99." }
        try { LocalDate.parse(date) } catch (_: Exception) { throw IllegalArgumentException("Use a valid date: YYYY-MM-DD.") }
        require(category in categories) { "Choose a valid category." }
        require(account.id.value.isNotEmpty()) { "Sign in to save expenses." }
        return PersonalExpense(merchant = merchant.trim(), amountMinor = minor, category = category, date = date, notes = notes.trim(), ownerId = account.id.value,source=source,paymentMethod=paymentMethod,receiptUrl=receiptUrl)
    }
    suspend fun delete(id: String) = dao.delete(id, account.id.value)
}

interface AccountProvider { val id: kotlinx.coroutines.flow.StateFlow<String> }

val categories = listOf("Food", "Travel", "Groceries", "Shopping", "Rent", "Bills", "Entertainment", "Health", "Education", "Subscriptions", "Other")
