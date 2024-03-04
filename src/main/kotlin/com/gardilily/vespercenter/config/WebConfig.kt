// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.config

import com.gardilily.vespercenter.utils.IEnumConvertFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


@Configuration
class WebConfig @Autowired constructor(
    private val iEnumConvertFactory: IEnumConvertFactory
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:3000",
                "https://vesper-front.gardilily.com",
                "http://localhost:13287")
            .allowCredentials(true)
            .allowedMethods("*")
            .allowedHeaders("*")
            .maxAge(3600)
    }

    override fun addFormatters(registry: FormatterRegistry) {
        registry.addConverterFactory(iEnumConvertFactory)
    }
}

