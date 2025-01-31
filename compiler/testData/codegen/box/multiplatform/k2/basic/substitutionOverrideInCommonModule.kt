// LANGUAGE: +MultiPlatformProjects
// IGNORE_BACKEND_K1: JVM, JVM_IR, JS, JS_IR, JS_IR_ES6, WASM
// IGNORE_NATIVE_K1: mode=ONE_STAGE_MULTI_MODULE
// ISSUE: KT-58229
// WITH_STDLIB

// MODULE: common
// FILE: common.kt

expect class Expect {
    fun o(): String
    val k: String
}

interface Base<E> {
    fun foo(x: Expect): String = x.o()
    val Expect.bar: String get() = this.k
}

interface Derived : Base<Any?>

// MODULE: platform()()(common)
// FILE: platform.kt

class Impl : Derived

class ActualTarget {
    fun o(): String = "O"
    val k: String = "K"
}

actual typealias Expect = ActualTarget

fun test(x: Derived): String {
    val actual = ActualTarget()
    return x.foo(actual) + with(x) { actual.k }
}

fun box(): String {
    return test(Impl())
}
