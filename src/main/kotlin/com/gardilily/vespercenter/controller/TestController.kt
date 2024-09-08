// SPDX-License-Identifier: MulanPSL-2.0

package com.gardilily.vespercenter.controller

import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.service.LinuxService
import com.gardilily.vespercenter.service.VesperService
import com.gardilily.vespercenter.service.vesperprotocol.VesperControlProtocols
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.lang.management.ManagementFactory

@RestController
@RequestMapping("test")
class TestController @Autowired constructor(
    val linuxService: LinuxService,
    val vesperService: VesperService
) {


}
