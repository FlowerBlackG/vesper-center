// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.properties

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component

/**
 * Vesper Center 软件系统通用参数定义。
 */
@Component
class VesperCenterProperties @Autowired constructor(
    environment: Environment
) {

    /* ------------ 系统参数。 ------------ */

    val systemVersionCode = environment.getProperty("vesper-center.system.version-code")!!.toLong()
    val systemVersionName = environment.getProperty("vesper-center.system.version-name")!!
    val systemBuildTime = environment.getProperty("vesper-center.system.build-time")!!

    val dataDir = run {
        val key = "vesper-center.data-dir"
        val f = environment.getProperty(key) ?: throw RuntimeException("environment $key is required.")
        f
    }

    val sessionTokenExpireMilliseconds = environment.getProperty("vesper-center.session.token-expire-milliseconds")?.toLong() ?: 3600000
    val sessionTicketLockerFileEnableDump = run {
        val key = "vesper-center.session.ticket-locker-file.enable-dump"
        val f = environment.getProperty(key) ?: "false"
        if (f != "true" && f != "false") {
            throw RuntimeException("$key should be true or false.")
        }
        return@run f == "true"
    }

    val sessionTicketLockerFileDumpKey = environment.getProperty("vesper-center.session.ticket-locker-file.dump-key")

    val corsAllowedOrigins = environment.getProperty("vesper-center.cors.allowed-origins")?.split(',') ?: emptyList()
}
