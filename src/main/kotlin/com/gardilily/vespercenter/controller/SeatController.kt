// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月17日 上海市嘉定区
 */


package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.gardilily.vespercenter.common.MacroDefines
import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.GroupMemberEntity
import com.gardilily.vespercenter.entity.PermissionEntity
import com.gardilily.vespercenter.entity.PermissionEntity.Permission
import com.gardilily.vespercenter.entity.GroupPermissionEntity.GroupPermission
import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.service.*
import com.gardilily.vespercenter.service.vesperprotocol.VesperLauncherProtocols
import com.gardilily.vespercenter.utils.Slf4k
import com.gardilily.vespercenter.utils.toHashMapWithKeysEvenNull
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import java.net.ServerSocket
import java.sql.Timestamp
import java.util.UUID
import javax.swing.GroupLayout.Group
import kotlin.random.Random

@RestController
@RequestMapping("seat")
@Slf4k
class SeatController @Autowired constructor(
    val seatService: SeatService,
    val linuxService: LinuxService,
    val userGroupService: UserGroupService,
    val userService: UserService,
    val userEntityService: UserEntityService,
    val groupPermissionService: GroupPermissionService,
    val permissionService: PermissionService,
    val groupMemberService: GroupMemberService,
    val vesperService: VesperService,
) {


    class CreateSeatsResponseDto private constructor() {
        data class Entry(
            val userId: Long,
            val groupId: Long?,
            val seatInfo: HashMap<String, Any?> // based on SeatEntity
        )
    }

    @Operation(summary = "新建桌面环境。支持批量创建")
    @Parameters(
        Parameter(name = "group", description = "所属组号。可以为空"),
        Parameter(name = "users", description = "用户id列表", required = true),
        Parameter(name = "note", description = "为主机附上的描述")
    )
    @PostMapping("new")
    fun createSeats(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, *>
    ): IResponse<List<CreateSeatsResponseDto.Entry>> {
        // 参数校验
        val users = body["users"] as Array<*>? ?: return IResponse.error(msg = "users required.")
        val group = body["group"] as Long? // nullable
        val note = body["note"] as String? // nullable

        // 权限检查
        permissionService.ensurePermission(ticket.userId, Permission.CREATE_SEAT)
        if (group != null) {
            groupPermissionService.ensurePermission(ticket.userId, group, GroupPermission.CREATE_OR_DELETE_SEAT)
        }

        // 创建主机
        val successList = ArrayList<CreateSeatsResponseDto.Entry>()

        users.forEach {
            val uid = try { it as Long } catch (_: Exception) { return@forEach }

            // 检查用户在不在组里面。
            if (group != null) {
                val existsQuery = KtQueryWrapper(GroupMemberEntity::class.java)
                    .eq(GroupMemberEntity::groupId, group)
                    .eq(GroupMemberEntity::userId, uid)
                if (!groupMemberService.exists(existsQuery)) {
                    return@forEach
                }
            }

            // 新建主机。
            val seatEntity = SeatEntity(
                userId = uid,
                groupId = group,
                creator = ticket.userId,
                enabled = 1,
                note = note,
                linuxUid = -1,
                linuxLoginName = "/",
                linuxPasswdRaw = "",
                createTime = Timestamp(System.currentTimeMillis())
            )
            seatService.baseMapper.insert(seatEntity)

            try {
                seatEntity.linuxLoginName = "vesper_center_${seatEntity.id}"
                seatEntity.linuxPasswdRaw = UUID.randomUUID().toString().substring(0 until 16)
                seatEntity.linuxUid = linuxService.createUser(
                    seatEntity.linuxLoginName!!,
                    seatEntity.linuxPasswdRaw!!
                ) ?: throw Exception("linux service failed to create user.")

                seatService.updateById(seatEntity)
                successList.add(
                    CreateSeatsResponseDto.Entry(
                    userId = uid,
                    groupId = group,
                    seatInfo = seatEntity.toHashMapWithKeysEvenNull(
                        SeatEntity::id, SeatEntity::userId, SeatEntity::creator,
                        SeatEntity::enabled, SeatEntity::nickname, SeatEntity::note,
                        SeatEntity::linuxUid, SeatEntity::linuxLoginName,
                        SeatEntity::createTime, SeatEntity::lastLoginTime
                    )
                ))
            } catch (e: Exception) {
                e.printStackTrace()
                seatService.removeById(seatEntity)
            }
        }

        return IResponse.ok(successList)
    }


    @Operation(summary = "为主机编写备注名")
    @Parameters(
        Parameter(name = "seatId"),
        Parameter(name = "name"),
    )
    @PostMapping("name")
    fun nameASeat(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, *>
    ): IResponse<Unit> {
        val userId = ticket.userId

        // 参数检查
        val seatId = body["seatId"] as Long? ?: return IResponse.error(msg = "seatId required.")
        val name = body["name"] as String? ?: return IResponse.error(msg = "name required.")

        val seat = seatService.getById(seatId) ?: return IResponse.error(msg = "非法操作。")

        // 权限检查
        if (seat.userId == userId) {
            // permission check passed.
        } else if (permissionService.checkPermission(userId, Permission.NAME_ANY_SEAT)) {
            // permission check passed.
        } else if (
            seat.groupId != null
            && groupPermissionService.checkPermission(
                userId, seat.groupId!!, GroupPermission.NAME_ANY_SEAT
            )
        ) {
            // permission check passed.
        } else {
            throw PermissionService.PermissionDeniedException("没有权限。")
        }

        // 修改名称
        seat.nickname = name

        return if (seatService.updateById(seat)) {
            IResponse.ok()
        } else {
            IResponse.error()
        }

    }



    data class StartSeatResponseDto(
        val seat: HashMap<String, Any?>,
    )


    @Operation(summary = "启动一个 seat")
    @Parameters(
        Parameter(name = "seatId")
    )
    @PostMapping("start")
    fun startSeat(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, *>
    ): IResponse<StartSeatResponseDto> {
        val userId = ticket.userId
        val seatId = body["seatId"] as Long? ?: return IResponse.error(msg = "seatId required.")

        val seat = seatService.getById(seatId) ?: return IResponse.error(msg = "启动失败（错误0）。")

        // 看看是否已经登录了。
        if (linuxService.isLoggedIn(seat)) {
            return IResponse.error(msg = "启动失败（错误1）。")
        }

        // 权限检查
        if (seat.userId != userId) {
            return IResponse.error(msg = "启动失败（错误2）。")
        }


        // 登录到 seat
        linuxService.loginToUser(seat)


        return IResponse.ok(StartSeatResponseDto(
            seat = seat.toHashMapWithKeysEvenNull(
                SeatEntity::id, SeatEntity::userId, SeatEntity::groupId,
                SeatEntity::nickname, SeatEntity::createTime, SeatEntity::lastLoginTime,
                SeatEntity::creator, SeatEntity::linuxLoginName, SeatEntity::linuxUid
            ),
        ))
    }


    data class StartVesperResponseDto(
        val vesperIP: String,
        val vesperPort: Int,
        val vncPassword: String
    )
    @Operation(summary = "启动 vesper。")
    @Parameters(
        Parameter(name = "seatId")
    )
    @PostMapping("launchVesper")
    fun startVesper(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, *>
    ): IResponse<StartVesperResponseDto> {
        val userId = ticket.userId
        // 参数非空检查。
        val seatId = body["seatId"] as Long? ?: return IResponse.error(msg = "seatId required.")
        val seat = seatService.getById(seatId) ?: return IResponse.error(msg = "错误1。")
        val execCmds = body["execCmds"] as String? ?: "2,7,7,konsoledolphin"

        // 权限检查。
        if (seat.userId != userId) {
            return IResponse.error(msg = "错误2。")
        }

        // 检查 vesper launcher 是否在运行。
        if (!vesperService.isVesperLauncherLive(seat)) {
            return IResponse.error(msg = "vesper launcher is not running!")
        }

        // 准备启动参数
        val vncPassword = UUID.randomUUID().toString().substring(0 until 6)
        val ip = "0.0.0.0"
        val portSocket = ServerSocket(0)
        val port = portSocket.localPort
        if (port == -1) {
            return IResponse.error(msg = "no available tcp port!")
        }

        // 准备 vesper 命令行
        val vesperCmdLine = StringBuilder("vesper")
        vesperCmdLine.append(" --headless")
            .append(" --add-virtual-display 1280*720") // todo
            .append(" --use-pixman-renderer")
            .append(" --exec-cmds $execCmds")
            .append(" --enable-vnc")
            .append(" --vnc-auth-passwd $vncPassword")
            .append(" --vnc-port $port")
            .append(" --libvncserver-passwd-file ${MacroDefines.Vesper.LIBVNCSERVER_PASSWD_FILE}")
            .append(" --enable-ctrl")
            .append(" --ctrl-domain-socket ${MacroDefines.Vesper.CONTROL_SOCK}")


        // 准备启动报文
        val request = VesperLauncherProtocols.ShellLaunch()
        request.cmd = vesperCmdLine.toString()


        // 向 vesper launcher 发送启动指令
        portSocket.close()
        val response = vesperService.send<VesperLauncherProtocols.Response>(request, vesperService.launcherSockPathOf(seat))
        response ?: return IResponse.error(msg = "failed to get response from vesper launcher.")
        if (response.code != 0) {
            return IResponse.error(msg = response.msg)
        }

        return IResponse.ok(StartVesperResponseDto(
            vesperIP = ip,
            vesperPort = port,
            vncPassword = vncPassword
        ))
    } // fun startVesper


    @Operation(summary = "检查某些主机的 vesper launcher 是否正在工作。")
    @Parameters(
        Parameter(name = "seatIds", description = "需要查询的主机id表。每个id以英文逗号分隔。")
    )
    @GetMapping("vesperLauncherLive")
    fun checkWhetherVesperLauncherLive(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam seatIds: String
    ): IResponse<HashMap<Long, Boolean>> {
        val result = HashMap<Long, Boolean>()
        seatIds.split(",").forEach {
            val seat = seatService.getById(it.toLong()) ?: return@forEach

            // todo: 暂时没考虑权限的问题。

            result[seat.id!!] = vesperService.isVesperLauncherLive(seat)
        }

        return IResponse.ok(result)
    }


    @Operation(summary = "检查某些主机的 vesper 是否正在工作。")
    @Parameters(
        Parameter(name = "seatIds", description = "需要查询的主机id表。每个id以英文逗号分隔。")
    )
    @GetMapping("vesperLive")
    fun checkWhetherVesperLive(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam seatIds: String
    ): IResponse<HashMap<Long, Boolean>> {
        val result = HashMap<Long, Boolean>()
        seatIds.split(",").forEach {
            val seat = seatService.getById(it.toLong()) ?: return@forEach

            // todo: 暂时没考虑权限的问题。

            result[seat.id!!] = vesperService.isVesperLive(seat)
        }

        return IResponse.ok(result)
    }


    @GetMapping("usersLoggedIn")
    fun getUsersLoggedIn(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
    ): IResponse<List<String>> {
        // todo: 暂未做权限校验。
        return IResponse.ok(linuxService.getLoggedInUsersAsList())
    }
}
