// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.service.IService
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.UserMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.util.DigestUtils



@Service
class UserService @Autowired constructor(
    val userGroupService: UserGroupService,
    val permissionService: PermissionService,
    val groupMemberService: GroupMemberService,
    val seatService: SeatService

) : ServiceImpl<UserMapper, UserEntity>() {

    fun removeUser(userId: Long) {
        userGroupService.removeUserFromAllGroups(userId, false)
        permissionService.clearAllPermissions(userId)
        seatService.removeSeatsOf(userId)
        this.removeById(userId)
    }

    fun removeUser(user: UserEntity) {
        removeUser(user.id!!)
    }

}
