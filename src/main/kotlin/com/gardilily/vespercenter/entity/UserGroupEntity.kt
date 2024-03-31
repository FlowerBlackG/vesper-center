// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */


package com.gardilily.vespercenter.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.sql.Timestamp

@TableName("user_group")
data class UserGroupEntity(
    @TableId(value = "id", type = IdType.AUTO)
    var id: Long? = null,
    var groupName: String? = null,
    var note: String? = null,
    var createTime: Timestamp? = null
)
