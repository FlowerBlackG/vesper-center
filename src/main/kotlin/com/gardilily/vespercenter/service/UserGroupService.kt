// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月14日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.entity.UserGroupEntity
import com.gardilily.vespercenter.mapper.UserGroupMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class UserGroupService @Autowired constructor(

) : ServiceImpl<UserGroupMapper, UserGroupEntity>() {


}
