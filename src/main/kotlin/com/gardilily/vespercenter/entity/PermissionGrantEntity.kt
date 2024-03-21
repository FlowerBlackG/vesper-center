// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */


package com.gardilily.vespercenter.entity

import com.baomidou.mybatisplus.annotation.TableName

@TableName(value = "permission_grant")
data class PermissionGrantEntity(
    var userId: Long = 0,
    var permissionId: PermissionEntity.Permission =
        PermissionEntity.Permission.UNDEFINED
)
