// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */


package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.annotation.IEnum
import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.fasterxml.jackson.annotation.JsonValue
import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.*
import com.gardilily.vespercenter.entity.PermissionEntity.Permission
import com.gardilily.vespercenter.mapper.PermissionGrantMapper
import com.gardilily.vespercenter.mapper.PermissionMapper
import com.gardilily.vespercenter.mapper.SeatMapper
import com.gardilily.vespercenter.mapper.UserMapper
import com.gardilily.vespercenter.service.*
import com.gardilily.vespercenter.utils.toHashMapWithKeysEvenNull
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.DigestUtils
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import kotlin.random.Random

@RestController
@RequestMapping("user")
class UserController @Autowired constructor(
    val userMapper: UserMapper,
    val permissionMapper: PermissionMapper,
    val permissionGrantMapper: PermissionGrantMapper,
    val seatMapper: SeatMapper,
    val seatService: SeatService,

    val userEntityService: UserEntityService,
    val userService: UserService,
    val permissionService: PermissionService,

    val userGroupService: UserGroupService,
    val groupMemberService: GroupMemberService,
    val sessionManager: SessionManager,
) {

    /**
     * 将用户密码转换成 md5 串。
     *
     * 算法：
     *   a = md5( password )
     *   a1 = a[0 .. 15]
     *   a2 = a[16 .. 31]
     *
     *   u = md5( userid )
     *   u1 = u[0 .. 15]
     *   u2 = u[16 .. 31]
     *
     *   x = a1 + u1 + a2 + u2
     *
     *   result = md5( x )
     *
     *
     * 该算法属于 md5 加盐算法，盐取自用户 id，能够提供更好的安全性。
     *
     */
    private fun String.toMd5Password(userId: Long): String {
        val a = DigestUtils.md5DigestAsHex(this.toByteArray())
        val a1 = a.substring(0 .. 15)
        val a2 = a.substring(16 .. 31)

        val u = DigestUtils.md5DigestAsHex(userId.toString().toByteArray())
        val u1 = u.substring(0 .. 15)
        val u2 = u.substring(16 .. 31)

        val x = "$a1$u1$a2$u2"
        return DigestUtils.md5DigestAsHex(x.toByteArray())
    }

    /**
     * 检查密码强度。
     *
     * 强密码规则：
     *   长度：不小于 8，不大于 64。
     *   包含：大写字母、小写字母、数字、特殊字符
     *   特殊字符定义：*&$%.,;:'"()~[]{}-_=+!@#<>?
     *
     * @return 返回 null 表示密码强度足够。返回 String 表示密码不够强。String 内容为强度不足的说明。
     */
    private fun String.checkPasswordStrength(): String? {
        val specialChars = "*&\\$%.,;:'\"()~[]{}-_=+!@#<>?"
        val alphabetUpper = 'A' .. 'Z'
        val alphabetLower = 'a' .. 'z'
        val digits = '0' .. '9'

        // 长度检查。
        if (this.length < 8) {
            return "密码太短。"
        } else if (this.length > 64) {
            return "密码太长。"
        }

        // 字符数统计。
        var specialCharCount = 0
        var digitCount = 0
        var upperCount = 0
        var lowerCount = 0

        this.forEach { ch ->
            when (ch) {
                in specialChars -> {
                    specialCharCount ++
                }
                in alphabetLower -> {
                    lowerCount ++
                }
                in alphabetUpper -> {
                    upperCount ++
                }
                in digits -> {
                    digitCount ++
                }
                else -> {
                    return "不好的字符（ch code: ${ch.code}）。"
                }
            }
        }

        return if (specialCharCount == 0) {
            "需要特殊字符"
        } else if (digitCount == 0) {
            "需要数字"
        } else if (upperCount == 0) {
            "需要大写字母"
        } else if (lowerCount == 0) {
            "需要小写字母"
        } else {
            null // 通过校验。
        }
    }



    @PostMapping("createSuperUser")
    fun createSuperUser(
        @RequestBody body: HashMap<String, Any>
    ): IResponse<HashMap<String, Any?>> {
        // 输入参数非空校验。
        val username = body["username"] as String? ?: return IResponse.error(msg = "需要用户名", code = HttpStatus.BAD_REQUEST)
        val password = body["password"] as String? ?: return IResponse.error(msg = "需要密码。", code = HttpStatus.BAD_REQUEST)

        // 权限检查
        val userCount = userMapper.selectCount(KtQueryWrapper(UserEntity::class.java)) ?: return IResponse.error(msg = "内部错误")
        if (userCount != 0L) {
            return IResponse.error(msg = "系统内已经存在超级用户！")
        }

        // 密码强度检查
        val passwdStrengthRes = password.checkPasswordStrength()
        if (passwdStrengthRes != null) {
            return IResponse.error(msg = "密码校验失败：$passwdStrengthRes")
        }

        // 创建用户
        val user = UserEntity(
            username = username,
            createTime = Timestamp(System.currentTimeMillis()),
            creator = 0
        )

        if (userMapper.insert(user) != 1) {
            return IResponse.error(msg = "用户添加失败！")
        }

        user.passwd = password.toMd5Password(user.id!!)
        user.creator = user.id
        userMapper.updateById(user)

        // 赋权

        Permission.allPermissions().forEach {
            permissionGrantMapper.insert(PermissionGrantEntity(
                userId = user.id!!,
                permissionId = it
            ))
        }


        return IResponse.ok(user.toHashMapWithKeysEvenNull(
            UserEntity::id,
            UserEntity::createTime,
            UserEntity::username
        ))
    }


    /**
     * 用户登录。
     * 传入参数：
     *   uname：用户名。
     *   password：密码。明文。
     *
     * 登录成功后，该用户的 id 会被设置到 session 中
     *   键：MacroDefines.SessionAttrKey.B_USER_ID
     *   类型：Long
     *
     * @return 整数结果为0表示可正常使用。
     */
    @PostMapping("login")
    fun login(
        @RequestBody requestBody: HashMap<String, Any>,
        response: HttpServletResponse,
    ): IResponse<Int> {

        // 输入参数非空校验。
        val uname = requestBody["uname"] as String? ?: return IResponse.error(msg = "需要用户名", code = HttpStatus.BAD_REQUEST)
        val password = requestBody["password"] as String? ?: return IResponse.error(msg = "需要密码。", code = HttpStatus.BAD_REQUEST)

        // 先取出用户信息，再检查密码对不对。
        val entity = userMapper.selectOne(
            KtQueryWrapper(UserEntity::class.java).eq(UserEntity::username, uname)
        ) ?: return IResponse.error(msg = "登录失败", code = HttpStatus.FORBIDDEN)

        if (entity.passwd != password.toMd5Password(entity.id!!)) {
            return IResponse.error(msg = "登录失败", code = HttpStatus.FORBIDDEN)
        }

        // 登录成功。

        val sessionKey = sessionManager.addSession(entity.id!!) // 设置 session 信息，标记其登录成功。
        response.addHeader(SessionManager.HTTP_HEADER_KEY, sessionKey)

        entity.lastLoginTime = Timestamp(System.currentTimeMillis()) // 更新“上次登录时间”。
        userMapper.updateById(entity)
        return IResponse.ok(0, msg = "登录成功")
    }

    /**
     * 退出登录。
     */
    @GetMapping("logout")
    fun logout(request: HttpServletRequest): IResponse<Boolean> {
        val token = request.getHeader(SessionManager.HTTP_HEADER_KEY)
        if (token != null) {
            sessionManager.remove(token)
        }
        return IResponse.ok(true)
    }

    /**
     * 获取我的权限列表。
     * 该功能只是告知前端，该用户有哪些权限，以便其在视觉上隐藏部分无法使用的功能。
     * 在各个接口逻辑内，仍需要对用户权限做校验。
     */
    @GetMapping("myPermissions")
    fun getMyPermissions(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) sessionTicket: SessionManager.Ticket
    ): IResponse<List<Permission>> {
        return IResponse.ok(
            permissionGrantMapper.selectList(
                KtQueryWrapper(PermissionGrantEntity::class.java)
                    .eq(PermissionGrantEntity::userId, sessionTicket.userId)
            ).map { it.permissionId }
        )
    }

    @GetMapping("userPermissions")
    fun getUserPermissions(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam targetUserId: Long
    ): IResponse<List<Permission>> {
        return IResponse.ok(
            permissionGrantMapper.selectList(
                KtQueryWrapper(PermissionGrantEntity::class.java)
                    .eq(PermissionGrantEntity::userId, targetUserId)
            ).map { it.permissionId }
        )
    }

    /**
     * 获知系统内总共有哪些权限。
     */
    @GetMapping("allPermissions")
    fun getAllPermissions(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket
    ): IResponse<List<PermissionEntity>> {
        return IResponse.ok(permissionMapper.selectList(
            KtQueryWrapper(PermissionEntity::class.java)
        ))
    }

    /**
     * 获取我的基本信息。
     */
    @GetMapping("basicInfo")
    fun getBasicInfo(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket
    ): IResponse<HashMap<String, Any?>> {
        val entity = userEntityService[ticket.userId]

        return IResponse.ok(entity.toHashMapWithKeysEvenNull(
            UserEntity::id,
            UserEntity::username,
            UserEntity::lastLoginTime,
            UserEntity::createTime
        ))
    }

    data class ChangePasswordRequestDto(
        var oldPw: String? = null,
        var newPw: String? = null
    )
    @PostMapping("changePassword")
    fun changePassword(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: ChangePasswordRequestDto
    ): IResponse<Unit> {
        val userId = ticket.userId

        if (body.oldPw == null || body.newPw == null) {
            return IResponse.error(msg = "信息不完整。")
        }

        val newPwStrengthResult = body.newPw!!.checkPasswordStrength()
        if (newPwStrengthResult != null) {
            return IResponse.error(msg = newPwStrengthResult)
        }

        val hashedOldPw = body.oldPw!!.toMd5Password(userId)
        val hashedNewPw = body.newPw!!.toMd5Password(userId)


        val userEntity = userEntityService[userId]

        if (hashedOldPw != userEntity.passwd) {
            return IResponse.error(msg = "旧密码错误。")
        }

        userEntity.passwd = hashedNewPw
        userMapper.updateById(userEntity)

        return IResponse.ok()
    }

    /**
     * 获取所有用户。
     * todo: 应支持分页查询。
     * todo: 权限控制
     */
    @GetMapping("allUsers")
    fun getAllUsers(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket
    ): IResponse<List<HashMap<String, Any?>>> {
        val entity = userEntityService[ticket.userId]
        val resList = ArrayList<HashMap<String, Any?>>()
        userMapper.selectList(KtQueryWrapper(UserEntity::class.java)).forEach { uEntity ->
            resList.add(uEntity.toHashMapWithKeysEvenNull(
                UserEntity::id,
                UserEntity::username,
                UserEntity::lastLoginTime,
                UserEntity::createTime,
                UserEntity::creator,
            ))
        }

        return IResponse.ok(resList)
    }

    /**
     * 获取某个用户的信息。
     *
     * @param targetUserId 目标用户的身份编号。
     */
    @GetMapping("userDetail")
    fun getUserDetail(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestParam targetUserId: Long? = null
    ): IResponse<HashMap<String, Any?>> {
        targetUserId ?: return IResponse.error(msg = "需要targetUserId。", code = HttpStatus.BAD_REQUEST)

        val targetEntity = userMapper.selectOne(
            KtQueryWrapper(UserEntity::class.java).eq(UserEntity::id, targetUserId)
        ) ?: return IResponse.error(msg = "没有这个用户。", code = HttpStatus.BAD_REQUEST)

        val resMap = targetEntity.toHashMapWithKeysEvenNull(
            UserEntity::id,
            UserEntity::username,
            UserEntity::createTime,
            UserEntity::lastLoginTime,
            UserEntity::creator,
        )

        resMap["permissions"] = permissionGrantMapper.selectList(
            KtQueryWrapper(PermissionGrantEntity::class.java)
                .eq(PermissionGrantEntity::userId, targetUserId)
        )

        val creatorEntity = userService.getById(targetEntity.creator)
        resMap["creatorEntity"] = creatorEntity?.toHashMapWithKeysEvenNull(
            UserEntity::id,
            UserEntity::username,
            UserEntity::createTime,
            UserEntity::lastLoginTime,
        )

        resMap["groupsIn"] = groupMemberService.count(
            KtQueryWrapper(GroupMemberEntity::class.java).eq(GroupMemberEntity::userId, targetUserId)
        )

        resMap["seatsOwned"] = seatMapper.selectCount(
            KtQueryWrapper(SeatEntity::class.java).eq(SeatEntity::userId, targetUserId)
        )

        return IResponse.ok(resMap)

    }

    data class GrantPermissionRequestDto(
        var permission: Permission? = null,
        var grant: Boolean? = null,
        var targetUserId: Long? = null
    )

    /**
     * 赋权。
     *
     */
    @PostMapping("grantPermission")
    @Transactional
    fun grantPermission(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: GrantPermissionRequestDto
    ): IResponse<Unit> {
        // 传入参数非空校验。
        if (body.permission == null || body.grant == null || body.targetUserId == null) {
            return IResponse.error(msg = "请求体不完整。", code = HttpStatus.BAD_REQUEST)
        }

        val entity = userEntityService[ticket.userId]

        // 操作权限校验。
        when (body.permission) {
            // 本接口拒绝受理超级权限赋权请求！
            Permission.GRANT_PERMISSION -> return IResponse.error(msg = "不允许改动超级权限。", code = HttpStatus.FORBIDDEN)

            // 如果要赋予其他用户权限，自己必须拥有“赋权”权限。
            else -> permissionService.ensurePermission(entity, Permission.GRANT_PERMISSION)
        }

        // 检查待赋权用户是否存在。
        userMapper.selectById(body.targetUserId) ?: return IResponse.error(msg = "没有这个用户。", code = HttpStatus.BAD_REQUEST)


        // 先要知道这个权限是否已经存在。
        val permissionIsGranted = permissionGrantMapper.selectOne(
            KtQueryWrapper(PermissionGrantEntity::class.java)
                .eq(PermissionGrantEntity::permissionId, body.permission)
                .eq(PermissionGrantEntity::userId, body.targetUserId)
        ) != null

        return if (permissionIsGranted == body.grant) {
            IResponse.ok(msg = "无需改动。")
        } else if (permissionIsGranted) {
            // 取消权限。
            permissionGrantMapper.delete(
                KtQueryWrapper(PermissionGrantEntity::class.java)
                    .eq(PermissionGrantEntity::userId, body.targetUserId)
                    .eq(PermissionGrantEntity::permissionId, body.permission)
            )

            IResponse.ok(msg = "取消权限。")
        } else {
            // 赋权。
            permissionGrantMapper.insert(
                PermissionGrantEntity(
                    permissionId = body.permission!!,
                    userId = body.targetUserId!!
                )
            )

            IResponse.ok(msg = "赋权。")
        }

    } // fun grantPermission


    class CreateNewUserDto private constructor() {

        data class RequestUserEntity(
            val username: String,
            val group: Long?
        )

        data class CreateUsersResultEntry(
            var group: Long? = null,
            var passwd: String? = null,
            /** userid */
            var id: Long? = null,
            var username: String,
            var result: Result = Result.PENDING,
            var resultMsg: String = "",
        )

        enum class Result(@JsonValue val enumValue: String) : IEnum<String> {
            PENDING("pending"),
            CREATED("created"),
            FAILED("failed"),

            ;

            override fun getValue(): String {
                return this.enumValue
            }
        }
    }


    /**
     * 批量创建新用户。
     *
     * 请求参数：
     *     newUsers: [NewUserEntity]
     *   其中，NewUserEntity 结构如下：
     *     username: String
     *     group: Long?
     */
    @Operation(summary = "批量创建新用户")
    @Parameters(
        Parameter(name = "newUsers", example = "[\"GuanTouyu\", \"YeSiqiu\", \"Strawpplenage°\"]")
    )
    @PostMapping("createUsers")
    fun createUsers(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: HashMap<String, Any>
    ): IResponse<Map<String, CreateNewUserDto.CreateUsersResultEntry>> {
        permissionService.ensurePermission(ticket.userId, Permission.CREATE_AND_DELETE_USER)

        val newUsersRaw = body["newUsers"] as List<*>? ?: return IResponse.error(msg = "需要 newUsers。")

        val newUsers = ArrayList<CreateNewUserDto.RequestUserEntity>()

        /**
         * username -> result entity
         */
        val resultMap = HashMap<String, CreateNewUserDto.CreateUsersResultEntry>()

        for (it in newUsersRaw) {
            it as HashMap<*, *>
            newUsers.add(CreateNewUserDto.RequestUserEntity(
                username = it["username"] as String? ?: continue,
                group = (it["group"] as Int?)?.toLong()
            ))
        }

        // 插入所有用户的信息。

        newUsers.forEach {

            // 检查用户组是否存在。
            if (it.group != null) {
                if (userGroupService.getById(it.group) == null) {
                    resultMap[it.username] = CreateNewUserDto.CreateUsersResultEntry(
                        username = it.username,
                        result = CreateNewUserDto.Result.FAILED,
                        resultMsg = "组不存在"
                    )
                }
                return@forEach
            }

            // 检查重名。
            if (userService.exists(KtQueryWrapper(UserEntity::class.java).eq(UserEntity::username, it.username))) {
                if (!resultMap.contains(it.username)) {
                    resultMap[it.username] = CreateNewUserDto.CreateUsersResultEntry(
                        username = it.username,
                        result = CreateNewUserDto.Result.FAILED,
                        resultMsg = "用户名重复"
                    )
                }

                return@forEach
            }

            // 随机密码
            val passwd = Random.nextLong().toString().toMd5Password(1).substring(0 until 12)
            val user = UserEntity(
                creator = ticket.userId,
                username = it.username,
                createTime = Timestamp(System.currentTimeMillis())
            )

            // 插入用户
            if (userMapper.insert(user) == 1) {
                resultMap[it.username] = CreateNewUserDto.CreateUsersResultEntry(
                    result = CreateNewUserDto.Result.CREATED,
                    username = it.username,
                    group = it.group,
                    passwd = passwd,
                    resultMsg = "创建成功",
                    id = user.id
                )
            }

            user.passwd = passwd.toMd5Password(user.id!!)
            userMapper.updateById(user)

            // 加入群组
            if (it.group != null) {
                groupMemberService.baseMapper.insert(GroupMemberEntity(
                    userId = user.id!!,
                    groupId = it.group
                ))
            }
        } // newUsers.forEach

        return IResponse.ok(resultMap)
    }


    data class DeleteUserRequestDto(
        val userIds: List<Long>?,
        val usernames: List<String>?
    )

    data class DeleteUserResultDtoEntry(
        val userId: Long?,
        val username: String?,
        val success: Boolean,
        val msg: String
    )

    /**
     *
     * helper function
     */
    private fun deleteUsers(
        operatorUserId: Long,
        users: List<Long>
    ): List<DeleteUserResultDtoEntry> {

        val res = HashMap<Long, DeleteUserResultDtoEntry>()

        for (userId in users) {
            if (res.contains(userId)) {
                continue
            }

            val user = userService.getById(userId)
            if (user == null) {
                res[userId] = DeleteUserResultDtoEntry(
                    success = false,
                    msg = "查无此人",
                    username = null,
                    userId = userId
                )
                continue
            }

            if (user.id == operatorUserId) {
                res[userId] = DeleteUserResultDtoEntry(
                    success = false,
                    msg = "不许删自己！",
                    username = user.username!!,
                    userId = userId
                )
                continue
            }

            // 不许删超级管理员。

            if (permissionService.checkPermission(user.id!!, Permission.GRANT_PERMISSION)) {
                res[userId] = DeleteUserResultDtoEntry(
                    success = false,
                    msg = "不许造反！",
                    username = user.username!!,
                    userId = userId
                )
                continue
            }

            // check permission
            if (permissionService.checkPermission(operatorUserId, Permission.DELETE_ANY_USER)) {
                // passed
            } else if (
                permissionService.checkPermission(operatorUserId, Permission.CREATE_AND_DELETE_USER)
                && operatorUserId == user.creator
            ) {
                // passed
            } else {
                res[userId] = DeleteUserResultDtoEntry(
                    success = false,
                    msg = "无权限",
                    username = user.username!!,
                    userId = userId
                )
                continue
            }


            // 执行到这里，允许删除该用户。
            userService.removeUser(user)

            res[userId] = DeleteUserResultDtoEntry(
                success = true,
                msg = "已删除",
                username = user.username!!,
                userId = userId
            )
        }

        return res.map { it.value }
    }

    @Operation(summary = "批量删除用户。usernames 和 userIds 二选一。")
    @PostMapping("deleteUsers")
    fun deleteUsers(
        @RequestAttribute(SessionManager.SESSION_ATTR_KEY) ticket: SessionManager.Ticket,
        @RequestBody body: DeleteUserRequestDto
    ): IResponse<List<DeleteUserResultDtoEntry>> {
        return if (body.userIds != null) {
            IResponse.ok(
                this.deleteUsers(ticket.userId, body.userIds)
            )
        } else if (body.usernames != null) {
            val userIds = ArrayList<Long>()

            val notFoundUsers = HashMap<String, DeleteUserResultDtoEntry>()  // username -> resObj

            for (username in body.usernames) {
                val user = userService.getOne(
                    KtQueryWrapper(UserEntity::class.java).eq(UserEntity::username, username)
                )

                if (user == null) {
                    notFoundUsers[username] = DeleteUserResultDtoEntry(
                        userId = null,
                        username = username,
                        success = false,
                        msg = "查无此人"
                    )
                    continue
                }

                userIds.add(user.id!!)
            }

            IResponse.ok(
                this.deleteUsers(ticket.userId, userIds) + notFoundUsers.map { it.value }
            )
        } else {
            IResponse.ok(emptyList())
        }
    }

}
