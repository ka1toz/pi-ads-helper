package com.jbase.demo

import android.util.Log

private const val TAG = "JBaseReflection"

internal fun Any.callOptional(methodName: String, vararg args: Any?): Any? {
    return runCatching {
        val method = javaClass.methods.firstOrNull { method ->
            method.name == methodName &&
                method.parameterTypes.size == args.size &&
                method.parameterTypes.zip(args.map { it?.javaClass }).all { (param, argType) ->
                    argType == null || param.wrapPrimitive().isAssignableFrom(argType.wrapPrimitive())
                }
        }
            ?: javaClass.methods.firstOrNull { method ->
                method.name == methodName && method.parameterTypes.isEmpty()
            }
            ?: error("Method not found: ${javaClass.name}.$methodName/${args.size}")

        if (method.parameterTypes.isEmpty()) {
            method.invoke(this)
        } else {
            method.invoke(this, *args)
        }
    }.onFailure {
        Log.w(TAG, "${javaClass.simpleName}.$methodName failed: ${it.message}")
    }.getOrNull()
}

internal fun Any.callBoolean(methodName: String, default: Boolean = false, vararg args: Any?): Boolean {
    return callOptional(methodName, *args) as? Boolean ?: default
}

internal fun classInstance(className: String): Any? {
    return runCatching {
        val clazz = Class.forName(className)
        val getInstance = clazz.methods.firstOrNull { it.name == "getInstance" && it.parameterTypes.isEmpty() }
        if (getInstance != null) {
            getInstance.invoke(null)
        } else {
            clazz.getDeclaredConstructor().newInstance()
        }
    }.onFailure {
        Log.e(TAG, "Cannot create $className: ${it.message}", it)
    }.getOrNull()
}

private fun Class<*>?.wrapPrimitive(): Class<*>? {
    return when (this) {
        java.lang.Boolean.TYPE -> java.lang.Boolean::class.java
        java.lang.Byte.TYPE -> java.lang.Byte::class.java
        java.lang.Character.TYPE -> java.lang.Character::class.java
        java.lang.Double.TYPE -> java.lang.Double::class.java
        java.lang.Float.TYPE -> java.lang.Float::class.java
        java.lang.Integer.TYPE -> java.lang.Integer::class.java
        java.lang.Long.TYPE -> java.lang.Long::class.java
        java.lang.Short.TYPE -> java.lang.Short::class.java
        java.lang.Void.TYPE -> java.lang.Void::class.java
        else -> this
    }
}
