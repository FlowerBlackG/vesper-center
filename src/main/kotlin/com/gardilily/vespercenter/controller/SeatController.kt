// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月17日 上海市嘉定区
 */


package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.baomidou.mybatisplus.extension.kotlin.KtUpdateChainWrapper
import com.baomidou.mybatisplus.extension.kotlin.KtUpdateWrapper
import com.baomidou.mybatisplus.extension.plugins.pagination.Page
import com.gardilily.vespercenter.common.MacroDefines
import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.dto.PagedResult
import com.gardilily.vespercenter.entity.GroupMemberEntity
import com.gardilily.vespercenter.entity.PermissionEntity.Permission
import com.gardilily.vespercenter.entity.GroupPermissionEntity.GroupPermission
import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.service.*
import com.gardilily.vespercenter.service.vesperprotocol.VesperControlProtocols
import com.gardilily.vespercenter.service.vesperprotocol.VesperLauncherProtocols
import com.gardilily.vespercenter.utils.Slf4k
import com.gardilily.vespercenter.utils.toHashMapWithKeysEvenNull
import com.gardilily.vespercenter.utils.toHashMapWithNulls
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.sql.Timestamp
import java.util.UUID
import kotlin.io.path.Path

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
            val uniqueKey: Long,
            val success: Boolean,
            val msg: String,
            val seatInfo: HashMap<String, Any?>? = null // based on SeatEntity
        )
    }


    class CreateSeatsRequestDto private constructor() {
        data class Entry(
            val uniqueKey: Long,
            val group: Long? = null,
            /** or you can use userid. */
            val username: String? = null,
            /** or you can use username. */
            val userid: Long? = null,
            val skel: Long? = null,
            val note: String? = null
        )
    }


    @Operation(summary = "新建桌面环境。支持批量创建")
    @PostMapping("new")
    fun createSeats(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: List<CreateSeatsRequestDto.Entry>
    ): IResponse<List<CreateSeatsResponseDto.Entry>> {
        // 参数校验
        body.forEach {
            if (it.username.isNullOrBlank() && it.userid == null) {
                return IResponse.error(msg = "非法访问。错误码：f084a8c2-5b00-4ab4-8134-0f1621188b45")
            }
        }

        val skelPathCache = HashMap<Long, String?>()  // skel seat id -> skel seat path
        fun skelPathOf(skelId: Long?): String? {
            if (skelId == null)
                return null

            if (skelPathCache.contains(skelId))
                return skelPathCache[skelId]

            val skelSeat = seatService.getById(skelId)
            val path = if (skelSeat != null) "/home/${skelSeat.linuxLoginName}" else null
            skelPathCache[skelId] = path

            return path
        }


        val result = ArrayList<CreateSeatsResponseDto.Entry>()
        
        for (it in body) {
            // 权限检查

            val permissionCheckPassed = if (it.group != null) {
                groupPermissionService.checkPermission(ticket.userId, it.group, GroupPermission.CREATE_OR_DELETE_SEAT)
            } else {
                permissionService.checkPermission(ticket.userId, Permission.CREATE_SEAT)
            }

            if (!permissionCheckPassed) {
                result.add(CreateSeatsResponseDto.Entry(
                    uniqueKey = it.uniqueKey,
                    success = false,
                    msg = "你的权限不够。"
                ))
                continue
            }

            // 创建主机
            val uid = if (it.userid != null)
                it.userid
            else {
                val query = KtQueryWrapper(UserEntity::class.java)
                    .eq(UserEntity::username, it.username)
                userService.baseMapper.selectOne(query)?.id
            }

            if (uid == null) {
                result.add(CreateSeatsResponseDto.Entry(
                    uniqueKey = it.uniqueKey,
                    success = false,
                    msg = "找不到这个用户。"
                ))
                continue
            }

            // 检查用户在不在组里面。
            if (it.group != null) {
                val existsQuery = KtQueryWrapper(GroupMemberEntity::class.java)
                    .eq(GroupMemberEntity::groupId, it.group)
                    .eq(GroupMemberEntity::userId, uid)
                if (!groupMemberService.exists(existsQuery)) {
                    result.add(CreateSeatsResponseDto.Entry(
                        uniqueKey = it.uniqueKey,
                        success = false,
                        msg = "用户不在该组。"
                    ))
                    continue
                }
            }

            // 新建主机。
            val seatEntity = SeatEntity(
                userId = uid,
                groupId = it.group,
                creator = ticket.userId,
                seatEnabled = true,
                note = it.note,
                linuxUid = -1,
                linuxLoginName = "/",
                linuxPasswdRaw = "",
                createTime = Timestamp(System.currentTimeMillis())
            )
            seatService.baseMapper.insert(seatEntity)

            try {
                seatEntity.nickname = "VC Seat ${seatEntity.id}"
                seatEntity.linuxLoginName = "vesper_center_${seatEntity.id}"
                seatEntity.linuxPasswdRaw = UUID.randomUUID().toString().substring(0 until 16).replace("-", "")
                seatEntity.linuxUid = linuxService.createUser(
                    seatEntity.linuxLoginName!!,
                    seatEntity.linuxPasswdRaw!!,
                    skeletonDirectory = skelPathOf(it.skel),
                ) ?: throw Exception("linux service failed to create user.")

                seatService.updateById(seatEntity)
                result.add(CreateSeatsResponseDto.Entry(
                    uniqueKey = it.uniqueKey,
                    success = true,
                    msg = "创建成功。",
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
                result.add(CreateSeatsResponseDto.Entry(
                    uniqueKey = it.uniqueKey,
                    success = false,
                    msg = "创建失败。未知错误。错误码：4908933c-0dd9-4d60-a496-04c6d67cca44"
                ))
            }

        }


        return IResponse.ok(result)
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
        @RequestParam viewAllSeatsInGroup: Boolean?,
        @RequestParam pageNo: Long = 1,
        @RequestParam pageSize: Long = 20,
        @RequestParam search: String = ""
    ): IResponse< PagedResult<*> > {
        val userId = ticket.userId
        val searchKeywords = search.split(" ").filter { it.isNotBlank() }

        val query = KtQueryWrapper(SeatEntity::class.java).select(
            SeatEntity::id,
            SeatEntity::userId,
            SeatEntity::creator,
            SeatEntity::nickname,
            SeatEntity::seatEnabled,
            SeatEntity::groupId,
            SeatEntity::note,
            SeatEntity::linuxUid,
            SeatEntity::linuxLoginName,
            SeatEntity::createTime,
            SeatEntity::lastLoginTime,
            SeatEntity::linuxPasswdRaw
        )


        // 群组模式。
        if (groupId != null) {

            val isMemberQuery = KtQueryWrapper(GroupMemberEntity::class.java)
                .eq(GroupMemberEntity::userId, ticket.userId)
                .eq(GroupMemberEntity::groupId, groupId)

            if (!groupMemberService.exists(isMemberQuery)) {
                return IResponse.error(msg = "无权限")
            }

            if (viewAllSeatsInGroup != null && viewAllSeatsInGroup) {

                query.eq(SeatEntity::groupId, groupId)

            } else {
                // 仅查看自己可以管理的。

                if (groupPermissionService.checkPermission(ticket.userId, groupId, GroupPermission.LOGIN_TO_ANY_SEAT)) {

                    query.eq(SeatEntity::groupId, groupId)
                } else {

                    query.eq(SeatEntity::groupId, groupId)
                        .eq(SeatEntity::userId, ticket.userId)
                }

            }

        } // if (groupId != null)



        if (groupId == null || alsoSeatsInNonGroupMode == true) {
            // 无群组模式

            // 如果我有管理全部 seat 的权限...
            val iCanViewAllSeats = permissionService.checkPermission(userId, Permission.DELETE_ANY_SEAT)
                    || permissionService.checkPermission(userId, Permission.NAME_ANY_SEAT)
                    || permissionService.checkPermission(userId, Permission.LOGIN_TO_ANY_SEAT)

            if (iCanViewAllSeats) {
                if (groupId == null) {
                    // without modifying query, we can see all things.
                } else {
                    query.or {
                        it.ne(SeatEntity::id, -1)  // means select all
                    }
                }
            } else {

                // 我的 seat
                if (groupId == null)
                    query.eq(SeatEntity::userId, userId)
                else
                    query.or { it.eq(SeatEntity::userId, userId) }

                // 我管理的全局 seat
                if (permissionService.checkPermission(userId, Permission.CREATE_SEAT)) {

                    query.or {
                        it.eq(SeatEntity::creator, userId)
                    }

                }
            }
        }


        if (searchKeywords.isNotEmpty()) {

            searchKeywords.forEachIndexed { idx, str ->
                query.like(SeatEntity::nickname, str)
            }

        }


        val pageParams = Page<SeatEntity>(pageNo, pageSize)
        val dataPage = seatService.baseMapper.selectPage(pageParams, query)

        val list = dataPage.records.map {
            val map = it.toHashMapWithNulls()
            map["username"] = userService.getById(it.userId)?.username
            map["groupname"] = if (it.groupId == null)
                "无群组"
            else {
                userGroupService.getById(it.groupId).groupName
            }

            map
        }


        val pagedResult = PagedResult(list, pageNo = dataPage.current, pageSize = dataPage.size, total = dataPage.total)
        return IResponse.ok(pagedResult)
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
            return IResponse.error(msg = "主机已经启动过了。")
        }

        // 权限检查
        if (!seatService.canLogin(userId, seat)) {
            return IResponse.error(msg = "不允许登录。可能是主机被管理员禁用了，或者你没有登录权限。")
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
            return IResponse.error(msg = "不允许登录。可能是主机被管理员禁用了，或者你没有登录权限。")
        }

        // 检查 vesper launcher 是否在运行。
        if (!vesperService.isVesperLauncherLive(seat)) {
            return IResponse.error(msg = "vesper launcher 不在运行。")
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
        val vesperCmdLine = StringBuilder()
        vesperCmdLine.append("VESPER_VNC_AUTH_PASSWD=$vncPassword")
        vesperCmdLine.append(" vesper")
        vesperCmdLine.append(" --no-color")
            .append(" --log-to /home/${seat.linuxLoginName}/vesper-core.log")
            .append(" --headless")
            .append(" --add-virtual-display ${body.displayWidth}*${body.displayHeight}")
            .append(" --exec-cmds \"$execCmds\"")
            .append(" --enable-vnc")
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


        // check permission

        if (seat.userId == ticket.userId) {
            // permission check passed
        }
        else if (permissionService.checkPermission(ticket, Permission.DISABLE_OR_ENABLE_ANY_SEAT)) {
            // passed
        }
        else if (
            seat.groupId != null
            && groupPermissionService.checkPermission(ticket, seat.groupId!!, GroupPermission.DISABLE_OR_ENABLE_ANY_SEAT)
        ) {
            // passed
        }
        else {
            return IResponse.error(msg = "无权限。")
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
            SeatEntity::note, SeatEntity::linuxUid, SeatEntity::linuxPasswdRaw,
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

            val seatListQuery = KtQueryWrapper(SeatEntity::class.java)
                .eq(SeatEntity::groupId, body.groupId)
            val seatList = seatService.list(seatListQuery)

            if (body.enabled) {
                seatService.enable(seatList)
            } else {
                seatService.disable(seatList, alsoQuit = body.alsoQuit!!)
            }

        } else {
            if (body.enabled) {
                seatService.enable(body.seatId!!)
            } else {
                seatService.disable(body.seatId!!, alsoQuit = body.alsoQuit!!)
            }
        }

        return IResponse.ok()
    }


    data class AddSSHKeyRequest(
        val keys: List<String> = emptyList(),
        val seatId: Long? = null
    )

    @PostMapping("sshKey")
    fun addSSHKey(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: AddSSHKeyRequest
    ): IResponse<Int> {

        if (body.seatId == null)
            return IResponse.error(msg = "需要seatId。")

        val keys = body.keys.filter { it.isNotBlank() }.toMutableSet()
        keys.forEach { key ->
            key.forEach { ch ->
                if (ch.code !in 32 .. 126) {
                    return IResponse.error(msg = "检测到非法字符。")
                }
            }

            if (!key.startsWith("ssh-rsa ") && !key.startsWith("ssh-ed25519 ")) {
                return IResponse.error(msg = "存在不支持的公钥类型。只支持rsa和ed25519。")
            }
        }

        val seat = seatService.getById(body.seatId) ?: return IResponse.error(msg = "没有这个seat。")


        // check permission

        if (seat.userId != ticket.userId) {
            return IResponse.error(msg = "不是你的seat.")
        }

        if (seat.isDisabled()) {
            return IResponse.error(msg = "seat被禁用。")
        }


        // create .ssh folder and authorized_keys file

        val homeFolder = "/home/${seat.linuxLoginName}"
        val sshFolder = "$homeFolder/.ssh"
        val authorizedKeyFile = "$sshFolder/authorized_keys"

        linuxService.unlockFileAccess(homeFolder)


        // check file exists
        if (Files.exists(Path(sshFolder))) {
            if (!Files.isDirectory(Path(sshFolder))) {
                return IResponse.error(msg = "~/.ssh不是文件夹！")
            }
        } else {  // .ssh not created
            try {
                Files.createDirectory(Path(sshFolder))
            } catch (_: Exception) {
                return IResponse.error(msg = "无法创建.ssh文件夹。")
            }
        }

        linuxService.unlockFileAccess(sshFolder)

        if (Files.exists(Path(authorizedKeyFile))) {
            if (!Files.isRegularFile(Path(authorizedKeyFile))) {
                return IResponse.error(msg = "$authorizedKeyFile 不是标准文件！")
            }
        } else { // authorized_keys file not created
            try {
                Files.createFile(Path(authorizedKeyFile))
            } catch (_: Exception) {
                return IResponse.error(msg = "无法创建 authorized_keys 文件。")
            }
        }

        linuxService.unlockFileAccess(authorizedKeyFile)

        // remove duplicates

        File(authorizedKeyFile).forEachLine { line ->
            keys.remove(line)
        }


        // build append keys' content

        val appendContent = StringBuilder()
        keys.forEach { key ->
            appendContent.appendLine(key)
        }

        val file = File(authorizedKeyFile)
        file.appendText("\n")
        file.appendText(appendContent.toString())

        linuxService.fixSSHPermission(seat)

        return IResponse.ok(keys.size)
    }


    data class FixSSHPermissionRequest(
        val seatId: Long? = null
    )

    @PostMapping("fixSSHPermission")
    fun fixSSHPermission(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: FixSSHPermissionRequest
    ): IResponse<Unit> {
        if (body.seatId == null)
            return IResponse.error()

        val seat = seatService.getById(body.seatId) ?: return IResponse.error(msg = "没有这个seat.")

        if (seat.userId != ticket.userId)
            return IResponse.error(msg = "不是你的seat.")

        linuxService.fixSSHPermission(seat)
        return IResponse.ok()
    }

    data class ChangeGroupRequest(
        val seatId: Long? = null,
        val toGroup: Long? = null
    )
    @Transactional
    @PostMapping("changeGroup")
    fun changeGroup(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: ChangeGroupRequest
    ): IResponse<Unit> {
        if (body.seatId == null)
            return IResponse.error(msg = "参数不全。")


        val seat = seatService.getById(body.seatId) ?: return IResponse.error(msg = "没有这个seat。")


        // ensure seat's owner is in target group
        if (body.toGroup != null) {
            val inGroupQuery = KtQueryWrapper(GroupMemberEntity::class.java)
                .eq(GroupMemberEntity::userId, seat.userId!!)
                .eq(GroupMemberEntity::groupId, body.toGroup)
            if (groupMemberService.count(inGroupQuery) <= 0)
                return IResponse.error(msg = "机主用户不在目标组内。")
        }


        // check permission
        // you should be able to:
        //   1. remove seat from original group
        //   2. add seat to target group

        // now, check permission '1'
        if (permissionService.checkPermission(ticket, Permission.DELETE_ANY_SEAT)) {
            // passed
        } else if (groupPermissionService.checkPermission(ticket, seat.groupId, GroupPermission.CREATE_OR_DELETE_SEAT)) {
            // passed
        } else {
            return IResponse.error(msg = "没有将seat从原组删除的权限。")
        }

        // now, check permission '2'
        if (permissionService.checkPermission(ticket, Permission.CREATE_SEAT)) {
            // passed
        } else if (groupPermissionService.checkPermission(ticket, body.toGroup, GroupPermission.CREATE_OR_DELETE_SEAT)) {
            // passed
        } else {
            return IResponse.error(msg = "没有在目标组创建seat的权限。")
        }


        // now, do the move.

        seatService.changeGroup(seat, body.toGroup)


        return IResponse.ok()
    }

}
