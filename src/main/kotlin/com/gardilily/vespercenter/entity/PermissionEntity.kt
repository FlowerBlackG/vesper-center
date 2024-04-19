// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */

package com.gardilily.vespercenter.entity

import com.baomidou.mybatisplus.annotation.IEnum
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.fasterxml.jackson.annotation.JsonValue

@TableName(value = "permission")
data class PermissionEntity(
    @TableId(value = "id", type = IdType.AUTO)
    var id: Permission? = null,
    var enumKey: String? = null,
    var note: String? = null
) {

    enum class Permission(@JsonValue val enumValue: Long) : IEnum<Long> {


        UNDEFINED(0),

        /**
         * 管理所有用户的权限
         */
        GRANT_PERMISSION(1),

        /**
         * 创建和删除自己创建的用户
         */
        CREATE_AND_DELETE_USER(100),

        /**
         * 删除任何用户
         */
        DELETE_ANY_USER(101),

        /**
         * 创建和删除自己创建的桌面环境
         */
        CREATE_SEAT(200),

        /**
         * 删除任何 seat
         */
        DELETE_ANY_SEAT(201),

        /**
         * 编辑任意 seat 的名字
         */
        NAME_ANY_SEAT(202),

        /**
         * 登录到任意用户的环境
         */
        LOGIN_TO_ANY_SEAT(203),

        /**
         * 登录到已经被关闭的主机
         */
        LOGIN_TO_DISABLED_SEAT(204),

        /**
         * 禁用或启用主机
         */
        DISABLE_OR_ENABLE_SEAT(205),

        /**
         * 创建组。包含删除自己组的权限。创建后，自动获取组内一切权限
         */
        CREATE_GROUP(300),

        /**
         * 编辑任意组的组内成员权限
         */
        MODIFY_ANY_GROUP_MEMBERS_PERMISSION(301),




        ;

        override fun getValue(): Long {
            return this.enumValue
        }


        companion object {
            fun allPermissions(): Collection<Permission> {
                val res = entries.toMutableSet()
                res.remove(UNDEFINED)
                return res
            }
        }
    }
}
