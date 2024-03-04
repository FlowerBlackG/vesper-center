// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.gardilily.vespercenter.common.Logger
import com.gardilily.vespercenter.entity.PermissionGrantEntity
import com.gardilily.vespercenter.entity.PermissionGroupEntity
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.PermissionGrantMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PermissionService @Autowired constructor(
    val logger: Logger,
    val permissionGrantMapper: PermissionGrantMapper
) {
    /**
     * 权限拒绝异常类。
     * 应在全局错误处理处捕获，并返回相应信息。
     */
    class PermissionDeniedException(message: String? = null) : Exception(message)

    fun ensurePermission(
        uid: Long,
        permission: PermissionGroupEntity.PermissionGroup
    ) {
        if (!checkPermission(uid, permission)) {
            throw PermissionDeniedException(permission.name)
        }
    }

    fun ensurePermission(
        entity: UserEntity,
        permission: PermissionGroupEntity.PermissionGroup
    ) {
        ensurePermission(entity.id!!, permission)
    }

    fun checkPermission(
        uid: Long,
        permission: PermissionGroupEntity.PermissionGroup
    ): Boolean {
        permissionGrantMapper.selectOne(
            KtQueryWrapper(PermissionGrantEntity::class.java)
                .eq(PermissionGrantEntity::permissionId, permission)
                .eq(PermissionGrantEntity::userId, uid)
        ) ?: return false

        return true
    }

    fun checkPermission(
        entity: UserEntity,
        permission: PermissionGroupEntity.PermissionGroup
    ) = checkPermission(entity.id!!, permission)

}
