// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.entity.GroupMemberEntity
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.entity.UserGroupEntity
import com.gardilily.vespercenter.mapper.GroupMemberMapper
import com.gardilily.vespercenter.mapper.UserGroupMapper
import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class UserGroupService @Autowired constructor(
    val groupMemberMapper: GroupMemberMapper,
    val groupPermissionService: GroupPermissionService,
    val seatService: SeatService,
) : ServiceImpl<UserGroupMapper, UserGroupEntity>() {


    fun removeUserFromGroup(user: UserEntity, groupId: Long, alsoSeats: Boolean = true) {
        removeUserFromGroup(user.id!!, groupId, alsoSeats)
    }


    fun removeUserFromGroup(userId: Long, group: UserGroupEntity, alsoSeats: Boolean = true) {
        removeUserFromGroup(userId, group.id!!, alsoSeats)
    }


    fun removeUserFromGroup(user: UserEntity, group: UserGroupEntity, alsoSeats: Boolean = true) {
        removeUserFromGroup(user.id!!, group.id!!, alsoSeats)
    }

    fun removeUserFromGroup(userId: Long, groupId: Long, alsoSeats: Boolean = true) {
        groupPermissionService.clearAllPermissions(userId, groupId)

        if (alsoSeats) {
            seatService.removeSeatsOf(userId, groupId)
        }

        groupMemberMapper.delete(
            KtQueryWrapper(GroupMemberEntity::class.java)
                .eq(GroupMemberEntity::userId, userId)
                .eq(GroupMemberEntity::groupId, groupId)
        )
    }

    fun removeUserFromAllGroups(user: UserEntity, alsoSeats: Boolean = true) = removeUserFromAllGroups(user.id!!, alsoSeats)

    fun removeUserFromAllGroups(userId: Long, alsoSeats: Boolean = true) {
        groupPermissionService.clearAllPermissions(userId, null)
        val groupMemberQuery = KtQueryWrapper(GroupMemberEntity::class.java)
            .eq(GroupMemberEntity::userId, userId)

        if (alsoSeats) {
            groupMemberMapper.selectList(groupMemberQuery).forEach {
                seatService.removeSeatsOf(userId = userId, groupId = it.groupId)
            }
        }

        groupMemberMapper.delete(
            groupMemberQuery
        )
    }

}
