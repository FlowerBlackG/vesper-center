// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月15日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.entity.GroupPermissionEntity
import com.gardilily.vespercenter.entity.PermissionEntity
import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.SeatMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class SeatService @Autowired constructor(
    val linuxService: LinuxService,
    val permissionService: PermissionService,
    val groupPermissionService: GroupPermissionService
) : ServiceImpl<SeatMapper, SeatEntity>() {


    /**
     * 删除某用户名下的所有主机。包含无分组和有分组的。
     */
    fun removeSeatsOf(userId: Long) {
        val query = KtQueryWrapper(SeatEntity::class.java)
            .eq(SeatEntity::userId, userId)

        removeSeats(query)
    }
    fun removeSeatsOf(user: UserEntity) = removeSeatsOf(user.id!!)

    /**
     * 删除某用户名下的特定分组主机。
     * groupId 为 null 时，指定删除无分组主机。
     */
    fun removeSeatsOf(userId: Long, groupId: Long?) {
        val query = KtQueryWrapper(SeatEntity::class.java)
            .eq(SeatEntity::userId, userId)
            .eq(SeatEntity::groupId, groupId)

        removeSeats(query)
    }


    private fun removeSeats(query: KtQueryWrapper<SeatEntity>) {
        val seats = baseMapper.selectList(query)

        seats.forEach { seat ->
            linuxService.forceLogout(seat)
            linuxService.removeUser(seat)
        }

        baseMapper.delete(query)
    }


    fun removeSeat(seat: SeatEntity) {
        linuxService.forceLogout(seat)
        linuxService.removeUser(seat)
        baseMapper.deleteById(seat)
    }


    fun checkLoginPermission(userId: Long, seat: SeatEntity): Boolean {
        return if (userId == seat.userId) {
            true
        } else if (permissionService.checkPermission(userId, PermissionEntity.Permission.LOGIN_TO_ANY_SEAT)) {
            true
        } else if (

            seat.groupId != null
            &&
            groupPermissionService.checkPermission(
                userId, seat.groupId!!, GroupPermissionEntity.GroupPermission.LOGIN_TO_ANY_SEAT
            )

        ) {
            true
        } else {
            false
        }

    }

    fun canLogin(userId: Long, seat: SeatEntity): Boolean {
        return if (!checkLoginPermission(userId, seat)) {
            false  // 没有登录权限，当然不允许。
        } else if (seat.isEnabled()) {
            true
        } else if (permissionService.checkPermission(userId, PermissionEntity.Permission.LOGIN_TO_DISABLED_SEAT)) {
            // disabled but I have the permission :D
            true
        } else if (
            seat.groupId != null
            &&
            groupPermissionService.checkPermission(userId, seat.groupId!!, GroupPermissionEntity.GroupPermission.LOGIN_TO_DISABLED_SEAT)
        ) {
            true
        } else {
            false
        }
    }
}
