// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.dto

import org.springframework.http.HttpStatus

/**
 * 统一返回格式。
 * 参考自同济大学济星云项目和同心约项目。
 */
class IResponse<T> private constructor(
    val data: T?,
    val code: Int,
    val msg: String
) {

    companion object {

        /**
         * 表示请求成功。
         *
         * @param data 返回的数据。
         * @param msg 提示信息。
         * @param code http状态码。尽量保持为 OK 不变。
         */
        fun <T> ok(data: T? = null, msg: String? = null, code: HttpStatus = HttpStatus.OK): IResponse<T> {
            return IResponse(data, code.value(), msg ?: code.reasonPhrase)
        }

        /**
         * 表示请求失败。
         *
         * @param msg 友好的提示信息。最好适合于前端直接展示。
         */
        fun <T> error(data: T? = null, msg: String? = null, code: HttpStatus = HttpStatus.NOT_ACCEPTABLE): IResponse<T> {
            return IResponse(data, code.value(), msg ?: code.reasonPhrase)
        }
    }
}
