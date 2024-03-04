// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */

package com.gardilily.vespercenter.common

class MacroDefines private constructor() {

    /**
     * Spring Session 存储键。
     */
    class SessionAttrKey private constructor() {
        companion object {
            const val USER_ID = "__buid"
        }
    } // class SessionAttrKey
}
