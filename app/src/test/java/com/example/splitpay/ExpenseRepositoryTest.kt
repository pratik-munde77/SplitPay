package com.example.splitpay

import com.example.splitpay.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExpenseRepositoryTest {
    private class MemoryDao : ExpenseDao {
        val rows = MutableStateFlow<List<PersonalExpense>>(emptyList())
        override fun observe(ownerId: String) = rows
        override suspend fun insert(expense: PersonalExpense) { rows.value += expense }
        override suspend fun delete(id: String, ownerId: String) { rows.value = rows.value.filterNot { it.id == id && it.ownerId == ownerId } }
    }
    @Test fun savesExactPaiseAndDeletesById() = runBlocking {
        val dao = MemoryDao()
        val repository = ExpenseRepository(dao, object : AccountProvider { override val id = MutableStateFlow("demo-pratik") })
        repository.save(" Coffee ", "100.10", "Food", "2026-09-13", " Notes ")
        val row = dao.rows.value.single()
        assertEquals(10010L, row.amountMinor)
        assertEquals("Coffee", row.merchant)
        assertEquals("Notes", row.notes)
        repository.delete(row.id)
        assertTrue(dao.rows.value.isEmpty())
    }
    @Test fun invalidInputsNeverReachPersistence() = runBlocking {
        val dao = MemoryDao()
        val repository = ExpenseRepository(dao, object : AccountProvider { override val id = MutableStateFlow("demo-pratik") })
        for (amount in listOf("0", "-1", "1.001", "NaN", "999999999999999999999")) {
            try { repository.save("Shop", amount, "Food", "2026-09-13", ""); fail("Accepted $amount") }
            catch (_: IllegalArgumentException) { }
        }
        try { repository.save("Shop", "1", "Food", "2026-02-30", ""); fail("Accepted invalid date") }
        catch (_: IllegalArgumentException) { }
        assertTrue(dao.rows.value.isEmpty())
    }
}
