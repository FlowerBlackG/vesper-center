// SPDX-License-Identifier: MulanPSL-2.0

/* 上财果团团 */

package com.gardilily.vespercenter.common

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.text.SimpleDateFormat
import java.util.*

/**
 * 通用日志工具。
 */
@Component
class Logger @Autowired constructor() {

    companion object {
        fun printlnWithDateTime(log: String) {
            val date = Date()
            val fmt = SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
            println("${fmt.format(date)} - $log")
        }

        fun info(tag: String, msg: String) {
            printlnWithDateTime("[info]: $tag: $msg")
        }

        fun warning(tag: String, msg: String) {
            printlnWithDateTime("[warning]: $tag: $msg")
        }

        fun error(tag: String, msg: String) {
            printlnWithDateTime("[error]: $tag: $msg")
        }
    }

    fun info(tag: String, msg: String) = Logger.info(tag, msg)

    fun warning(tag: String, msg: String) = Logger.warning(tag, msg)

    fun error(tag: String, msg: String) = Logger.error(tag, msg)
}
