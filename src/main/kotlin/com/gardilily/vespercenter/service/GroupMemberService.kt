// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月15日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.entity.GroupMemberEntity
import com.gardilily.vespercenter.mapper.GroupMemberMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GroupMemberService @Autowired constructor(
    val userService: UserService,
) : ServiceImpl<GroupMemberMapper, GroupMemberEntity>() {
    fun addUsersToGroup(users: List<Long>, groupId: Long) {
        users.forEach {
            addUserToGroup(it, groupId)
        }
    }

    fun addUserToGroup(userId: Long, groupId: Long): Boolean {
        // 检查用户是否存在。
        if (userService.getById(userId) == null) {
            log.error("user $userId not exists.")
            return false
        }

        val insertRes = baseMapper.insert(
            GroupMemberEntity(
                userId = userId,
                groupId = groupId,
            )
        )

        return insertRes == 1
    }
}
