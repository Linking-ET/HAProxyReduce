package top.zient.haproxyreduce.velocity

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.ProtocolDetectionResult
import io.netty.handler.codec.ProtocolDetectionState
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion
import org.slf4j.Logger
import top.zient.haproxyreduce.common.ProxyModeLoader
import top.zient.haproxyreduce.common.ProxyWhitelist

class HAProxyDetectorHandler(private val logger: Logger) : ByteToMessageDecoder() {

    init {
        setSingleDecode(true)
    }

    override fun decode(ctx: ChannelHandlerContext, inBuf: ByteBuf, out: MutableList<Any>) {
        val remoteAddr = ctx.channel().remoteAddress()
        val mode   = ProxyModeLoader.mode
        val inList = ProxyWhitelist.check(remoteAddr)

        /* ---------- 路线决策 ---------- */
        when {
                mode.disableWhitelist && inList  -> {   // 黑名单内强制原始
                        ctx.pipeline().remove(this)
                        return
                    }
                mode.disableWhitelist && !inList -> {
                    // 黑名单外：允许通过，留到后面嗅探逻辑
                    // 不 return，继续走
                    }
                !mode.disableWhitelist && !mode.softWhitelist && !inList -> { // 白名单硬模式
                        ctx.pipeline().remove(this)
                        return
                    }
            }

        /* ---------- 自动嗅探 ---------- */
        if (!inList || !mode.autoProtocolComplete) {
            // 名单外 || 自动嗅探关闭 → 强制要求带头
            ctx.pipeline().remove(this)
            inBuf.resetReaderIndex()
            return
        }

        /* ---------- 真正嗅探 ---------- */
        val result: ProtocolDetectionResult<HAProxyProtocolVersion> =
            HAProxyMessageDecoder.detectProtocol(inBuf)
        when (result.state()) {
            ProtocolDetectionState.NEEDS_MORE_DATA -> return
            ProtocolDetectionState.INVALID         -> {
                // 没带 HAProxy 头 → 当原始 TCP
                ctx.pipeline().remove(this)
                inBuf.resetReaderIndex()
            }
            ProtocolDetectionState.DETECTED        -> {
                // 成功识别 → 替换为正式解码器
                ctx.pipeline().replace(this, "haproxy-decoder", HAProxyMessageDecoder())
            }
        }
    }
}
