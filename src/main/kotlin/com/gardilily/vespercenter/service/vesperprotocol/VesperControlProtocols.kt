// SPDX-License-Identifier: MulanPSL-2.0

/*
    vesper 内部通信协议
    创建于2024年3月8日 上海市嘉定区
 */

package com.gardilily.vespercenter.service.vesperprotocol

import com.gardilily.vespercenter.utils.Slf4k
import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import java.nio.ByteBuffer

@Slf4k

class VesperControlProtocols private constructor() {
    open class Base : VesperProtocol() {
        companion object {
            const val MAGIC_STR = "KpBL"
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
        var msg: ByteArray = ByteArray(0)
        val msgString: String
            get() = String(msg)
        val msgAsString: String
            get() = msgString
        val msgAsInt: Int
            get() = ByteBuffer.wrap(msg).getInt()
        val msgAsLong: Long
            get() = ByteBuffer.wrap(msg).getLong()

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


    class TerminateVesper : Base() {
        companion object {
            const val typeCode = 0x0001u
        }

        override val type get() = typeCode
        override val bodyLength get() = 0uL

        override fun encodeBody(container: ArrayList<Byte>) {
            // do nothing.
        }

    }


    class GetVNCPort : Base() {
        companion object {
            const val typeCode = 0x0101u
        }

        override val type get() = typeCode
        override val bodyLength get() = 0uL

        override fun encodeBody(container: ArrayList<Byte>) {
            // do nothing.
        }
    }



    class GetVNCPassword : Base() {
        companion object {
            const val typeCode = 0x0102u
        }

        override val type get() = typeCode
        override val bodyLength get() = 0uL

        override fun encodeBody(container: ArrayList<Byte>) {
            // do nothing.
        }
    }


}
