// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.config

import com.baomidou.mybatisplus.annotation.DbType
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer
import com.baomidou.mybatisplus.core.MybatisConfiguration
import com.baomidou.mybatisplus.extension.MybatisMapWrapperFactory
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * 自动构造配置信息。装配不能自动构造的对象。这些对象通常来自第三方库。
 */
@Configuration
class BeanConfig {
    /**
     * 自动装配 RestTemplate。
     * RestTemplate 用于在服务端发起网络请求，与微信服务器等通信。
     *
     * 隐患：未设置网络超时时间。该事情值得评估。
     */
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        return builder.build()
    }

    /**
     * 启用 mybatis plus 分页能力。
     */
    @Bean
    fun mybatisPlusPaginationInterceptor(): MybatisPlusInterceptor {
        val interceptor = MybatisPlusInterceptor()
        interceptor.addInnerInterceptor(PaginationInnerInterceptor(DbType.MYSQL))
        return interceptor
    }

    @Bean
    fun mybatisConfigurationCustomizer(): ConfigurationCustomizer {
        return ConfigurationCustomizer { configuration -> configuration?.objectWrapperFactory = MybatisMapWrapperFactory() }
    }

    @Bean
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): RedisTemplate<String, Any> {
        val redis = RedisTemplate<String, Any>()
        redis.setConnectionFactory(redisConnectionFactory)
        return redis
    }
}
