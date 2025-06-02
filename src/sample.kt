open class A {
    private fun hiddenFun() {}
    open fun visibleFun() {}
    private val hiddenAttr = 1
    val visibleAttr = 2
}
class B : A() {
    override fun visibleFun() {}
    val childAttr = 3
}
