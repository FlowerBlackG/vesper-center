// SPDX-License-Identifier: MulanPSL-2.0

/*
    vesper 内部通信协议
    创建于2024年3月8日 上海市嘉定区
 */

package com.gardilily.vespercenter.service.vesperprotocol

import com.gardilily.vespercenter.common.Logger
import java.nio.ByteBuffer


open class VesperProtocol {
    companion object {
        const val MAGIC_STR = "...."
        const val typeCode = 0x0000u
        const val HEADER_LEN = 16

        /**
         * data 应该指向 header 后的第一个字节。外部需要保证 body length 正确。
         */
        fun decode(data: ByteBuffer, magic: String, type: UInt): VesperProtocol? {

            // 根据 magic 和 type 构建对象。

            val p = when (magic) {
                VesperLauncherProtocols.Base.MAGIC_STR -> when (type) {
                    VesperLauncherProtocols.Response.typeCode -> VesperLauncherProtocols.Response()
                    else -> {
                        Logger.warning("protocol decode", "type $type is unknown!")
                        return null
                    }
                }

                VesperControlProtocols.Base.MAGIC_STR -> when (type) {
                    else -> {
                        Logger.warning("protocol decode", "type $type is unknown!")
                        return null
                    }
                }

                else -> {
                    Logger.warning("protocol decode", "magic |$magic| is unknown!")
                    return null
                }
            }

            // 解析并返回结果。

            return when (p.decodeBody(data)) {
                0 -> p
                else -> null
            }
        } // fun decode
    } // companion object of interface VesperProtocol

    open val magicStr get() = MAGIC_STR // should be overridden
    open val type get() = typeCode // should be overridden

    fun encode(container: ArrayList<Byte>) {
        container.clear()

        /* header */

        // magic

        magicStr.forEach { ch -> container.add(ch.code.toByte()) }

        // type

        ByteBuffer.allocate(Int.SIZE_BYTES).putInt(type.toInt()).array().forEach {
            container.add(it)
        }

        // body length

        ByteBuffer.allocate(ULong.SIZE_BYTES).putLong(bodyLength.toLong()).array().forEach {
            container.add(it)
        }

        // body

        encodeBody(container)
    }

    fun toByteArray(): ByteArray {
        val arr = ArrayList<Byte>()
        this.encode(arr)
        return arr.toByteArray()
    }

    fun toByteBuffer(): ByteBuffer {
        return ByteBuffer.wrap( toByteArray() )
    }

    open fun decodeBody(data: ByteBuffer): Int {
        throw RuntimeException("base method called!")
    }

    protected open val bodyLength: ULong
        get() = 0uL

    protected open fun encodeBody(container: ArrayList<Byte>) {
        throw RuntimeException("base method called!")
    }
} // open class VesperProtocol

