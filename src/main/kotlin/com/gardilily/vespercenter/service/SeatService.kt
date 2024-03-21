// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月15日 上海市嘉定区
 */


package com.gardilily.vespercenter.service

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import com.gardilily.vespercenter.entity.SeatEntity
import com.gardilily.vespercenter.mapper.SeatMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class SeatService @Autowired constructor(
    val linuxService: LinuxService
) : ServiceImpl<SeatMapper, SeatEntity>() {



}
