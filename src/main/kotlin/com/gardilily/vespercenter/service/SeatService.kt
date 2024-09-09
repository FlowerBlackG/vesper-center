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
import com.gardilily.vespercenter.entity.UserGroupEntity
import com.gardilily.vespercenter.mapper.SeatMapper
import com.gardilily.vespercenter.properties.VesperCenterProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.random.Random
import kotlin.random.nextUInt

@Service
class SeatService @Autowired constructor(
    val linuxService: LinuxService,
    val permissionService: PermissionService,
    val groupPermissionService: GroupPermissionService,
    val vesperCenterProperties: VesperCenterProperties
) : ServiceImpl<SeatMapper, SeatEntity>() {

    val SSH_FOLDER_LOCKER = "${vesperCenterProperties.dataDir}/SeatService/ssh_folder_locker"


    init {

        // create ssh folder locker

        val sshFolderLocker = File(SSH_FOLDER_LOCKER)
        if (!sshFolderLocker.exists()) {
            Files.createDirectories(sshFolderLocker.toPath())
        }

    }


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


    fun disable(seatId: Long, alsoQuit: Boolean = true, dontUpdateDatabase: Boolean = false) {
        val seat = getById(seatId) ?: return
        if (seat.isDisabled()) {
            return
        }

        linuxService.updatePassword(seat, (1000UL + Random.nextUInt()).toString()) // disable password login

        // disable ssh login
        val sshFolderUser = Path("/home/${seat.linuxLoginName}/.ssh")
        val sshFolderInLocker = Path("$SSH_FOLDER_LOCKER/$seatId.ssh")
        if (linuxService.shellTest("-d $sshFolderUser", sudo = true) == 0) {
            linuxService.move(sshFolderUser.absolutePathString(), sshFolderInLocker.absolutePathString(), sudo = true)
        }

        if (alsoQuit) {
            linuxService.forceLogout(seat)
        }

        if (!dontUpdateDatabase) {
            seat.seatEnabled = false
            updateById(seat)
        }
    }


    fun disable(seats: List<SeatEntity>, alsoQuit: Boolean = true) {
        seats.forEach { disable(it.id!!, alsoQuit) }
    }


    fun enable(seatId: Long, dontUpdateDatabase: Boolean = false) {
        val seat = getById(seatId) ?: return
        if (seat.isEnabled()) {
            return
        }

        linuxService.updatePassword(seat, seat.linuxPasswdRaw!!)

        // restore ssh login
        val sshFolderUser = Path("/home/${seat.linuxLoginName}/.ssh")
        val sshFolderInLocker = Path("$SSH_FOLDER_LOCKER/$seatId.ssh")
        if (linuxService.shellTest("-d $sshFolderInLocker", sudo = true) == 0) {
            linuxService.move(sshFolderInLocker.absolutePathString(), sshFolderUser.absolutePathString(), sudo = true)
        }


        if (!dontUpdateDatabase) {
            seat.seatEnabled = true
            updateById(seat)
        }
    }


    fun enable(seats: List<SeatEntity>) {
        seats.forEach { enable(it.id!!) }
    }


    fun changeGroup(seat: SeatEntity, group: UserGroupEntity?) {
        changeGroup(seat, group?.id)
    }


    fun changeGroup(seat: SeatEntity, groupId: Long?) {
        seat.groupId = groupId
        updateById(seat)
    }

}
