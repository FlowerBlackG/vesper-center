// SPDX-License-Identifier: MulanPSL-2.0

/*
 * Vesper Center
 * 创建于 2024年2月29日 上海市嘉定区
 */

package com.gardilily.vespercenter

import lombok.extern.slf4j.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootApplication
@EnableTransactionManagement
class VesperCenterApplication

fun main(args: Array<String>) {
	runApplication<VesperCenterApplication>(*args)
}
