// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月19日 上海市嘉定区
 */

package com.gardilily.vespercenter.common

import com.gardilily.vespercenter.properties.VesperCenterProperties
import com.gardilily.vespercenter.utils.Slf4k
import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import jakarta.annotation.PreDestroy
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.HashMap
import kotlin.random.Random
import kotlin.text.HexFormat

@Slf4k
@Component
class SessionManager @Autowired constructor(
    val vesperCenterProperties: VesperCenterProperties
) {

    /* 定义键 */
    companion object {
        const val VERSION = 1

        const val HTTP_HEADER_KEY = "vesper-session"
        const val SESSION_ATTR_KEY = "vesper-session"
    }


    /* 对外方法 */
    fun doFilter(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token: String? = request.getHeader(HTTP_HEADER_KEY)

        run {
            if (token == null) {
                return@run
            }

            if (!ticketLocker.contains(token)) {
                return@run
            }

            val ticket = ticketLocker[token]!!
            if (ticket.expired) {
                ticketLocker.remove(token)
                return@run
            }


            // 运行到这里，表明 ticket 存在且未过期

            ticket.refreshLife()
            request.setAttribute(SESSION_ATTR_KEY, ticket)
        }

        chain.doFilter(request, response)
    }


    fun addSession(userId: Long): String {
        val session = createSessionData(userId)
        ticketLocker[session.key] = session
        return session.key
    }


    fun remove(token: String): Boolean {
        return ticketLocker.remove(token) != null
    }


    /* 内部方法 */
    @OptIn(ExperimentalStdlibApi::class)
    private fun createSessionData(userId: Long): Ticket {
        val key = StringBuilder()

        key.append(UUID.randomUUID().toString().replace("-", "")) // 随机 uuid
            .append(":")
            .append(System.currentTimeMillis().toHexString(HexFormat.Default)) // 时间戳
            .append(":")
            .append(Random.nextLong().toHexString(HexFormat.Default)) // 随机 int64
            .append(":")
            .append(vesperCenterProperties.systemVersionCode) // vesper center version code
            .append(":")
            .append("$userId") // user id

        return Ticket(
            userId = userId,
            key = key.toString(),
            sessionMgr = this,
            vesperCenterVersionCode = vesperCenterProperties.systemVersionCode
        )
    }

    private fun getUserIdFrom(token: String?): Long? {
        token ?: return null

        val idx = token.lastIndexOf(':')
        return if (idx != -1) {
            try {
                token.substring(idx + 1).toLong()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun getTicket(token: String): Ticket? {
        return ticketLocker[token]
    }


    /* 结构定义 */
    data class Ticket(
        var userId: Long,
        /** 发送给前端的 key，用于附在 header 里表明身份。 */
        var key: String,
        var createTime: Long = System.currentTimeMillis(),
        var vesperCenterVersionCode: Long,
        var sessionMgr: SessionManager
    ) {

        var expireTime: Long = createTime + sessionMgr.vesperCenterProperties.sessionTokenExpireMilliseconds
            private set(value) {field = value}
            get() = field

        val expired: Boolean
            inline get() {
                return System.currentTimeMillis() > expireTime
            }


        fun extendLife(timeMillis: Long?) {
            if (timeMillis != null) {
                expireTime += timeMillis
            } else {
                refreshLife()
            }
        }

        fun refreshLife() {
            expireTime = System.currentTimeMillis() + sessionMgr.vesperCenterProperties.sessionTokenExpireMilliseconds
        }

        override fun toString(): String {
            val json = JSONObject()

            serializableMembers.forEach {
                json.put(it.name, it.get(this))
            }

            return json.toString()
        }

        companion object {
            private val serializableMembers = listOf(
                Ticket::userId, Ticket::key, Ticket::createTime,
                Ticket::vesperCenterVersionCode, Ticket::expireTime
            )

            fun fromString(str: String, sessionMgr: SessionManager): Ticket? {
                try {
                    val json = JSONObject(str)
                    val ticket = Ticket(
                        userId = -1,
                        key = "",
                        vesperCenterVersionCode = -1,
                        sessionMgr = sessionMgr,
                    )

                    serializableMembers.forEach {
                        val field = Ticket::class.java.getDeclaredField(it.name)
                        val accessible = field.isAccessible
                        field.isAccessible = true
                        field.set(ticket, json.get(it.name))
                        field.isAccessible = accessible
                    }

                    return ticket
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
        }
    }

    /* 数据成员 */

    /**
     * 会话“柜子”。
     *
     * 柜子的结构是从用户名映射到 Ticket 对象。
     * 外部需要处理安全性问题。
     */
    @Slf4k
    private class TicketLocker(
        private val sessionMgr: SessionManager,
        private val dumpPath: String? = null,
        dumpKey: String? = null
    ) {

        private val dumpAesKey = if (dumpKey != null) {
            SecretKeySpec(dumpKey.toByteArray(), "AES")
        } else {
            null
        }

        private val dumpAesIv = IvParameterSpec(ByteArray(16))
        private val dumpAlgorithm = "AES/CBC/PKCS5Padding"

        private companion object {
            const val DUMP_MAGIC = "chiikawa"
        }

        private val locker = HashMap<String, Ticket>()

        /**
         *
         * 文件存储结构：
         *     经过加密的串。
         *     原始内容：
         *         line 1: ticket locker's DUMP_MAGIC
         *         line 2: session mgr's VERSION
         *         line 3 .. (n+2): (n-2)'s ticket
         */
        fun dumpToFile(): Boolean {
            if (dumpPath == null) {
                return false
            }

            val file = File(dumpPath)
            if (file.exists()) {
                file.delete()
            }

            file.parentFile.mkdirs()
            file.createNewFile()

            val txtBuilder = StringBuilder(DUMP_MAGIC)
                .append('\n')
                .append(VERSION)
                .append('\n')

            for (it in locker) {
                val ticket = it.value
                txtBuilder.append(ticket).append('\n')
            }

            val txt = if (dumpAesKey != null) {
                val cipher = Cipher.getInstance(dumpAlgorithm)
                cipher.init(Cipher.ENCRYPT_MODE, dumpAesKey, dumpAesIv)
                val cipherText = cipher.doFinal(txtBuilder.toString().toByteArray())
                Base64.getEncoder().encodeToString(cipherText)
            } else {
                txtBuilder.toString()
            }

            file.printWriter().use { out ->
                out.print(txt)
            }

            log.info("ticket-locker stored to file: ${file.absolutePath}")
            return true
        }

        fun loadFromFile(): Boolean {
            if (dumpPath == null) {
                return false
            }

            val file = File(dumpPath)
            if (!file.exists()) {
                return false
            }

            val txtRaw = file.inputStream().bufferedReader().use { it.readText() }

            try {

                val txt = if (dumpAesKey != null) {
                    val cipher = Cipher.getInstance(dumpAlgorithm)
                    cipher.init(Cipher.DECRYPT_MODE, dumpAesKey, dumpAesIv)
                    val plainText = cipher.doFinal(Base64.getDecoder().decode(txtRaw))
                    String(plainText)
                } else {
                    txtRaw
                }

                val txtLines = txt.split('\n').toMutableList()

                // check magic
                if (txtLines.removeFirstOrNull() != DUMP_MAGIC) {
                    log.error("magic mismatch! failed to load from file.")
                    return false
                }

                // check version
                if (txtLines.firstOrNull() != VERSION.toString()) {
                    log.error("version ${txtLines.firstOrNull()} is not compatible with current: $VERSION.")
                    return false
                }
                txtLines.removeFirstOrNull()

                txtLines.forEach { line ->
                    if (line.isBlank()) {
                        return@forEach
                    }

                    val ticket = Ticket.fromString(line, sessionMgr)
                    if (ticket != null) {
                        locker[ticket.key] = ticket
                    }
                }

                log.info("loaded ticket-locker from file: ${file.absolutePath}")
            } catch (e: Exception) {
                e.printStackTrace()
                log.error("failed to decode ticket locker file!")
                locker.clear()
                return false
            } // end of try-catch

            return true
        } // fun loadFromFile


        operator fun get(key: String?): Ticket? {
            if (key == null) {
                return null
            }
            return locker[key]
        }

        operator fun set(key: String?, value: Ticket?) {
            if (key == null) {
                return
            }

            if (value == null) {
                locker.remove(key)
            } else {
                locker[key] = value
            }
        }

        fun remove(key: String?): Ticket? {
            return if (key == null) {
                null
            } else {
                locker.remove(key)
            }
        }

        fun contains(key: String?): Boolean {
            if (key == null) {
                return false
            }
            return locker.contains(key)
        }
    }


    private val ticketLocker = TicketLocker(
        this,
        "${vesperCenterProperties.dataDir}/SessionManager/vesper-center-session.txt",
        vesperCenterProperties.sessionTicketLockerFileDumpKey
    )

    init {
        ticketLocker.loadFromFile()
    }


    /* Event Hooks */
    @PreDestroy
    private fun onSpringBootShutdown() {
        ticketLocker.dumpToFile()
    }

}

