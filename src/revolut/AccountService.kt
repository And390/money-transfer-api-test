package revolut

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


class AccountService {
    private class Account(
        var amount: Long
    )

    private val accounts = ConcurrentHashMap<Int, Account>()
    private val lastAccountId = AtomicInteger(0)

    fun create(startAmount: Long): Int {
        val id = lastAccountId.incrementAndGet()
        accounts[id] = Account(startAmount)
        return id
    }

    fun get(id: Int): Long? {
        val account = accounts[id] ?: return null
        synchronized(account) { return account.amount }
    }

    fun transfer(from: Int, to: Int, amount: Long) {
        if (amount <= 0) throw ClientException("Can not transfer non-positive amount")
        val fromAccount = accounts[from] ?: throw ClientException("Account not found: $from")
        val toAccount = accounts[to] ?: throw ClientException("Account not found: $to")
        val (account1, account2) = if (from < to) fromAccount to toAccount else toAccount to fromAccount
        synchronized(account1) {
            synchronized(account2) {
                if (fromAccount.amount < amount) throw ClientException("Not enough money")
                fromAccount.amount -= amount
                toAccount.amount += amount
            }
        }
    }
}
