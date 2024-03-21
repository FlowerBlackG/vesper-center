// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月16日 上海市嘉定区
 */


package com.gardilily.vespercenter.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * 让 Kotlin 支持类似 @Slf4j 的能力。
 *
 * 参考：
 *   https://zhuanlan.zhihu.com/p/357666365
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Slf4k {
    companion object {
        val <reified T> T.log: Logger
            inline get() = LoggerFactory.getLogger(T::class.java)
    }
}
