package top.zient.haproxyreduce.common

/**
 * 运行时三元开关组合
 * 仅作数据容器，无业务逻辑
 */
data class ProxyMode(
    val disableWhitelist: Boolean = false,   // true=黑名单模式
    val softWhitelist: Boolean = true,       // true=名单外也允许连
    val autoProtocolComplete: Boolean = true // true=自动嗅探 PROXY 头
) {
    /* 方便 Java 侧调用 */
    fun isBlacklist(): Boolean = disableWhitelist
    fun isSoft(): Boolean = !disableWhitelist && softWhitelist
    fun isAuto(): Boolean = autoProtocolComplete
}
