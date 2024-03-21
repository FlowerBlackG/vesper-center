// SPDX-License-Identifier: MulanPSL-2.0

/* 上财果团团 */

package com.gardilily.vespercenter.common

import com.gardilily.vespercenter.dto.IResponse
import com.gardilily.vespercenter.service.PermissionService
import com.gardilily.vespercenter.service.UserEntityService
import com.gardilily.vespercenter.utils.Slf4k
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * 全局异常处理类。
 */
@RestControllerAdvice
@Slf4k
class GlobalExceptionHandler @Autowired private constructor(

) {

    @ResponseBody
    @ExceptionHandler(value = [MissingServletRequestParameterException::class])
    fun missingServletRequestParameterExceptionHandler(e: Exception): IResponse<Unit> {
        return IResponse.error(
            msg = e.message,
            code = HttpStatus.BAD_REQUEST
        )
    }


    @ResponseBody
    @ExceptionHandler(value = [ServletRequestBindingException::class])
    fun servletRequestBindingExceptionHandler(e: Exception): IResponse<Unit> {
        return IResponse.error(
            msg = e.message + " 可能是鉴权错误。",
            code = HttpStatus.UNAUTHORIZED
        )
    }

    @ResponseBody
    @ExceptionHandler(value = [HttpRequestMethodNotSupportedException::class])
    fun httpRequestMethodNotSupportedExceptionHandler(e: Exception): IResponse<Unit> {
        return IResponse.error(
            msg = "请求方式不支持。",
            code = HttpStatus.METHOD_NOT_ALLOWED
        )
    }

    @ResponseBody
    @ExceptionHandler(value = [HttpMessageNotReadableException::class])
    fun httpMessageNotReadableExceptionHandler(e: Exception): IResponse<Unit> {
        return IResponse.error(
            msg = "请求体无法读取。",
            code = HttpStatus.BAD_REQUEST
        )
    }


    @ResponseBody
    @ExceptionHandler(value = [PermissionService.PermissionDeniedException::class])
    fun bUserPermissionDeniedExceptionHandler(e: Exception): IResponse<Unit> {
        return IResponse.error(
            msg = "无权限：${e.message}",
            code = HttpStatus.FORBIDDEN
        )
    }


    @ResponseBody
    @ExceptionHandler(value = [UserEntityService.UserNotFoundException::class])
    fun userEntityServiceUserNotFoundExceptionHandler(e: Exception): IResponse<Unit> {
        return IResponse.error(
            msg = e.message,
            code = HttpStatus.UNAUTHORIZED
        )
    }

    @ResponseBody
    @ExceptionHandler(value = [NoResourceFoundException::class])
    fun noResourceFoundExceptionHandler(e: Exception): IResponse<Unit> {
        return IResponse.error(
            msg = "资源不存在：${e.message}",
            code = HttpStatus.NOT_FOUND
        )
    }


    @ResponseBody
    @ExceptionHandler(value = [Exception::class])
    fun defaultExceptionHandler(e: Exception): IResponse<Unit> {
        e.printStackTrace()
        return IResponse.error(msg = "未知错误。")
    }
}
