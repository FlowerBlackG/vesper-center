// SPDX-License-Identifier: MulanPSL-2.0

package com.gardilily.vespercenter.dto

data class PagedResult <T> (
    val records: List<T>?,
    val pageNo: Long,
    val pageSize: Long,
    val total: Long,
)

