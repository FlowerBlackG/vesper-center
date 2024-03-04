// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月3日 上海市嘉定区
 */


package com.gardilily.vespercenter.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.gardilily.vespercenter.entity.UserEntity
import org.apache.ibatis.annotations.Mapper
import org.springframework.stereotype.Repository

@Repository
@Mapper
interface UserMapper : BaseMapper<UserEntity>
