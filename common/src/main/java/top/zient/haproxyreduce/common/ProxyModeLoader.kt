package top.zient.haproxyreduce.common

import org.yaml.snakeyaml.Yaml
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.notExists

object ProxyModeLoader {

    /* 运行时单例，启动时写入，之后只读 */
    @JvmStatic
    var mode: ProxyMode = ProxyMode()  // 默认值

    /**
     * 加载 config.yml；文件缺失或字段缺失均使用默认值
     */
    @Throws(IOException::class)
    fun load(path: Path): ProxyMode {
        if (path.notExists()) {
            return ProxyMode().also { mode = it }
        }

        Files.newInputStream(path).use { input ->
            val yaml = Yaml()
            val map: Map<String, Any> = yaml.load(input) ?: emptyMap()

            val disable = map["disable-whitelist"] as? Boolean ?: false
            val soft = map["soft-whitelist"] as? Boolean ?: true
            val auto = map["auto-protocol-complete"] as? Boolean ?: true

            return ProxyMode(disable, soft, auto).also { mode = it }
        }
    }
}
