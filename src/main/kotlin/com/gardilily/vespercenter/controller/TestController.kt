// SPDX-License-Identifier: MulanPSL-2.0

package com.gardilily.vespercenter.controller

import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.properties.VesperCenterSystemProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("test")
class TestController @Autowired constructor(
    val vesperCenterSystemProperties: VesperCenterSystemProperties
) {

    @GetMapping("version")
    fun version(): IResponse<String> {
        return IResponse.ok(vesperCenterSystemProperties.systemVersionName)
    }


}
