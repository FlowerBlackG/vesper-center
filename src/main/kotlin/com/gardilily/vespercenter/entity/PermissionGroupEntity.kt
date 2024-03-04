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

@TableName(value = "permission_group")
data class PermissionGroupEntity(
    @TableId(value = "id", type = IdType.AUTO)
    var id: PermissionGroup? = null,
    var fullname: String? = null,
    var note: String? = null
) {

    enum class PermissionGroup(@JsonValue val enumValue: Long) : IEnum<Long> {
        UNDEFINED(0),

        GRANT_PERMISSION(1),

        CREATE_OR_DELETE_USER(20),
        CREATE_OR_DELETE_SEAT(50),


        LOGIN_TO_ANY_SEAT(100),

        ;

        override fun getValue(): Long {
            return this.enumValue
        }
    }
}
