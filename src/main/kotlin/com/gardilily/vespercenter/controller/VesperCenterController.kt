// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */

package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.annotation.IEnum
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.fasterxml.jackson.annotation.JsonValue
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.UserMapper
import com.gardilily.vespercenter.properties.VesperCenterProperties
import com.gardilily.vespercenter.service.LinuxService
import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate

@RestController
@RequestMapping("vesperCenter")
class VesperCenterController @Autowired constructor(
    val vesperCenterProperties: VesperCenterProperties,
    val userMapper: UserMapper,
    val linuxService: LinuxService,
    val restTemplate: RestTemplate
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


    @GetMapping("memoryUsage")
    fun getMemoryUsage(): IResponse<LinuxService.SystemMemoryUsage> {
        return IResponse.ok(linuxService.getSystemMemoryUsage())
    }

    @GetMapping("cpuUsage")
    fun getCpuUsage(): IResponse<Double> {
        return IResponse.ok(linuxService.getSystemCpuLoad())
    }

    data class GetUpdateLogResponseDtoEntry(
        var versionCode: Long = 0,
        var versionName: String = "",
        var completeTime: String = "",
        var updateLog: String = "",
        val software: Software,
    ) {
        enum class Software(@JsonValue val enumValue: String) : IEnum<String> {

            VESPER("vesper"),
            VESPER_LAUNCHER("vesper-launcher"),
            VESPER_CENTER("vesper-center"),
            VESPER_FRONT("vesper-front"),

            ;

            override fun getValue(): String {
                return this.enumValue
            }
        }
    }
    @GetMapping("updateLog")
    fun getUpdateLog(): IResponse<List<GetUpdateLogResponseDtoEntry>> {
        val res = ArrayList<GetUpdateLogResponseDtoEntry>()

        fun processSingleUpdate(content: String, software: GetUpdateLogResponseDtoEntry.Software) {
            val lines = content.replace("\r", "").split("\n").toMutableList()
            while (lines.firstOrNull()?.isEmpty() == true) {
                lines.removeFirst()
            }

            while (lines.lastOrNull()?.isEmpty() == true) {
                lines.removeLast()
            }

            if (lines.size < 4) {
                return
            }

            val entry = GetUpdateLogResponseDtoEntry(software = software)
            entry.versionName = lines.removeFirst().substringAfter(":").trim()
            entry.versionCode = lines.removeFirst().substringAfter(":").trim().toLong()
            entry.completeTime = lines.removeFirst().substringAfter(":").trim()
            lines.removeFirst()


            while (lines.firstOrNull()?.isEmpty() == true) {
                lines.removeFirst()
            }

            while (lines.lastOrNull()?.isEmpty() == true) {
                lines.removeLast()
            }

            val updateLog = StringBuilder()
            lines.forEach { updateLog.append(it).append('\n') }
            entry.updateLog = updateLog.toString()

            res.add(entry)
        }

        fun processFullUpdateLogFile(content: String, software: GetUpdateLogResponseDtoEntry.Software) {
            content.split("--------------------------------")
                .filter { it.isNotEmpty() }
                .forEach { processSingleUpdate(it, software) }
        }


        val urlTemplate = "https://git.tongji.edu.cn/vesper-system/(SOFTWARE)/-/raw/(BRANCH)/update-log.txt"

        listOf(
            Pair(GetUpdateLogResponseDtoEntry.Software.VESPER, urlTemplate.replace("(SOFTWARE)", "vesper").replace("(BRANCH)", "main")),
            Pair(GetUpdateLogResponseDtoEntry.Software.VESPER_LAUNCHER, urlTemplate.replace("(SOFTWARE)", "vesper-launcher").replace("(BRANCH)", "main")),
            Pair(GetUpdateLogResponseDtoEntry.Software.VESPER_FRONT, urlTemplate.replace("(SOFTWARE)", "vesper-front").replace("(BRANCH)", "master")),
            Pair(GetUpdateLogResponseDtoEntry.Software.VESPER_CENTER, urlTemplate.replace("(SOFTWARE)", "vesper-center").replace("(BRANCH)", "main")),
        ).forEach {
            val (soft, url) = it
            val file = restTemplate.getForObject(url, String::class.java)
            if (file != null) {
                processFullUpdateLogFile(file, soft)
            }
        }

        return IResponse.ok(res)
    }


    @GetMapping("ping")
    fun ping(): IResponse<String> {
        return IResponse.ok("pong")
    }

}
