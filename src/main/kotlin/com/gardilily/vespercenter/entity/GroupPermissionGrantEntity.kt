// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */


package com.gardilily.vespercenter.entity

import com.baomidou.mybatisplus.annotation.TableName


@TableName(value = "group_permission_grant")
data class GroupPermissionGrantEntity(
    var userId: Long = 0,
    var groupId: Long = 0,
    var permissionId: GroupPermissionEntity.GroupPermission =
        GroupPermissionEntity.GroupPermission.UNDEFINED
)
