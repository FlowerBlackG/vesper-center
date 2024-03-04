// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.utils

import com.baomidou.mybatisplus.annotation.IEnum
import org.springframework.core.convert.converter.Converter
import org.springframework.core.convert.converter.ConverterFactory
import org.springframework.stereotype.Component

@Component
class IEnumConvertFactory : ConverterFactory<String, IEnum<*>?> {

    override fun <T : IEnum<*>?> getConverter(targetType: Class<T>): Converter<String?, T> {
        return StringToIEnum(targetType)
    }

    private class StringToIEnum<T : IEnum<*>?>(private val targetType: Class<T>) : Converter<String?, T> {
        override fun convert(source: String): T? {
            return if (source.isEmpty()) {
                null
            } else {
                getIEnum(targetType, source) as T?
            }
        }
    }

    companion object {
        fun <T : IEnum<*>?> getIEnum(targetType: Class<T>, source: String): Any? {
            for (enumObj in targetType.enumConstants) {
                if (source == (enumObj?.value as Int?).toString()) {
                    return enumObj
                }
            }

            return null
        }
    }

}
