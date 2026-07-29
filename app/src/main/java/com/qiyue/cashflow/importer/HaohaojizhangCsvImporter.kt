package com.qiyue.cashflow.importer

import com.qiyue.cashflow.data.AccountEntity
import com.qiyue.cashflow.data.CategoryEntity
import com.qiyue.cashflow.data.EntryType
import com.qiyue.cashflow.data.TransactionEntity
import java.io.InputStream
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ImportedBook(
    val sourceRowCount: Int,
    val accounts: List<AccountEntity>,
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>
)

data class ImportResult(
    val parsedRows: Int,
    val insertedRows: Int,
    val skippedRows: Int
)

class HaohaojizhangCsvImporter(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun import(inputStream: InputStream): ImportedBook {
        val text = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            .removePrefix("\uFEFF")
        val rows = parseCsv(text)
        if (rows.isEmpty()) return ImportedBook(0, emptyList(), emptyList(), emptyList())

        val headers = rows.first()
        val records = rows.drop(1)
            .filter { row -> row.any { it.isNotBlank() } }
            .map { row -> headers.zip(row + List((headers.size - row.size).coerceAtLeast(0)) { "" }).toMap() }

        val transactions = buildList {
            records.forEachIndexed { index, record ->
                val transaction = record.toTransaction(index)
                add(transaction)
                val refund = record.toRefundTransaction(index)
                if (refund != null) add(refund)
            }
        }

        val accounts = transactions
            .flatMap { listOfNotNull(it.accountName, it.toAccountName) }
            .map { AccountEntity(name = it) }
            .distinctBy { it.name }

        val categories = transactions
            .map {
                CategoryEntity(
                    name = it.subcategory ?: it.category,
                    parentName = it.category.takeIf { _ -> it.subcategory != null },
                    type = it.type
                )
            }
            .distinctBy { "${it.parentName}|${it.name}|${it.type}" }

        return ImportedBook(
            sourceRowCount = records.size,
            accounts = accounts,
            categories = categories,
            transactions = transactions
        )
    }

    private fun Map<String, String>.toTransaction(index: Int): TransactionEntity {
        val type = when (value("类型")) {
            "支出" -> EntryType.EXPENSE
            "收入" -> EntryType.INCOME
            "债务" -> EntryType.DEBT
            "转账" -> EntryType.TRANSFER
            else -> EntryType.EXPENSE
        }
        val occurredAt = LocalDateTime.parse(value("日期"), dateTimeFormat)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        return TransactionEntity(
            occurredAtMillis = occurredAt,
            type = type,
            amountCents = moneyToCents(value("金额")),
            accountName = normalizeAccount(value("账户")),
            toAccountName = value("转入账户").takeIf { it.isNotBlank() }?.let(::normalizeAccount),
            category = value("分类").ifBlank { "未分类" },
            subcategory = value("子分类").ifBlank { null },
            note = value("备注").ifBlank { null },
            tags = value("标签").ifBlank { null },
            counterparty = value("债务人").ifBlank { null },
            feeCents = moneyToCents(value("转账手续费")),
            refundAmountCents = moneyToCents(value("退款金额")),
            refundAtMillis = parseDateOrNull(value("退款日期")),
            excludeFromBudget = value("不计入预算") == "是",
            excludeFromCashflow = value("不计入收支") == "是",
            sourceRowHash = sourceHash(index, this)
        )
    }

    private fun Map<String, String>.toRefundTransaction(index: Int): TransactionEntity? {
        val refundCents = moneyToCents(value("退款金额"))
        if (refundCents <= 0) return null

        val refundAt = parseDateOrNull(value("退款日期"))
            ?: LocalDateTime.parse(value("日期"), dateTimeFormat)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        return TransactionEntity(
            occurredAtMillis = refundAt,
            type = EntryType.REFUND,
            amountCents = refundCents,
            accountName = normalizeAccount(value("转入账户").ifBlank { value("账户") }),
            category = value("分类").ifBlank { "退款" },
            subcategory = value("子分类").ifBlank { null },
            note = buildString {
                append("退款")
                value("备注").takeIf { it.isNotBlank() }?.let { append("：").append(it) }
            },
            tags = value("标签").ifBlank { null },
            excludeFromBudget = value("不计入预算") == "是",
            excludeFromCashflow = value("不计入收支") == "是",
            sourceRowHash = sourceHash(index, this) + ":refund"
        )
    }

    private fun parseDateOrNull(raw: String): Long? {
        if (raw.isBlank()) return null
        val date = runCatching { LocalDate.parse(raw, dateFormat) }.getOrNull() ?: return null
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun moneyToCents(raw: String): Long {
        if (raw.isBlank()) return 0
        return BigDecimal(raw.trim()).movePointRight(2).setScale(0).longValueExact()
    }

    private fun normalizeAccount(raw: String): String =
        when (val account = raw.trim()) {
            "", "无账户", "未指定账户" -> "现金"
            "银行卡", "银行/理财" -> "银行"
            else -> account
        }

    private fun Map<String, String>.value(key: String): String = this[key]?.trim().orEmpty()

    private fun sourceHash(index: Int, record: Map<String, String>): String {
        val payload = buildString {
            append(index)
            record.toSortedMap().forEach { (key, value) -> append('|').append(key).append('=').append(value) }
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '"' && inQuotes && index + 1 < text.length && text[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    row.add(cell.toString())
                    cell.clear()
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                    row.add(cell.toString())
                    cell.clear()
                    rows.add(row.toList())
                    row.clear()
                }
                else -> cell.append(ch)
            }
            index++
        }

        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row.toList())
        }
        return rows
    }
}
