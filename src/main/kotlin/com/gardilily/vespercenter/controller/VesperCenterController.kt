// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */

package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.UserMapper
import com.gardilily.vespercenter.properties.VesperCenterProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("vesperCenter")
class VesperCenterController @Autowired constructor(
    val vesperCenterProperties: VesperCenterProperties,
    val userMapper: UserMapper
) {

    data class GetSystemVersionResponseDto(
        val versionCode: Long,
        val versionName: String,
        val buildTime: String,
    )
    @GetMapping("version")
    fun getSystemVersion(): IResponse<GetSystemVersionResponseDto> {
        return IResponse.ok(GetSystemVersionResponseDto(
            versionCode = vesperCenterProperties.systemVersionCode,
            versionName = vesperCenterProperties.systemVersionName,
            buildTime = vesperCenterProperties.systemBuildTime
        ))
    }

    @GetMapping("systemInitialized")
    fun getSystemInitialized(): IResponse<Boolean> {
        return IResponse.ok(
            userMapper.selectCount(KtQueryWrapper(UserEntity::class.java)) != 0L
        )
    }

}