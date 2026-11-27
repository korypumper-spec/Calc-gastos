package com.mibolsillointeligente.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mibolsillointeligente.app.data.local.entity.BudgetEntity
import com.mibolsillointeligente.app.data.local.entity.DebtEntity
import com.mibolsillointeligente.app.data.local.entity.DebtPaymentEntity
import com.mibolsillointeligente.app.data.local.entity.SavingsGoalEntity
import com.mibolsillointeligente.app.data.local.entity.ScheduledPaymentEntity
import com.mibolsillointeligente.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC")
    suspend fun getAllSnapshot(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreDuplicate(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface ScheduledPaymentDao {
    @Query("SELECT * FROM scheduled_payments ORDER BY dueDateEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<ScheduledPaymentEntity>>

    @Query("SELECT * FROM scheduled_payments WHERE id = :id")
    suspend fun getById(id: Long): ScheduledPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduledPaymentEntity): Long

    @Update
    suspend fun update(entity: ScheduledPaymentEntity)

    @Query("DELETE FROM scheduled_payments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scheduled_payments")
    suspend fun deleteAll()
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY id ASC")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavingsGoalEntity): Long

    @Update
    suspend fun update(entity: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAll()
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY id ASC")
    fun observeDebts(): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debt_payments ORDER BY dateEpochDay DESC, id DESC")
    fun observePayments(): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getById(id: Long): DebtEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(entity: DebtEntity): Long

    @Update
    suspend fun updateDebt(entity: DebtEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebtPayment(entity: DebtPaymentEntity): Long

    @Query("DELETE FROM debts WHERE id = :id")
    suspend fun deleteDebtById(id: Long)

    @Query("DELETE FROM debt_payments")
    suspend fun deleteAllPayments()

    @Query("DELETE FROM debts")
    suspend fun deleteAllDebts()
}
