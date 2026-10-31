package com.wangzan101.mika.remote

/** Wake streaming clients on actual changes; timed waits are only heartbeats. */
object BridgeEvents {
    private val lock = java.lang.Object()
    private var revision = 0L
    fun signal() = synchronized(lock) { revision++; lock.notifyAll() }
    fun current(): Long = synchronized(lock) { revision }
    fun await(after: Long, milliseconds: Long): Long = synchronized(lock) {
        val deadline = android.os.SystemClock.elapsedRealtime() + milliseconds
        while (revision == after) {
            val remaining = deadline - android.os.SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            lock.wait(remaining)
        }
        revision
    }
}
