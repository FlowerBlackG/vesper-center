// SPDX-License-Identifier: MulanPSL-2.0
/* 上财果团团 */

package com.gardilily.vespercenter.utils

import kotlin.reflect.KMutableProperty1

/*
 * 对象编辑工具。
 */

/**
 * 将对象转换成 hashmap。
 */
fun Any.toHashMap(): HashMap<String, Any> {
    val res = HashMap<String, Any>()

    this::class.java.declaredFields.forEach { field ->

        // 保存之前的访问权限。
        // todo 目前的这种方法有待改进。
        val prevAccessibility = field.isAccessible

        // 强制打开访问权限。
        field.isAccessible = true

        // 获取值。
        val value = field.get(this)

        // 恢复访问权限。
        field.isAccessible = prevAccessibility

        if (value != null) {
            // 记录结果。
            res[field.name] = value
        }
    }

    return res
}

fun Any.toHashMapWithNulls(): HashMap<String, Any?> {
    val res = HashMap<String, Any?>()

    this::class.java.declaredFields.forEach { field ->

        // 保存之前的访问权限。
        // todo 目前的这种方法有待改进。
        val prevAccessibility = field.isAccessible

        // 强制打开访问权限。
        field.isAccessible = true

        // 获取值。
        val value = field.get(this)

        // 恢复访问权限。
        field.isAccessible = prevAccessibility

        // 记录结果。
        res[field.name] = value

    }

    return res
}

/**
 * 将对象转换成 hashmap，但只取指定的成员。
 */
inline fun <reified T> Any.toHashMapWithKeys(
    vararg keys: KMutableProperty1<T, *>
): HashMap<String, Any> {

    val res = HashMap<String, Any>()

    keys.forEach { key ->
        val value = key.get(this as T)
        if (value != null) {
            res[key.name] = value
        }
    }

    return res
}

inline fun <reified T> Any.toHashMapWithKeysEvenNull(
    vararg keys: KMutableProperty1<T, *>
): HashMap<String, Any?> {

    val res = HashMap<String, Any?>()

    keys.forEach { key ->
        val value = key.get(this as T)
        res[key.name] = value
    }

    return res
}

