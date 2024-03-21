// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */


package com.gardilily.vespercenter.entity

import com.baomidou.mybatisplus.annotation.TableName

@TableName("group_member")
data class GroupMemberEntity(
    var userId: Long = 0,
    var groupId: Long = 0,
)
