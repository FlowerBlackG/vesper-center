// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月15日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.entity.GroupPermissionEntity
import com.gardilily.vespercenter.entity.GroupPermissionGrantEntity
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.entity.UserGroupEntity
import com.gardilily.vespercenter.mapper.GroupPermissionGrantMapper
import com.gardilily.vespercenter.mapper.GroupPermissionMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GroupPermissionService @Autowired constructor(
    val groupPermissionGrantMapper: GroupPermissionGrantMapper
) : ServiceImpl<GroupPermissionMapper, GroupPermissionEntity>() {


    fun ensurePermission(
        uid: Long,
        gid: Long,
        permission: GroupPermissionEntity.GroupPermission
    ) {
        if (!checkPermission(uid, gid, permission)) {
            throw PermissionService.PermissionDeniedException(permission.name)
        }
    }


    fun ensurePermission(
        entity: UserEntity,
        gid: Long,
        permission: GroupPermissionEntity.GroupPermission
    ) {
        ensurePermission(entity.id!!, gid, permission)
    }



    fun ensurePermission(
        entity: UserEntity,
        group: UserGroupEntity,
        permission: GroupPermissionEntity.GroupPermission
    ) {
        ensurePermission(entity.id!!, group.id!!, permission)
    }


    fun ensurePermission(
        uid: Long,
        group: UserGroupEntity,
        permission: GroupPermissionEntity.GroupPermission
    ) {
        ensurePermission(uid, group.id!!, permission)
    }



    fun checkPermission(
        uid: Long,
        gid: Long,
        permission: GroupPermissionEntity.GroupPermission
    ): Boolean {
        groupPermissionGrantMapper.selectOne(
            KtQueryWrapper(GroupPermissionGrantEntity::class.java)
                .eq(GroupPermissionGrantEntity::permissionId, permission)
                .eq(GroupPermissionGrantEntity::userId, uid)
                .eq(GroupPermissionGrantEntity::groupId, gid)
        ) ?: return false

        return true
    }

    fun checkPermission(
        entity: UserEntity,
        gid: Long,
        permission: GroupPermissionEntity.GroupPermission
    ) = checkPermission(entity.id!!, gid, permission)


    fun checkPermission(
        uid: Long,
        group: UserGroupEntity,
        permission: GroupPermissionEntity.GroupPermission
    ) = checkPermission(uid, group.id!!, permission)


    fun checkPermission(
        user: UserEntity,
        group: UserGroupEntity,
        permission: GroupPermissionEntity.GroupPermission
    ) = checkPermission(user.id!!, group.id!!, permission)


    fun clearAllPermissions(
        user: UserEntity,
        group: UserGroupEntity?
    ) {
        clearAllPermissions(user.id!!, group?.id)
    }

    fun clearAllPermissions(
        userId: Long,
        groupId: Long?
    ) {
        val query = KtQueryWrapper(GroupPermissionGrantEntity::class.java)
            .eq(GroupPermissionGrantEntity::userId, userId)

        if (groupId != null) {
            query.eq(GroupPermissionGrantEntity::groupId, groupId)
        }

        groupPermissionGrantMapper.delete(query)
    }

}
