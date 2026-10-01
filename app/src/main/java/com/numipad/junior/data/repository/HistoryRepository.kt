package com.numipad.junior.data.repository

import com.numipad.junior.data.local.CalculationHistoryEntity
import com.numipad.junior.data.local.HistoryDao
import com.numipad.junior.data.local.NumiPadDatabase
import com.numipad.junior.domain.calculator.CalcOperator
import com.numipad.junior.domain.calculator.CalculationRecord
import com.numipad.junior.domain.progress.AppClock
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class HistoryItem(
    val id: Long,
    val first: String,
    val operator: CalcOperator,
    val second: String,
    val result: String,
    val rounded: Boolean,
    val createdAt: Long,
) {
    val expression: String get() = "$first ${operator.symbol} $second"
    val spoken: String get() = "$first ${operator.spokenName} $second equals ${if (rounded) "about " else ""}$result"
}

class HistoryRepository(
    private val db: NumiPadDatabase,
    private val clock: AppClock,
) {
    private val dao: HistoryDao = db.historyDao()

    val latest: Flow<List<HistoryItem>> = dao.observeLatest(MAX_ENTRIES).map { rows -> rows.mapNotNull { it.toItem() } }

    suspend fun add(record: CalculationRecord) {
        db.withTransaction {
            dao.insert(
                CalculationHistoryEntity(
                    firstOperand = record.first,
                    operator = record.operator.name,
                    secondOperand = record.second,
                    result = record.result,
                    rounded = record.rounded,
                    createdAt = clock.now().toEpochMilli(),
                ),
            )
            dao.trim(MAX_ENTRIES)
        }
    }

    suspend fun get(id: Long): HistoryItem? = dao.get(id)?.toItem()

    suspend fun clear() = dao.clear()

    private fun CalculationHistoryEntity.toItem(): HistoryItem? {
        val op = CalcOperator.fromName(operator) ?: return null
        return HistoryItem(id, firstOperand, op, secondOperand, result, rounded, createdAt)
    }

    companion object {
        const val MAX_ENTRIES = 50
    }
}
