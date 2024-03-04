// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */


package com.gardilily.vespercenter.controller

import com.baomidou.mybatisplus.extension.kotlin.KtQueryWrapper
import com.gardilily.vespercenter.common.MacroDefines
import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.entity.PermissionGrantEntity
import com.gardilily.vespercenter.entity.PermissionGroupEntity
import com.gardilily.vespercenter.entity.PermissionGroupEntity.PermissionGroup
import com.gardilily.vespercenter.entity.UserEntity
import com.gardilily.vespercenter.mapper.PermissionGrantMapper
import com.gardilily.vespercenter.mapper.PermissionGroupMapper
import com.gardilily.vespercenter.mapper.SeatMapper
import com.gardilily.vespercenter.mapper.UserMapper
import com.gardilily.vespercenter.service.PermissionService
import com.gardilily.vespercenter.service.UserEntityService
import com.gardilily.vespercenter.service.UserService
import com.gardilily.vespercenter.utils.toHashMapWithKeysEvenNull
import jakarta.servlet.http.HttpSession
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.DigestUtils
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp

@RestController
@RequestMapping("user")
class UserController @Autowired constructor(
    val userMapper: UserMapper,
    val permissionGroupMapper: PermissionGroupMapper,
    val permissionGrantMapper: PermissionGrantMapper,
    val seatMapper: SeatMapper,

    val userEntityService: UserEntityService,
    val userService: UserService,
    val permissionService: PermissionService,
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
        httpSession: HttpSession
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

        httpSession.setAttribute(MacroDefines.SessionAttrKey.USER_ID, entity.id) // 设置 session 信息，标记其登录成功。
        entity.lastLoginTime = Timestamp(System.currentTimeMillis()) // 更新“上次登录时间”。
        userMapper.updateById(entity)
        return IResponse.ok(0, msg = "登录成功")
    }

    /**
     * 退出登录。
     */
    @GetMapping("logout")
    fun logout(httpSession: HttpSession): IResponse<Boolean> {
        httpSession.removeAttribute(MacroDefines.SessionAttrKey.USER_ID)
        return IResponse.ok(true)
    }

    /**
     * 获取我的权限列表。
     * 该功能只是告知前端，该用户有哪些权限，以便其在视觉上隐藏部分无法使用的功能。
     * 在各个接口逻辑内，仍需要对用户权限做校验。
     */
    @GetMapping("myPermissions")
    fun getMyPermissions(
        @SessionAttribute(MacroDefines.SessionAttrKey.USER_ID) userId: Long
    ): IResponse<List<PermissionGrantEntity>> {
        return IResponse.ok(permissionGrantMapper.selectList(
            KtQueryWrapper(PermissionGrantEntity::class.java)
                .eq(PermissionGrantEntity::userId, userId)
        ))
    }

    /**
     * 获知系统内总共有哪些权限。
     */
    @GetMapping("allPermissions")
    fun getAllPermissions(
        @SessionAttribute(MacroDefines.SessionAttrKey.USER_ID) userId: Long
    ): IResponse<List<PermissionGroupEntity>> {
        return IResponse.ok(permissionGroupMapper.selectList(
            KtQueryWrapper(PermissionGroupEntity::class.java)
        ))
    }

    /**
     * 获取我的基本信息。
     */
    @GetMapping("basicInfo")
    fun getBasicInfo(
        @SessionAttribute(MacroDefines.SessionAttrKey.USER_ID) userId: Long
    ): IResponse<HashMap<String, Any?>> {
        val entity = userEntityService[userId]

        return IResponse.ok(entity.toHashMapWithKeysEvenNull(
            UserEntity::id,
            UserEntity::username,
            UserEntity::lastLoginTime
        ))
    }

    data class ChangePasswordRequestDto(
        var oldPw: String? = null,
        var newPw: String? = null
    )
    @PostMapping("changePassword")
    fun changePassword(
        @SessionAttribute(MacroDefines.SessionAttrKey.USER_ID) bUserId: Long,
        @RequestBody body: ChangePasswordRequestDto
    ): IResponse<Unit> {

        if (body.oldPw == null || body.newPw == null) {
            return IResponse.error(msg = "信息不完整。")
        }

        val newPwStrengthResult = body.newPw!!.checkPasswordStrength()
        if (newPwStrengthResult != null) {
            return IResponse.error(msg = newPwStrengthResult)
        }

        val hashedOldPw = body.oldPw!!.toMd5Password(bUserId)
        val hashedNewPw = body.newPw!!.toMd5Password(bUserId)


        val userEntity = userEntityService[bUserId]

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
     */
    @GetMapping("allUsers")
    fun getAllUsers(
        @SessionAttribute(MacroDefines.SessionAttrKey.USER_ID) userId: Long
    ): IResponse<List<HashMap<String, Any?>>> {
        val entity = userEntityService[userId]
        val resList = ArrayList<HashMap<String, Any?>>()
        userMapper.selectList(KtQueryWrapper(UserEntity::class.java)).forEach { uEntity ->
            resList.add(uEntity.toHashMapWithKeysEvenNull(
                UserEntity::id,
                UserEntity::username,
                UserEntity::lastLoginTime
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
        @SessionAttribute(MacroDefines.SessionAttrKey.USER_ID) userId: Long,
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
            UserEntity::lastLoginTime
        )

        resMap["permissions"] = permissionGrantMapper.selectList(
            KtQueryWrapper(PermissionGrantEntity::class.java)
                .eq(PermissionGrantEntity::userId, targetUserId)
        )

        return IResponse.ok(resMap)

    }

    data class GrantPermissionRequestDto(
        var permission: PermissionGroup? = null,
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
        @SessionAttribute(MacroDefines.SessionAttrKey.USER_ID) userId: Long,
        @RequestBody body: GrantPermissionRequestDto
    ): IResponse<Unit> {
        // 传入参数非空校验。
        if (body.permission == null || body.grant == null || body.targetUserId == null) {
            return IResponse.error(msg = "请求体不完整。", code = HttpStatus.BAD_REQUEST)
        }

        val entity = userEntityService[userId]

        // 操作权限校验。
        when (body.permission) {
            // 本接口拒绝受理超级权限赋权请求！
            PermissionGroup.GRANT_PERMISSION -> return IResponse.error(msg = "拒绝赋权！", code = HttpStatus.FORBIDDEN)

            // 如果要赋予其他用户权限，自己必须拥有“赋权”权限。
            else -> permissionService.ensurePermission(entity, PermissionGroup.GRANT_PERMISSION)
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

}
