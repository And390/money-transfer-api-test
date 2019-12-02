package revolut

import org.testng.Assert.*
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.util.concurrent.*


class AccountServiceTest {

    private lateinit var service: AccountService

    @BeforeMethod
    fun beforeMethod() {
        service = AccountService()
    }

    @Test
    fun getNonExistedReturnsNull() {
        assertNull(service.get(1))
    }

    @Test
    fun getCreatedReturnsCreatedAmount() {
        val id = service.create(10L)
        assertEquals(service.get(id), 10L)
    }

    @Test
    fun getOneOfCreatedReturnsCreatedAmount() {
        service.create(10L)
        val id = service.create(15L)
        service.create(20L)
        assertEquals(service.get(id), 15L)
    }

    @Test
    fun getCreatedInAnotherThread() {
        val latch = CountDownLatch(1)
        val n = 16
        val executor = Executors.newFixedThreadPool(n)
        val futures = (1..n).map { i ->
            executor.submit(Callable {
                latch.await()
                service.create(i.toLong())
            })
        }
        latch.countDown()

        val ids = futures.map { it.get() }.toHashSet()
        assertEquals(ids.size, 16)
        val amounts = ids.map { service.get(it).also { assertNotNull(it) }!! }.sorted()
        assertEquals(amounts, (1L..n.toLong()).toList())
    }

    @Test
    fun canNotTransferNonExistent() {
        val id = service.create(10L)
        assertThrows(ClientException::class.java) { service.transfer(id, id + 1, 1L) }
        assertThrows(ClientException::class.java) { service.transfer(id + 1, id, 1L) }
    }

    @Test
    fun canNotTransferNonPositiveAmount() {
        val id1 = service.create(10L)
        val id2 = service.create(15L)
        assertThrows(ClientException::class.java) { service.transfer(id1, id2, 0) }
        assertThrows(ClientException::class.java) { service.transfer(id1, id2, -1) }
    }

    @Test
    fun canNotTransferMoreThanSourceAmount() {
        val id1 = service.create(10L)
        val id2 = service.create(20L)
        assertThrows(ClientException::class.java) { service.transfer(id1, id2, 15) }
    }

    @Test
    fun transfer() {
        val id1 = service.create(10L)
        val id2 = service.create(15L)
        service.transfer(id1, id2, 5L)

        assertEquals(service.get(id1), 5L)
        assertEquals(service.get(id2), 20L)
    }

    @Test
    fun transferInMultipleThreads() {
        for (j in 0..10) {
            val n = 16

            val id1 = service.create(n.toLong() / 2)
            val id2 = service.create(100L)

            val latch = CountDownLatch(1)
            val executor = Executors.newFixedThreadPool(n)
            val futures = (1..n).map { _ ->
                executor.submit(Callable {
                    latch.await()
                    try {
                        service.transfer(id1, id2, 1L)
                        false
                    } catch (e: ClientException) {
                        true
                    }
                })
            }
            latch.countDown()

            val errorCount = futures.count { it.get() }
            assertEquals(errorCount, n - n / 2)
            assertEquals(service.get(id1), 0L)
            assertEquals(service.get(id2), 100L + n / 2)
        }
    }
}