package nl.thijsalders.spigotproxy.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;

import java.lang.reflect.Method;

public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static final HAProxyMessageHandler HAPROXY_MESSAGE_HANDLER = new HAProxyMessageHandler();
    private final ChannelInitializer<SocketChannel> oldChildHandler;
    private final Method oldChildHandlerMethod;

    public NettyChannelInitializer(ChannelInitializer<SocketChannel> oldChildHandler) throws Exception {
        this.oldChildHandler = oldChildHandler;
        this.oldChildHandlerMethod = this.oldChildHandler.getClass().getDeclaredMethod("initChannel", Channel.class);
        this.oldChildHandlerMethod.setAccessible(true);
    }

    public ChannelInitializer<SocketChannel> getOldChildHandler() {
        return this.oldChildHandler;
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        this.oldChildHandlerMethod.invoke(this.oldChildHandler, channel);

        channel.pipeline().addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
        channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", HAPROXY_MESSAGE_HANDLER);
    }
}
