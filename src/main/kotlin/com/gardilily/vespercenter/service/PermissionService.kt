// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.entity.PermissionGrantEntity
import com.gardilily.vespercenter.entity.PermissionEntity
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.PermissionGrantMapper
import com.gardilily.vespercenter.mapper.PermissionMapper
import com.gardilily.vespercenter.utils.Slf4k
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
@Slf4k
class PermissionService @Autowired constructor(
    val permissionGrantMapper: PermissionGrantMapper
) : ServiceImpl<PermissionMapper, PermissionEntity>() {
    /**
     * 权限拒绝异常类。
     * 应在全局错误处理处捕获，并返回相应信息。
     */
    class PermissionDeniedException(message: String? = null) : Exception(message)

    fun ensurePermission(
        uid: Long,
        permission: PermissionEntity.Permission
    ) {
        if (!checkPermission(uid, permission)) {
            throw PermissionDeniedException(permission.name)
        }
    }

    fun ensurePermission(
        entity: UserEntity,
        permission: PermissionEntity.Permission
    ) {
        ensurePermission(entity.id!!, permission)
    }

    fun checkPermission(
        uid: Long,
        permission: PermissionEntity.Permission
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
        permission: PermissionEntity.Permission
    ) = checkPermission(entity.id!!, permission)

}
