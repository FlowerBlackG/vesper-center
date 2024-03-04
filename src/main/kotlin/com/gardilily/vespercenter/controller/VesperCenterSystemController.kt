// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */

package com.gardilily.vespercenter.controller

import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.properties.VesperCenterSystemProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("vesperCenterSystem")
class VesperCenterSystemController @Autowired constructor(
    val vesperCenterSystemProperties: VesperCenterSystemProperties
) {

    data class GetSystemVersionResponseDto(
        val versionCode: Long,
        val versionName: String,
        val buildTime: String,
    )
    @GetMapping("version")
    fun getSystemVersion(): IResponse<GetSystemVersionResponseDto> {
        return IResponse.ok(GetSystemVersionResponseDto(
            versionCode = vesperCenterSystemProperties.systemVersionCode,
            versionName = vesperCenterSystemProperties.systemVersionName,
            buildTime = vesperCenterSystemProperties.systemBuildTime
        ))
    }

}