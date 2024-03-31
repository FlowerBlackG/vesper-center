// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */


package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.gardilily.vespercenter.common.MacroDefines
import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.*
import com.gardilily.vespercenter.entity.GroupPermissionEntity.GroupPermission
import com.gardilily.vespercenter.entity.PermissionEntity.Permission
import com.gardilily.vespercenter.mapper.GroupMemberMapper
import com.gardilily.vespercenter.mapper.GroupPermissionGrantMapper
import com.gardilily.vespercenter.mapper.UserGroupMapper
import com.gardilily.vespercenter.service.*
import com.gardilily.vespercenter.utils.Slf4k.Companion.log
import com.gardilily.vespercenter.utils.toHashMapWithKeysEvenNull
import io.swagger.v3.oas.annotations.Operation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.SessionAttribute
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
            GroupPermission.GRANT_PERMISSION -> return IResponse.error(msg = "拒绝赋权！")

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
    @PostMapping("remove")
    fun removeGroup(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, Any>
    ): IResponse<Unit> {
        val userId = ticket.userId

        val gid = body["groupId"] as Long? ?: return IResponse.error(msg = "groupId required.")
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


    /**
     *
     * 参数：
     *   groupId: Long
     *   users: List<Long>
     */
    @Operation(summary = "将用户添加到群组")
    @PostMapping("addUsers")
    fun addUsers(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, Any>
    ): IResponse<Unit> {

        val groupId = body["groupId"] as Long? ?: return IResponse.error(msg = "groupId required.")
        val users = body["users"] as List<Long>? ?: return IResponse.error(msg = "users required.")

        val group = userGroupService.getById(groupId) ?: return IResponse.error(msg = "没有这个组。")
        groupPermissionService.ensurePermission(ticket.userId, groupId, GroupPermission.ADD_OR_REMOVE_USER)

        groupMemberService.addUsersToGroup(users, groupId)

        return IResponse.ok()
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


    data class GroupPermissionResponseDtoEntry(
        val group: Long,
        val permission: GroupPermission
    )
    @GetMapping("permissions")
    @Operation(summary = "获得该用户在所有群组内的所有权限")
    fun getAllGroupPermissions(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket
    ): IResponse<List<GroupPermissionResponseDtoEntry>> {
        val granted = groupPermissionGrantMapper.selectList(
            KtQueryWrapper(GroupPermissionGrantEntity::class.java)
                .eq(GroupPermissionGrantEntity::userId, ticket.userId)
        )

        val res = ArrayList<GroupPermissionResponseDtoEntry>()
        granted.forEach {
            res.add(GroupPermissionResponseDtoEntry(
                group = it.groupId,
                permission = it.permissionId
            ))
        }

        return IResponse.ok(res)
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

}
