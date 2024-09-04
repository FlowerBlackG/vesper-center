// SPDX-License-Identifier: MulanPSL-2.0
/*
    Vesper 服务。
    创建于2024年3月12日 上海市嘉定区
 */

package com.gardilily.vespercenter.service

import com.gardilily.vespercenter.common.MacroDefines
import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.service.vesperprotocol.VesperControlProtocols
import com.gardilily.vespercenter.service.vesperprotocol.VesperProtocol
import com.gardilily.vespercenter.utils.Slf4k
import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Service
@Slf4k
class VesperService @Autowired constructor(
    val linuxService: LinuxService
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

    fun <T> send(request: VesperProtocol, socketPath: String): T? {
        return send(request, socketPath) as T?
    }

    fun <T> send(request: VesperProtocol, socketPath: Path): T? {
        return send(request, socketPath) as T?
    }

    fun sendToVesper(request: VesperControlProtocols.Base, socketPath: Path): VesperControlProtocols.Response? {
        return send(request, socketPath) as VesperControlProtocols.Response?
    }

    fun sendToVesper(request: VesperControlProtocols.Base, socketPath: String): VesperControlProtocols.Response? {
        return send(request, Path.of(socketPath)) as VesperControlProtocols.Response?
    }

    fun sendToVesper(request: VesperControlProtocols.Base, seat: SeatEntity): VesperControlProtocols.Response? {
        return send(request, controlSockPathOf(seat)) as VesperControlProtocols.Response?
    }

    fun sendToVesperCore(req: VesperControlProtocols.Base, sock: String) = sendToVesper(req, sock)
    fun sendToVesperCore(req: VesperControlProtocols.Base, sock: Path) = sendToVesper(req, sock)
    fun sendToVesperControl(req: VesperControlProtocols.Base, sock: String) = sendToVesper(req, sock)
    fun sendToVesperControl(req: VesperControlProtocols.Base, sock: Path) = sendToVesper(req, sock)
    fun sendToVesperCtrl(req: VesperControlProtocols.Base, sock: String) = sendToVesper(req, sock)
    fun sendToVesperCtrl(req: VesperControlProtocols.Base, sock: Path) = sendToVesper(req, sock)


    fun send(request: VesperProtocol, socketPath: Path): VesperProtocol? {
        val address = UnixDomainSocketAddress.of(socketPath)

        linuxService.unlockFileAccess(socketPath.absolutePathString())

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
                log.error("$length is too large!")
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


    fun isVesperLauncherLive(linuxUid: Int): Boolean {
        return linuxService.getProcessList(
            executableName = "vesper-launcher",
            uid = linuxUid
        ).isNotEmpty()
    }


    fun isVesperLauncherLive(seat: SeatEntity) = isVesperLauncherLive(seat.linuxUid!!)

    fun isVesperLive(linuxUid: Int): Boolean {
        return linuxService.getProcessList(
            executableName = "vesper",
            uid = linuxUid
        ).isNotEmpty()
    }

    fun isVesperLive(seat: SeatEntity) = isVesperLive(seat.linuxUid!!)


    fun launcherSockPathOf(linuxUid: Int): String {
        return "${linuxService.xdgRuntimeDirOf(linuxUid)}/${MacroDefines.Vesper.LAUNCHER_SOCK}"
    }
    fun launcherSockPathOf(seat: SeatEntity) = launcherSockPathOf(seat.linuxUid!!)


    fun controlSockPathOf(linuxUid: Int): String {
        return "${linuxService.xdgRuntimeDirOf(linuxUid)}/${MacroDefines.Vesper.CONTROL_SOCK}"
    }
    fun controlSockPathOf(seat: SeatEntity) = controlSockPathOf(seat.linuxUid!!)
}

