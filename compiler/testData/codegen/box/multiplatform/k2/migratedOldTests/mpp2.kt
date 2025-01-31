// !LANGUAGE: +MultiPlatformProjects
// TARGET_BACKEND: JS_IR
// TARGET_BACKEND: NATIVE
// TARGET_BACKEND: WASM
// TARGET_BACKEND: JVM_IR

// IGNORE_BACKEND_K1: JVM, JVM_IR, JS, JS_IR, JS_IR_ES6, WASM
// IGNORE_NATIVE_K1: mode=ONE_STAGE_MULTI_MODULE

// MODULE: common
// FILE: common.kt

expect class C {
    constructor(arg: Any?)
}

expect class T

expect fun f(arg: Int): Int

expect var p: String

// MODULE: actual()()(common)
// FILE: actual.kt
actual data class C actual constructor(val arg: Any?) {}

actual typealias T = C

actual fun f(arg: Int) = arg
actual var p: String = "p"

fun box(): String {
    val c = C(42).toString()
    if (c != "C(arg=42)")
        return "c is wrongly $c"

    val cIsT = C(42) is T
    if (!cIsT)
        return "C(42) is wrongly not T"

    val f = f(1)
    if (f != 1)
        return "f is wrongly $f"

    p = "h"
    if (p != "h")
        return "p is wrongly $p"

    return "OK"
}
