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
            const val USER_ID = "__uid"
        }
    } // class SessionAttrKey


    class RequestAttrKey private constructor() {
        companion object {
            const val VESPER_SESSION_TICKET = SessionManager.SESSION_ATTR_KEY
        }
    }


    class Vesper private constructor() {
        companion object {

            /**
             * Vesper Control Domain Socket 相对于 XDG_RUNTIME_DIR 的路径。
             */
            const val CONTROL_SOCK = "vesper.sock"
            const val LAUNCHER_SOCK = "vesper-launcher.sock"
            const val LIBVNCSERVER_PASSWD_FILE = "libvncserver-passwd.txt"
        }
    }

}
