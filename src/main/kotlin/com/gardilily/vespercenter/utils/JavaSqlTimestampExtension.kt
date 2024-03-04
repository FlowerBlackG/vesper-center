// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.utils

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar

fun Timestamp.toIso8601(): String {
    return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").format(this)
}

fun Timestamp.toRfc3339() = this.toIso8601()

/**
 *
 * @param field Calendar.XXX
 */
fun Timestamp.after(field: Int, amount: Int): Timestamp {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = this.time
    calendar.add(field, amount)

    return Timestamp(calendar.time.time)
}
