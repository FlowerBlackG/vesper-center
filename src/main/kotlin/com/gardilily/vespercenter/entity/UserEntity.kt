// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */

package com.gardilily.vespercenter.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.sql.Timestamp

@TableName("user")
data class UserEntity(
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long? = null,

    var username: String? = null,
    var passwd: String? = null,
    var createTime: Timestamp? = null,
    var lastLoginTime: Timestamp? = null,
)
