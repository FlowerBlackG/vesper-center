// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.utils

import org.json.JSONObject

/*
 * 针对 org.json 库的一些拓展。
 */

/**
 * 获取字符串，并在得不到时返回 null，而不是异常。
 */
fun JSONObject.getStringOrNull(key: String) = try {
    this.getString(key)
} catch (_: Exception) {
    null
}

/**
 * 获取字符串，并在失败时返回默认值。
 */
fun JSONObject.getString(key: String, defaultValue: String) = this.getStringOrNull(key) ?: defaultValue

fun JSONObject.getIntOrNull(key: String) = try {
    this.getInt(key)
} catch (_: Exception) {
    null
}
