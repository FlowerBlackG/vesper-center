// SPDX-License-Identifier: MulanPSL-2.0
/*
    Vesper Protocol 服务（工具）。
    创建于2024年3月12日 上海市嘉定区
 */

package com.gardilily.vespercenter.service

import com.gardilily.vespercenter.common.Logger
import com.gardilily.vespercenter.service.vesperprotocol.VesperProtocol
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Path

@Service
class VesperProtocolService @Autowired constructor(
    val logger: Logger
) {

    /**
     *
     * @return resCode. 0 表示正常。非 0 表示读取失败。
     */
    protected fun SocketChannel.readVesperHeader(): Triple<String, UInt, ULong>? {
        val buf = ByteBuffer.allocate(VesperProtocol.HEADER_LEN)

        var totalBytesRead = 0
        while (totalBytesRead < VesperProtocol.HEADER_LEN) {
            val bytesRead = this.read(buf)
            if (bytesRead == -1) {
                return null
            }

            totalBytesRead += bytesRead
        }

        buf.flip()
        val magicByteArr = ByteArray(4)  // magic 4字节
        buf.get(magicByteArr)
        val magic = String(magicByteArr)

        val type = buf.getInt().toUInt()
        val length = buf.getLong().toULong()

        return Triple(magic, type, length)
    }


    fun send(request: VesperProtocol, socketPath: String): VesperProtocol? {
        return send(request, Path.of(socketPath))
    }


    fun send(request: VesperProtocol, socketPath: Path): VesperProtocol? {
        val address = UnixDomainSocketAddress.of(socketPath)

        SocketChannel.open(StandardProtocolFamily.UNIX).use { sc ->

            // 发送请求

            sc.connect(address)
            sc.write(request.toByteBuffer())

            // 接收应答

            val responseHeader = sc.readVesperHeader() ?: return null
            val magic = responseHeader.first
            val type = responseHeader.second
            val length = responseHeader.third
            if (length > 12uL * 1024uL) {
                Logger.error("read protocol response", "$length is too large!")
                return null
            }

            val buf = ByteBuffer.allocate(length.toInt())
            var totalBytesRead = 0
            while (totalBytesRead < length.toInt()) {
                val bytesRead = sc.read(buf)
                if (bytesRead == -1) {
                    return null
                }

                totalBytesRead += bytesRead
            }

            buf.flip()
            return VesperProtocol.decode(buf, magic, type)
        }
    }

}

