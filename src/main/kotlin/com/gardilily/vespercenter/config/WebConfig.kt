// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.config

import com.gardilily.vespercenter.common.SessionManager
import com.gardilily.vespercenter.properties.VesperCenterProperties
import com.gardilily.vespercenter.utils.IEnumConvertFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


@Configuration
class WebConfig @Autowired constructor(
    private val iEnumConvertFactory: IEnumConvertFactory,
    private val vesperCenterProperties: VesperCenterProperties
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                *(vesperCenterProperties.corsAllowedOrigins.toTypedArray())
            )
            .allowPrivateNetwork(true)
            .allowCredentials(false)
            .allowedMethods("*")
            .allowedHeaders("*")
            .exposedHeaders(SessionManager.HTTP_HEADER_KEY)
            .maxAge(3600)

    }

    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverterFactory(iEnumConvertFactory)
    }
}

