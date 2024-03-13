// SPDX-License-Identifier: MulanPSL-2.0
/*
    Vesper Control 服务。
    创建于2024年3月7日 上海市嘉定区
 */

package com.gardilily.vespercenter.service

import com.gardilily.vespercenter.service.vesperprotocol.VesperControlProtocols
import com.gardilily.vespercenter.service.vesperprotocol.VesperLauncherProtocols
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.net.Socket
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.nio.file.Path


@Service
class VesperControlService @Autowired constructor(
    val vesperProtocolService: VesperProtocolService
) {

    fun testConn() {

        val socketPath = Path.of("/run/user/1000/vesper.sock")
        val ter = VesperControlProtocols.TerminateVesper()
        val res = vesperProtocolService.send(ter, socketPath) as VesperControlProtocols.Response?

        val x = 1

        println("rescode: ${res?.code}, msg is: |${res?.msg}|")

    }


}
