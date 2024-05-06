// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月17日 上海市嘉定区
 */


package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.kotlin.KtUpdateChainWrapper
import com.baomidou.mybatisplus.extension.kotlin.KtUpdateWrapper
import com.gardilily.vespercenter.common.MacroDefines
import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.GroupMemberEntity
import com.gardilily.vespercenter.entity.PermissionEntity.Permission
import com.gardilily.vespercenter.entity.GroupPermissionEntity.GroupPermission
import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.service.*
import com.gardilily.vespercenter.service.vesperprotocol.VesperControlProtocols
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
        Parameter(name = "skel", description = "样板间的 seat id", required = false),
        Parameter(name = "note", description = "为主机附上的描述")
    )
    @PostMapping("new")
    fun createSeats(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, *>
    ): IResponse<List<CreateSeatsResponseDto.Entry>> {
        // 参数校验
        val users = body["users"] as List<*>? ?: return IResponse.error(msg = "users required.")
        val group = (body["group"] as Int?)?.toLong() // nullable
        val note = body["note"] as String? // nullable
        val skelSeatId = body["skel"] as Long? // nullable

        val skelPath = if (skelSeatId != null) {

            val skelSeat = seatService.getById(skelSeatId)
            if (skelSeat != null) {
                "/home/${skelSeat.linuxLoginName}"
            } else
                null

        } else
            null

        // 权限检查
        if (group != null) {
            groupPermissionService.ensurePermission(ticket.userId, group, GroupPermission.CREATE_OR_DELETE_SEAT)
        } else {
            permissionService.ensurePermission(ticket.userId, Permission.CREATE_SEAT)
        }

        // 创建主机
        val successList = ArrayList<CreateSeatsResponseDto.Entry>()

        users.forEach {
            val uid = try { (it as Int).toLong() } catch (_: Exception) { return@forEach }

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
                seatEnabled = true,
                note = note,
                linuxUid = -1,
                linuxLoginName = "/",
                linuxPasswdRaw = "",
                createTime = Timestamp(System.currentTimeMillis())
            )
            seatService.baseMapper.insert(seatEntity)

            try {
                seatEntity.nickname = "VC Seat ${seatEntity.id}"
                seatEntity.linuxLoginName = "vesper_center_${seatEntity.id}"
                seatEntity.linuxPasswdRaw = UUID.randomUUID().toString().substring(0 until 16)
                seatEntity.linuxUid = linuxService.createUser(
                    seatEntity.linuxLoginName!!,
                    seatEntity.linuxPasswdRaw!!,
                    skeletonDirectory = skelPath,
                ) ?: throw Exception("linux service failed to create user.")

                seatService.updateById(seatEntity)
                successList.add(
                    CreateSeatsResponseDto.Entry(
                    userId = uid,
                    groupId = group,
                    seatInfo = seatEntity.toHashMapWithKeysEvenNull(
                        SeatEntity::id, SeatEntity::userId, SeatEntity::creator,
                        SeatEntity::seatEnabled, SeatEntity::nickname, SeatEntity::note,
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
        val seatId = body["seatId"]?.toString()?.toLong() ?: return IResponse.error(msg = "seatId required.")
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


    /**
     *
     * todo: 分页查询
     */
    @Operation(
        summary = "获取主机列表。",
        description = "传入 groupId 以启用群组模式。<br/>" +
                "非群组模式下，返回本人可用的主机及有权管理的主机。<br/>" +
                "根据是否传入 viewAllSeatsInGroup 决定是否查看全组主机。<br/>" +
                "若输入 alsoSeatsInNonGroupMode 值为 true，将同时返回群组模式和非群组模式下的结果。"
    )
    @GetMapping("seats")
    fun getSeats(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam groupId: Long?,
        @RequestParam alsoSeatsInNonGroupMode: Boolean?,
        @RequestParam viewAllSeatsInGroup: Boolean?
    ): IResponse<List<Any?>> {
        val userId = ticket.userId

        val res = HashMap<Long, Any?>()  // id -> entity

        fun addToRes(list: List<SeatEntity>) {
            list.forEach { it ->
                val id = it.id!!
                val entity = it

                if (res.contains(id)) {
                    return@forEach
                }

                val map = entity.toHashMapWithKeysEvenNull(
                    SeatEntity::id, SeatEntity::userId, SeatEntity::creator, SeatEntity::nickname,
                    SeatEntity::seatEnabled, SeatEntity::groupId, SeatEntity::note,
                    SeatEntity::linuxUid, SeatEntity::linuxLoginName, SeatEntity::createTime,
                    SeatEntity::lastLoginTime
                )

                map["username"] = userService.getById(entity.userId)?.username  // todo: 待优化
                res[id] = map

            }
        } // fun addToRes


        // 群组模式。
        if (groupId != null) {

            val isMemberQuery = KtQueryWrapper(GroupMemberEntity::class.java)
                .eq(GroupMemberEntity::userId, ticket.userId)
                .eq(GroupMemberEntity::groupId, groupId)

            if (!groupMemberService.exists(isMemberQuery)) {
                return IResponse.error(msg = "无权限")
            }

            if (viewAllSeatsInGroup != null && viewAllSeatsInGroup) {
                addToRes(
                    seatService.baseMapper.selectList(
                        KtQueryWrapper(SeatEntity::class.java)
                            .eq(SeatEntity::groupId, groupId)
                    )
                )

            } else {
                // 仅查看自己可以管理的。

                if (groupPermissionService.checkPermission(ticket.userId, groupId, GroupPermission.LOGIN_TO_ANY_SEAT)) {
                    addToRes(
                        seatService.baseMapper.selectList(
                            KtQueryWrapper(SeatEntity::class.java)
                                .eq(SeatEntity::groupId, groupId)
                        )
                    )
                } else {
                    addToRes(
                        seatService.baseMapper.selectList(
                            KtQueryWrapper(SeatEntity::class.java)
                                .eq(SeatEntity::groupId, groupId)
                                .eq(SeatEntity::userId, ticket.userId)
                        )
                    )
                }

            }

            if (alsoSeatsInNonGroupMode == null || !alsoSeatsInNonGroupMode) {
                return IResponse.ok(res.map { it.value })
            }
        } // if (groupId != null)


        // 无群组模式

        // 我的 seat
        val mySeats = seatService.baseMapper.selectList(
            KtQueryWrapper(SeatEntity::class.java)
                .eq(SeatEntity::userId, userId)
        )
        addToRes(mySeats)

        // 我管理的全局 seat
        if (permissionService.checkPermission(userId, Permission.CREATE_SEAT)) {
            val myManageSeats = seatService.baseMapper.selectList(
                KtQueryWrapper(SeatEntity::class.java)
                    .eq(SeatEntity::creator, userId)
            )
            addToRes(myManageSeats)
        }

        // 如果我有管理全部 seat 的权限...
        if (permissionService.checkPermission(userId, Permission.DELETE_ANY_SEAT)
            || permissionService.checkPermission(userId, Permission.NAME_ANY_SEAT)) {

            addToRes(seatService.baseMapper.selectList(KtQueryWrapper(SeatEntity::class.java)))
        }

        return IResponse.ok(res.map { it.value!! })
    }


    // delete seats

    data class DeleteSeatRequestDto(
        val seatIds: List<Long>
    )

    data class DeleteSeatResponseDtoEntry(
        val seatId: Long,
        val success: Boolean,
        val msg: String,
    )

    @PostMapping("delete")
    fun deleteSeats(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: DeleteSeatRequestDto
    ): IResponse<List<DeleteSeatResponseDtoEntry>> {
        val res = HashMap<Long, DeleteSeatResponseDtoEntry>()

        for (seatId in body.seatIds) {
            if (res.contains(seatId)) {
                continue
            }

            val seat = seatService.getById(seatId)
            if (seat == null) {
                res[seatId] = DeleteSeatResponseDtoEntry(
                    seatId = seatId, success = false, msg = "没有这个 seat"
                )
                continue
            }

            // permission check

            var permissionDeniedReason: String? = null

            if (permissionService.checkPermission(ticket, Permission.DELETE_ANY_SEAT)) {
                // passed
            } else if (seat.creator == ticket.userId && permissionService.checkPermission(ticket, Permission.CREATE_SEAT)) {
                // passed
            } else { // todo: 组内删除权限。
                permissionDeniedReason = "权限不足"
            }


            if (permissionDeniedReason != null) {
                res[seatId] = DeleteSeatResponseDtoEntry(
                    seatId = seatId, success = false, msg = permissionDeniedReason
                )
                continue
            }

            // now, permission check passed.

            seatService.removeSeat(seat)
            res[seatId] = DeleteSeatResponseDtoEntry(
                seatId = seatId, success = true, msg = "删除成功"
            )
        }

        return IResponse.ok(res.map { it.value })
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
        val seatId = (body["seatId"] as Int?)?.toLong() ?: return IResponse.error(msg = "seatId required.")

        val seat = seatService.getById(seatId) ?: return IResponse.error(msg = "启动失败（错误0）。")

        // 看看是否已经登录了。
        if (linuxService.isLoggedIn(seat)) {
            return IResponse.error(msg = "启动失败（错误1）。")
        }

        // 权限检查
        if (!seatService.canLogin(userId, seat)) {
            return IResponse.error(msg = "启动失败（错误2）。")
        }


        // 登录到 seat
        linuxService.loginToUser(seat)

        seat.lastLoginTime = Timestamp(System.currentTimeMillis())
        seatService.updateById(seat)

        linuxService.unlockTmpfsAccess(seat)

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
    data class LaunchVesperRequestDto(
        val seatId: Long?,
        val displayWidth: Long = 1440,
        val displayHeight: Long = 900
    )
    @Operation(summary = "启动 vesper。")
    @Parameters(
        Parameter(name = "seatId")
    )
    @PostMapping("launchVesper")
    fun startVesper(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: LaunchVesperRequestDto
    ): IResponse<StartVesperResponseDto> {
        val userId = ticket.userId
        // 参数非空检查。
        val seatId = body.seatId ?: return IResponse.error(msg = "seatId required.")
        val seat = seatService.getById(seatId) ?: return IResponse.error(msg = "错误1。")

        if (body.displayWidth < 500 || body.displayWidth > 2560 || body.displayHeight < 300 || body.displayHeight > 1600) {
            return IResponse.error(msg = "屏幕尺寸太奇怪了吧？")
        }

        val execApps = listOf("konsole", "dolphin")
        val execCmdsBuilder = StringBuilder()
        execApps.forEach {
            if (execCmdsBuilder.isNotBlank()) {
                execCmdsBuilder.append(", ")
            }
            execCmdsBuilder.append(it)
        }
        val execCmds = execCmdsBuilder.toString()

        // 权限检查。
        if (!seatService.canLogin(userId, seat)) {
            return IResponse.error(msg = "拒绝登录。")
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
        vesperCmdLine.append(" --no-color")
            .append(" --log-to /home/${seat.linuxLoginName}/vesper-core.log")
            .append(" --headless")
            .append(" --add-virtual-display ${body.displayWidth}*${body.displayHeight}")
            .append(" --use-pixman-renderer")
            .append(" --exec-cmds \"$execCmds\"")
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
            return IResponse.error(msg = response.msgString)
        }

        return IResponse.ok(StartVesperResponseDto(
            vesperIP = ip,
            vesperPort = port,
            vncPassword = vncPassword
        ))
    } // fun startVesper


    @GetMapping("vncConnectionInfo")
    fun getVncConnectionInfo(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam seatId: Long
    ): IResponse<StartVesperResponseDto> {
        val seat = seatService.getById(seatId)
        if (seat.userId == ticket.userId) {
            // passed
        } else if (seat.creator == ticket.userId) {
            // passed
        } else if (permissionService.checkPermission(ticket.userId, Permission.LOGIN_TO_ANY_SEAT)) {
            // passed
        } else if (seat.groupId != null && groupPermissionService.checkPermission(ticket.userId, seat.groupId!!, GroupPermission.LOGIN_TO_ANY_SEAT)) {
            // passed
        } else {
            return IResponse.error(msg = "权限不足")
        }


        if (!vesperService.isVesperLive(seat)) {
            return IResponse.error(msg = "vesper control 未在运行")
        }

        val portRes = vesperService.sendToVesper(VesperControlProtocols.GetVNCPort(), seat) ?: return IResponse.error(msg = "未知错误：portRes")
        val passwordRes = vesperService.sendToVesper(VesperControlProtocols.GetVNCPassword(), seat) ?: return IResponse.error(msg = "未知错误：passwordRes")

        if (portRes.code != 0 || passwordRes.code != 0) {
            return IResponse.error(msg = "未知错误：some of the codes are not 0")
        }

        return IResponse.ok(StartVesperResponseDto(
            vesperIP = "0.0.0.0",
            vesperPort = portRes.msgString.toInt(),
            vncPassword = passwordRes.msgString
        ))
    }


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


    data class LoginStatusResponse(
        val linux: Boolean,
        val vesper: Boolean,
        val vesperLauncher: Boolean
    )
    @GetMapping("loginStatus")
    fun getLoginStatus(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam seatId: Long
    ): IResponse<LoginStatusResponse> {
        val seat = seatService.getById(seatId) ?: return IResponse.error()

        return IResponse.ok(
            LoginStatusResponse(
                linux = linuxService.isLoggedIn(seat),
                vesper = vesperService.isVesperLive(seat),
                vesperLauncher = vesperService.isVesperLauncherLive(seat)
            )
        )
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


    @GetMapping("allUsersLoggedIn")
    fun getAllUsersLoggedIn(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
    ): IResponse<List<String>> {
        // todo: 暂未做权限校验。
        return IResponse.ok(linuxService.getLoggedInUsersAsList())
    }


    @GetMapping("userLoggedIn")
    fun checkUserLoggedIn(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam seatId: Long
    ): IResponse<Boolean> {
        val seat = seatService.getById(seatId) ?: return IResponse.error()

        // todo: permission check

        return IResponse.ok(linuxService.isLoggedIn(seat))
    }


    data class ShutdownRequest(
        val seatId: Long?
    )
    @PostMapping("shutdown")
    @Operation(summary = "关闭一个桌面环境。")
    fun shutdown(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: ShutdownRequest
    ): IResponse<Unit> {
        val seatId = body.seatId ?: return IResponse.error()

        val seat = seatService.getById(seatId) ?: return IResponse.error()

        if (seat.userId != ticket.userId) {
            return IResponse.error()
        }

        linuxService.forceLogout(seat)

        return IResponse.ok()
    }


    @Operation(summary = "获取某个特定主机的信息")
    @GetMapping("detail")
    fun detail(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam seatId: Long
    ): IResponse<HashMap<String, Any?>> {
        val seat = seatService.getById(seatId) ?: return IResponse.error()

        if (seat.userId == ticket.userId || seat.creator == ticket.userId) {
            // passed
        } else if (permissionService.checkPermission(ticket.userId, Permission.LOGIN_TO_ANY_SEAT)) {
            // passed
        } else if (seat.groupId != null) {
            groupPermissionService.ensurePermission(ticket.userId, seat.groupId!!, GroupPermission.LOGIN_TO_ANY_SEAT)
        } else {
            return IResponse.error()
        }

        val res = seat.toHashMapWithKeysEvenNull(
            SeatEntity::id, SeatEntity::userId, SeatEntity::groupId,
            SeatEntity::creator, SeatEntity::seatEnabled, SeatEntity::nickname,
            SeatEntity::note, SeatEntity::linuxUid,
            SeatEntity::linuxLoginName, SeatEntity::createTime, SeatEntity::lastLoginTime
        )

        if (seat.groupId != null) {
            res["groupName"] = userGroupService.getById(seat.groupId).groupName
        }

        return IResponse.ok(res)
    }


    data class SetEnabledRequestBody(
        val seatId: Long? = null,
        val groupId: Long? = null,
        val enabled: Boolean?,
        var alsoQuit: Boolean?
    )
    @PostMapping("enable")
    @Operation(summary = "编辑一台/一组主机的启用状态。seatId和groupId二选一")
    @Parameters(
        Parameter(name = "groupId", description = "调整整组主机启用状态"),
        Parameter(name = "seatId", description = "调整单个主机启用状态")
    )
    fun setEnabled(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: SetEnabledRequestBody
    ): IResponse<Unit> {

        // check request body

        if (body.enabled == null) {
            return IResponse.error(msg = "`enabled` should be passed!")
        } else if (body.seatId != null && body.groupId != null) {
            return IResponse.error(msg = "don't pass groupId and seatId at the same time!")
        } else if (body.seatId == null && body.groupId == null) {
            return IResponse.error(msg = "at least one of groupId and seatId should be passed!")
        }

        if (body.alsoQuit == null) {
            body.alsoQuit = false
        }

        val groupMode = body.groupId != null

        // check permission

        if (permissionService.checkPermission(ticket, Permission.DISABLE_OR_ENABLE_ANY_SEAT)) {
            // passed
        } else if (groupMode && groupPermissionService.checkPermission(ticket, body.groupId!!, GroupPermission.DISABLE_OR_ENABLE_ANY_SEAT)) {
            // passed
        } else {
            return IResponse.error(msg = "permission denied.")
        }

        // do the job

        if (groupMode) {
            val updateWrapper = KtUpdateWrapper(SeatEntity::class.java)
                .eq(SeatEntity::groupId, body.groupId)
                .set(SeatEntity::seatEnabled, body.enabled)
            seatService.baseMapper.update(updateWrapper)

            if (body.alsoQuit!!) {
                val select = KtQueryWrapper(SeatEntity::class.java).eq(SeatEntity::groupId, body.groupId)
                seatService.baseMapper.selectList(select).forEach {
                    linuxService.forceLogout(it)
                }
            }

        } else {
            val seat = seatService.getById(body.seatId) ?: return IResponse.error(msg = "没有这个 seat")
            seat.seatEnabled = body.enabled
            seatService.updateById(seat)

            if (body.alsoQuit!!) {
                linuxService.forceLogout(seat)
            }
        }

        return IResponse.ok()
    }

}
