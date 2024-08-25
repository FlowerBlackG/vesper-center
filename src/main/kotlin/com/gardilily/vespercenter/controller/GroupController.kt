// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */


package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.*
import com.gardilily.vespercenter.entity.GroupPermissionEntity.GroupPermission
import com.gardilily.vespercenter.entity.PermissionEntity.Permission
import com.gardilily.vespercenter.mapper.GroupMemberMapper
import com.gardilily.vespercenter.mapper.GroupPermissionGrantMapper
import com.gardilily.vespercenter.mapper.UserGroupMapper
import com.gardilily.vespercenter.service.*
import com.gardilily.vespercenter.utils.toHashMapWithKeysEvenNull
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp

@RestController
@RequestMapping("group")
class GroupController @Autowired constructor(
    val userGroupService: UserGroupService,
    val userGroupMapper: UserGroupMapper,
    val groupMemberService: GroupMemberService,
    val groupMemberMapper: GroupMemberMapper,
    val userService: UserService,
    val permissionService: PermissionService,
    val groupPermissionService: GroupPermissionService,
    val groupPermissionGrantMapper: GroupPermissionGrantMapper,
    val seatService: SeatService,
    val linuxService: LinuxService,
    val userEntityService: UserEntityService,
) {

    data class CreateGroupRequestDto(
        val name: String?,
        val note: String? = null
    )

    /**
     * 创建一个新的用户组。
     *
     * @return Long: id of the created group.
     */
    @Operation(summary = "创建新用户组")
    @PostMapping("create")
    fun createGroup(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: CreateGroupRequestDto
    ): IResponse<Long> {
        val userId = ticket.userId

        // 参数校验
        if (body.name.isNullOrBlank()) {
            return IResponse.error(msg = "请填写群组名称。")
        }

        // 权限校验
        permissionService.ensurePermission(userId, Permission.CREATE_GROUP)

        val group = UserGroupEntity(
            groupName = body.name,
            note = body.note,
            createTime = Timestamp(System.currentTimeMillis())
        )

        if (userGroupMapper.insert(group) != 1) {
            return IResponse.error(msg = "创建失败。(insert res not 1)")
        }

        // 将自己加入群组

        groupMemberService.addUserToGroup(userId, group.id!!)

        // 赋权

        GroupPermission.allPermissions().forEach {
            groupPermissionGrantMapper.insert(GroupPermissionGrantEntity(
                userId = userId,
                groupId = group.id!!,
                permissionId = it
            ))
        }

        return IResponse.ok(group.id)
    }


    @GetMapping("allPermissions")
    fun getAllPermissions(): IResponse<List<GroupPermissionEntity>> {
        val query = KtQueryWrapper(GroupPermissionEntity::class.java)
        val result = groupPermissionService.baseMapper.selectList(query)
        return IResponse.ok(result)
    }


    data class GrantPermissionRequestDto(
        var permission: GroupPermission? = null,
        var grant: Boolean? = null,
        var targetUserId: Long? = null,
        var groupId: Long? = null
    )
    @PostMapping("grantPermission")
    fun grantPermission(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: GrantPermissionRequestDto
    ): IResponse<Unit> {
        val userId = ticket.userId

        // 传参非空校验
        if (body.permission == null || body.grant == null || body.targetUserId == null || body.groupId == null) {
            return IResponse.error(msg = "请求体不完整。", code = HttpStatus.BAD_REQUEST)
        }

        // 操作权限校验。
        when (body.permission) {
            // 本接口拒绝受理超级权限赋权请求！
            GroupPermission.GRANT_PERMISSION -> return IResponse.error(msg = "不允许改动超级权限。")

            // 如果要赋予其他用户权限，自己必须拥有“赋权”权限。
            else -> groupPermissionService.ensurePermission(userId, body.groupId!!, GroupPermission.GRANT_PERMISSION)
        }

        // 检查待赋权用户是否存在。
        userService.getById(body.targetUserId) ?: return IResponse.error(msg = "没有这个用户。", code = HttpStatus.BAD_REQUEST)

        // 先要知道这个权限是否已经存在。
        val permissionIsGranted = groupPermissionService.checkPermission(body.targetUserId!!, body.groupId!!, body.permission!!)

        return if (permissionIsGranted == body.grant) {
            IResponse.ok(msg = "无需改动。")
        } else if (permissionIsGranted) {
            // 取消权限

            groupPermissionGrantMapper.delete(
                KtQueryWrapper(GroupPermissionGrantEntity::class.java)
                    .eq(GroupPermissionGrantEntity::permissionId, body.permission!!)
                    .eq(GroupPermissionGrantEntity::groupId, body.groupId!!)
                    .eq(GroupPermissionGrantEntity::userId, body.targetUserId!!)
            )

            IResponse.ok(msg = "取消权限。")
        } else {
            // 赋权

            groupPermissionGrantMapper.insert(
                GroupPermissionGrantEntity(
                    userId = body.targetUserId!!,
                    permissionId = body.permission!!,
                    groupId = body.groupId!!
                )
            )

            IResponse.ok(msg = "赋权。")
        }

    }


    /**
     * 删除一个群组。
     * 会同步删除绑定到该群组的所有主机。
     */
    @Operation(summary = "删除一个组。移除其中的用户，并删除组内主机")
    @Parameters(
        Parameter(name = "groupId")
    )
    @PostMapping("remove")
    fun removeGroup(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, Any>
    ): IResponse<Unit> {
        val userId = ticket.userId

        val rawGid = body["groupId"] as Int? ?: return IResponse.error(msg = "groupId required.")
        val gid = rawGid.toLong()
        groupPermissionService.ensurePermission(userId, gid, GroupPermission.DROP_GROUP)

        // 确认组存在
        userGroupService.getById(gid) ?: return IResponse.error(msg = "没有这个组。")

        // 取消所有赋权
        groupPermissionGrantMapper.delete(
            KtQueryWrapper(GroupPermissionGrantEntity::class.java)
                .eq(GroupPermissionGrantEntity::groupId, gid)
        )

        // 移除所有组员
        groupMemberService.remove(
            KtQueryWrapper(GroupMemberEntity::class.java)
                .eq(GroupMemberEntity::groupId, gid)
        )

        // 移除所有主机
        val seatQuery = KtQueryWrapper(SeatEntity::class.java)
            .eq(SeatEntity::groupId, gid)
        val seats = seatService.baseMapper.selectList(seatQuery)
        seats.forEach {
            linuxService.removeUser(it)
        }
        seatService.remove(seatQuery)

        // 移除这个组
        userGroupMapper.deleteById(gid)

        return IResponse.ok()
    }


    data class AddUsersRequestBody(
        val groupId: Long,
        val usernames: List<String>?,
        val userIds: List<Long>?,
    )
    data class AddUsersResultEntry(
        val userId: Long? = null,
        val username: String? = null,
        val success: Boolean,
        val msg: String
    )
    @Operation(summary = "将用户添加到群组")
    @PostMapping("addUsers")
    fun addUsers(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: AddUsersRequestBody
    ): IResponse<List<AddUsersResultEntry>> {
        val groupId = body.groupId
        userGroupService.getById(groupId) ?: return IResponse.error(msg = "没有这个组。")
        groupPermissionService.ensurePermission(ticket.userId, groupId, GroupPermission.ADD_OR_REMOVE_USER)

        val res = ArrayList<AddUsersResultEntry>()
        val users = if (body.userIds != null) {
            body.userIds.mapNotNull {
                val user = userService.getById(it)
                if (user == null) {
                    res.add(AddUsersResultEntry(userId = it, success = false, msg = "没有这个用户"))
                    null
                } else {
                    user
                }
            }
        } else if (body.usernames != null) {
            body.usernames.mapNotNull { username ->
                val user = userService.getOne(KtQueryWrapper(UserEntity::class.java).eq(UserEntity::username, username))
                if (user == null) {
                    res.add(AddUsersResultEntry(username = username, success = false, msg = "没有这个用户"))
                    null
                } else {
                    user
                }
            }
        } else {
            return IResponse.error(msg = "usernames 和 userIds 必选其一。错误定位码：d1c80d2a-4566-4d41-9bc9-2a51b9247667")
        }

        if (users.map { it.id!! }.toSet().size != users.size) {
            return IResponse.error(msg = "检测到重复的用户名或用户ID。错误定位码：11f6ff7a-917a-4cbe-98e0-50a816301d69")
        }

        for (user in users) {
            val existQuery = KtQueryWrapper(GroupMemberEntity::class.java)
                .eq(GroupMemberEntity::userId, user.id!!)
                .eq(GroupMemberEntity::groupId, groupId)
            if (groupMemberService.exists(existQuery)) {
                res.add(AddUsersResultEntry(
                    userId = user.id!!,
                    username = user.username!!,
                    success = false,
                    msg = "已经在组内"
                ))
                continue
            }

            groupMemberMapper.insert(GroupMemberEntity(
                userId = user.id!!,
                groupId = groupId,
            ))

            res.add(AddUsersResultEntry(
                userId = user.id!!,
                username = user.username!!,
                success = true,
                msg = "添加成功"
            ))
        }

        return IResponse.ok(res)
    }


    @Operation(summary = "获取群组内的用户列表")
    @GetMapping("users")
    fun getUsers(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam groupId: Long
    ): IResponse<List<Any>> {
        // 检查在不在组内。
        val existsQuery = KtQueryWrapper(GroupMemberEntity::class.java)
            .eq(GroupMemberEntity::groupId, groupId)
            .eq(GroupMemberEntity::userId, ticket.userId)
        if ( !groupMemberService.exists(existsQuery) ) {
            return IResponse.error(msg = "不在这个组里。")
        }

        val groupMemberEntities = groupMemberService.list(
            KtQueryWrapper(GroupMemberEntity::class.java)
                .eq(GroupMemberEntity::groupId, groupId)
        )

        val result = ArrayList<HashMap<String, Any?>>()
        groupMemberEntities.forEach {
            val user = userEntityService[it.userId]
            result.add(user.toHashMapWithKeysEvenNull(
                UserEntity::id,
                UserEntity::creator,
                UserEntity::createTime,
                UserEntity::username,
                UserEntity::lastLoginTime
            ))
        } // groupMemberEntities.forEach

        return IResponse.ok(result)
    }


    @GetMapping("permissions")
    @Operation(summary = "获得该用户在所有群组内的所有权限")
    fun getAllGroupPermissions(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam userId: Long? = null,
        @RequestParam groupId: Long? = null,
    ): IResponse<List<GroupPermissionGrantEntity>> {
        val query = KtQueryWrapper(GroupPermissionGrantEntity::class.java)
        if (userId == null) {
            query.eq(GroupPermissionGrantEntity::userId, ticket.userId)
        } else {
            query.eq(GroupPermissionGrantEntity::userId, userId)
        }

        if (groupId != null) {
            query.eq(GroupPermissionGrantEntity::groupId, groupId)
        }

        val granted = groupPermissionGrantMapper.selectList(query)

        return IResponse.ok(granted)
    }


    @Operation(summary = "展现有权限看到的所有群组。包含自己加入的和自己管理的。")
    @GetMapping("groups")
    fun getMyViewableGroups(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket
    ): IResponse<List<HashMap<String, Any?>>> {

        val res = HashMap<Long, HashMap<String, Any?>?>() // groupId -> entity
        fun addToRes(list: List<UserGroupEntity>) {
            list.forEach { entity ->
                val id = entity.id!!

                if (res.contains(id))
                    return@forEach

                res[id] = entity.toHashMapWithKeysEvenNull(
                    UserGroupEntity::id,
                    UserGroupEntity::groupName,
                    UserGroupEntity::note,
                    UserGroupEntity::createTime,
                )

                res[id]!!["membersCount"] = groupMemberService.count(
                    KtQueryWrapper(GroupMemberEntity::class.java)
                        .eq(GroupMemberEntity::groupId, id)
                )
            }
        }


        // 我自己所在的组

        val groupsImIn = groupMemberMapper.selectList(
            KtQueryWrapper(GroupMemberEntity::class.java).eq(GroupMemberEntity::userId, ticket.userId)
        )

        if (groupsImIn.isNotEmpty()) {
            val myGroupsQuery = KtQueryWrapper(UserGroupEntity::class.java)

            myGroupsQuery.`in`(
                UserGroupEntity::id,
                groupsImIn.map { it.groupId }
            )

            addToRes(
                userGroupMapper.selectList(myGroupsQuery)
            )
        }

        // todo: 还需添加的功能：管理员看自己不在的组

        return IResponse.ok(res.map { it.value!! })
    }


    data class RemoveUserRequestDto(
        val userId: Long?,
        val groupId: Long?
    )
    @PostMapping("removeUser")
    fun removeUser(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: RemoveUserRequestDto
    ): IResponse<Unit> {
        if (body.userId == null || body.groupId == null) {
            return IResponse.error()
        }

        // check permission

        groupPermissionService.ensurePermission(ticket, body.groupId, GroupPermission.ADD_OR_REMOVE_USER)

        // do the job

        userGroupService.removeUserFromGroup(userId = body.userId, groupId = body.groupId)

        return IResponse.ok()
    }


    @GetMapping("info")
    fun getGroupInfo(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam groupId: Long
    ): IResponse<Map<String, *>> {
        val group = userGroupService.getById(groupId) ?: return IResponse.error()

        val res = group.toHashMapWithKeysEvenNull(
            UserGroupEntity::id, UserGroupEntity::groupName, UserGroupEntity::note, UserGroupEntity::createTime
        )

        res["membersCount"] = groupMemberService.count(
            KtQueryWrapper(GroupMemberEntity::class.java).eq(GroupMemberEntity::groupId, groupId)
        )

        res["seatsCount"] = seatService.count(
            KtQueryWrapper(SeatEntity::class.java).eq(SeatEntity::groupId, groupId)
        )

        return IResponse.ok(res)
    }

}
