// SPDX-License-Identifier: MulanPSL-2.0

package com.gardilily.vespercenter.controller

import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.service.LinuxService
import com.gardilily.vespercenter.service.VesperService
import com.gardilily.vespercenter.service.vesperprotocol.VesperControlProtocols
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.SessionAttribute
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@RestController
@RequestMapping("test")
class TestController @Autowired constructor(
    val linuxService: LinuxService,
    val vesperService: VesperService
) {



    @GetMapping("cTime")
    fun cTime(
        response: HttpServletResponse
    ): IResponse<String> {
        response.addHeader("vesper-test", "time: ${ System.currentTimeMillis() }")
        return IResponse.ok()
    }

    @GetMapping("send")
    fun send() {
        vesperService.sendToVesper(VesperControlProtocols.GetVNCPort(), "/run/user/1000/vesper.sock")
    }
}
