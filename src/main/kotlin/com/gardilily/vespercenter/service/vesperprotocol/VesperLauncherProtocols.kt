// SPDX-License-Identifier: MulanPSL-2.0

/*
    vesper 内部通信协议
    创建于2024年3月8日 上海市嘉定区
 */

package com.gardilily.vespercenter.service.vesperprotocol

import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import java.nio.ByteBuffer

class VesperLauncherProtocols private constructor() {
    open class Base : VesperProtocol() {
        companion object {
            const val MAGIC_STR = "OycF"
            const val typeCode = VesperProtocol.typeCode
        }

        override val magicStr get() = MAGIC_STR
        override val type get() = typeCode

    }


    class Response : Base() {
        companion object {
            const val typeCode = 0xA001u
        }

        override val type get() = typeCode

        override val bodyLength get() = (UInt.SIZE_BYTES * 2 + msg.size).toULong()

        var code = 0
        var msg = ByteArray(0)
        val msgString: String
            get() = String(msg)

        override fun decodeBody(data: ByteBuffer): Int {

            if (data.remaining() < UInt.SIZE_BYTES * 2) {
                log.warn("length ${data.remaining()} is too few for body")
                return 1
            }

            code = data.getInt()
            val msgLen = data.getInt()

            if (data.remaining() < msgLen) {
                log.warn("length ${data.remaining()} is less than msg-len $msgLen")
                return 2
            }

            msg = ByteArray(msgLen)
            data.get(msg)
            return 0
        }
    }


    class ShellLaunch : Base() {
        companion object {
            const val typeCode = 0x0001u
        }

        override val type get() = typeCode

        override val bodyLength get() = (cmd.length + Long.SIZE_BYTES).toULong()

        var cmd = ""

        override fun encodeBody(container: ArrayList<Byte>) {
            // cmd length
            ByteBuffer.allocate(Long.SIZE_BYTES).putLong(cmd.length.toLong()).array().forEach {
                container.add(it)
            }

            // cmd
            cmd.forEach { ch ->
                container.add(ch.code.toByte())
            }
        }
    }


}
