package top.zient.haproxyreduce.common

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.notExists

class ProxyWhitelist private constructor(private val entries: List<CIDR>) {

    fun matches(address: InetAddress): Boolean =
        entries.any { it.contains(address) }

    val size: Int get() = entries.size

    companion object {
        @Volatile
        var ipList: ProxyWhitelist? = null   // ← 全局重命名
        private var lastWarning: InetAddress? = null

        /** 纯 IP 命中判断，模式逻辑由外部 Handler 自行组合 */
        fun check(address: SocketAddress): Boolean =
            ipList?.let {
                (address as? InetSocketAddress)?.address?.let { addr ->
                    it.matches(addr)
                } ?: false
            } ?: false

        fun getWarningFor(address: SocketAddress): String? {
            val inetAddr = (address as? InetSocketAddress)?.address ?: return null
            if (inetAddr != lastWarning) {
                lastWarning = inetAddr
                return "检测到来自 ${inetAddr.hostAddress} 的代理连接，但该地址不在白名单中（使用原始IP）"
            }
            return null
        }

        /** 移除魔法串，只扫 IP/CIDR */
        @Throws(IOException::class)
        fun load(path: Path): ProxyWhitelist {
            val entries = mutableListOf<CIDR>()
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
                reader.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
                    entries.addAll(CIDR.parse(trimmed))
                }
            }
            return ProxyWhitelist(entries)
        }

        private fun exportResource(target: Path, resourceName: String) {
            javaClass.getResourceAsStream("/$resourceName")?.use { input ->
                Files.createDirectories(target.parent)
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
            } ?: throw IOException("Classpath 中找不到 $resourceName")
        }

        @Throws(IOException::class)
        fun loadOrDefault(listPath: Path, configPath: Path): ProxyWhitelist {
            if (listPath.notExists()) exportResource(listPath, "list.conf")
            if (configPath.notExists() ||
                runCatching { ProxyModeLoader.load(configPath) }.isFailure
            ) {
                exportResource(configPath, "config.yml")
            }
            return load(listPath).also { ipList = it }   // ← 赋值重命名
        }
    }
}
