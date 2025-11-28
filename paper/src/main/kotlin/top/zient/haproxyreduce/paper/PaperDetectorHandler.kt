package top.zient.haproxyreduce.paper

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.ProtocolDetectionResult
import io.netty.handler.codec.ProtocolDetectionState
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder
import io.netty.handler.codec.haproxy.HAProxyProtocolVersion
import org.slf4j.Logger
import top.zient.haproxyreduce.common.ProxyModeLoader
import top.zient.haproxyreduce.common.ProxyWhitelist

class PaperDetectorHandler(private val logger: Logger) : ByteToMessageDecoder() {

    init {
        setSingleDecode(true)
    }

    override fun decode(ctx: ChannelHandlerContext, inBuf: ByteBuf, out: MutableList<Any>) {
        inBuf.markReaderIndex()

        val remoteAddr = ctx.channel().remoteAddress()
        val mode   = ProxyModeLoader.mode
        val inList = ProxyWhitelist.check(remoteAddr)

        /* ---------- 路线决策 ---------- */
        when {
            mode.disableWhitelist && inList  -> {
                ctx.pipeline().remove(this)
                return
                }
            mode.disableWhitelist && !inList -> {
                // 黑名单外：允许连接，留到嗅探逻辑
                // 不 return，继续执行
                }
            !mode.disableWhitelist && !mode.softWhitelist && !inList -> {
                ctx.pipeline().remove(this)
                return
                }
        }

        /* ---------- 自动嗅探 ---------- */
        if (!inList || !mode.autoProtocolComplete) {
            ctx.pipeline().remove(this)
            inBuf.resetReaderIndex()
            return
        }

        /* ---------- 真正嗅探 ---------- */
        val result: ProtocolDetectionResult<HAProxyProtocolVersion> =
            HAProxyMessageDecoder.detectProtocol(inBuf)
        when (result.state()) {
            ProtocolDetectionState.NEEDS_MORE_DATA -> {
                inBuf.resetReaderIndex()
                return
            }
            ProtocolDetectionState.INVALID         -> {
                ctx.pipeline().remove(this)
                inBuf.resetReaderIndex()
                }
            ProtocolDetectionState.DETECTED -> {
                // 成功识别 → 替换为正式解码器
                replaceDecoder(ctx)
            }
        }
    }

    fun replaceDecoder(ctx: ChannelHandlerContext) {
        val pipeline: ChannelPipeline = ctx.pipeline()
        try {
            pipeline.replace(this, "haproxy-decoder", HAProxyMessageDecoder())
        } catch (ignore: IllegalArgumentException) {
            pipeline.remove(this)
        }
    }
}
