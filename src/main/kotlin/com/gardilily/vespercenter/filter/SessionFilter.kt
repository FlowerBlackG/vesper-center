// SPDX-License-Identifier: MulanPSL-2.0

/*
 *
 * 创建于 2024年3月19日 上海市嘉定区
 */


package com.gardilily.vespercenter.filter

import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.utils.Slf4k
import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.annotation.WebFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4k
@WebFilter(filterName = "SessionFilter", urlPatterns = ["/*"])
class SessionFilter @Autowired constructor(
    val sessionManager: SessionManager
) : Filter {
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        sessionManager.doFilter(request as HttpServletRequest, response as HttpServletResponse, chain)
    }
}
