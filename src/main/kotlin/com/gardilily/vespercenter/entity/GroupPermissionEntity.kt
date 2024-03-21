// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */

package com.gardilily.vespercenter.entity

import com.baomidou.mybatisplus.annotation.IEnum
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.fasterxml.jackson.annotation.JsonValue


@TableName(value = "group_permission")
data class GroupPermissionEntity(
    @TableId(value = "id", type = IdType.AUTO)
    var id: GroupPermission? = null,
    var enumKey: String? = null,
    var note: String? = null
) {

    enum class GroupPermission(@JsonValue val enumValue: Long) : IEnum<Long> {


        UNDEFINED(0),

        /**
         * 组内赋权
         */
        GRANT_PERMISSION(1),

        /**
         * 删除一个组
         */
        DROP_GROUP(2),

        /**
         * 将用户移入或移出组。
         */
        ADD_OR_REMOVE_USER(100),

        /**
         * 在组内创建主机，以及删除组内任意主机。
         */
        CREATE_OR_DELETE_SEAT(200),

        /**
         * 编辑组内任意 seat 的名字。
         */
        NAME_ANY_SEAT(201),

        /**
         * 登录到组内任意主机。
         */
        LOGIN_TO_ANY_SEAT(202),

        /**
         * 收集指定位置的文件。
         */
        COLLECT_FILES(300),







        ;

        override fun getValue(): Long {
            return this.enumValue
        }


        companion object {
            fun allPermissions(): Collection<GroupPermission> {
                val res = entries.toMutableSet()
                res.remove(UNDEFINED)
                return res
            }
        }
    }
}

