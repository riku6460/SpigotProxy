package nl.thijsalders.spigotproxy.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

public final class NettyChannelInitializer extends ChannelInitializer<Channel> {
    private static final HAProxyMessageHandler HAPROXY_MESSAGE_HANDLER = new HAProxyMessageHandler();
    private static final MethodHandle INIT_CHANNEL;
    private final ChannelInitializer<?> oldChildHandler;

    static {
        try {
            Method initChannelMethod = ChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
            initChannelMethod.setAccessible(true);
            INIT_CHANNEL = MethodHandles.lookup().unreflect(initChannelMethod);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to find initChannel method in ChannelInitializer", e);
        }
    }

    public NettyChannelInitializer(ChannelInitializer<?> oldChildHandler) {
        this.oldChildHandler = oldChildHandler;
    }

    public ChannelInitializer<?> getOldChildHandler() {
        return this.oldChildHandler;
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        try {
            INIT_CHANNEL.invokeExact(this.oldChildHandler, channel);
        } catch (Throwable throwable) {
            throw new Exception(throwable);
        }

        channel.pipeline().addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
        channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", HAPROXY_MESSAGE_HANDLER);
    }
}
