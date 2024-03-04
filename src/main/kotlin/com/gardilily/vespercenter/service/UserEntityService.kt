// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.service

import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.UserMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class UserEntityService @Autowired constructor(
    val userMapper: UserMapper
) {

    class UserNotFoundException(message: String? = null) : RuntimeException(message)

    fun getUserEntity(uid: Long): UserEntity {
        val entity = userMapper.selectById(uid) ?: throw UserNotFoundException("没有这个用户。")
        return entity
    }

    operator fun get(uid: Long) = getUserEntity(uid)

}
