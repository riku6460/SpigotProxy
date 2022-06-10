package nl.thijsalders.spigotproxy.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.haproxy.HAProxyMessageDecoder;
import nl.thijsalders.spigotproxy.UnknownVersionException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.logging.Logger;

public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {
    private final ChannelInitializer<SocketChannel> oldChildHandler;
    private final Method oldChildHandlerMethod;
    private final Field addr;

    public NettyChannelInitializer(ChannelInitializer<SocketChannel> oldChildHandler, String minecraftPackage, Logger logger) throws Exception {
        this.oldChildHandler = oldChildHandler;
        this.oldChildHandlerMethod = this.oldChildHandler.getClass().getDeclaredMethod("initChannel", Channel.class);
        this.oldChildHandlerMethod.setAccessible(true);

        Class<?> networkManager;
        try {
            networkManager = Class.forName("net.minecraft.network.NetworkManager");
        } catch (ClassNotFoundException e) {
            networkManager = Class.forName(minecraftPackage + ".NetworkManager");
        }

        Field addr = null;
        for (Field field : networkManager.getFields()) {
            if (field.getType() == SocketAddress.class) {
                logger.fine("socketAddress: " + field);
                addr = field;
                break;
            }
        }

        if (addr == null) {
            throw new UnknownVersionException();
        }

        this.addr = addr;
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        this.oldChildHandlerMethod.invoke(this.oldChildHandler, channel);

        channel.pipeline().addAfter("timeout", "haproxy-decoder", new HAProxyMessageDecoder());
        channel.pipeline().addAfter("haproxy-decoder", "haproxy-handler", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof HAProxyMessage) {
                    HAProxyMessage message = (HAProxyMessage) msg;
                    String realaddress = message.sourceAddress();
                    int realport = message.sourcePort();

                    // Use proxied client address from HAProxy PROXY header
                    // Or use channel address for HAProxy LOCAL header
                    // See: https://www.haproxy.org/download/2.4/doc/proxy-protocol.txt
                    SocketAddress socketaddr = (realaddress != null)
                            ? new InetSocketAddress(realaddress, realport)
                            : channel.remoteAddress();

                    ChannelHandler handler = channel.pipeline().get("packet_handler");
                    addr.set(handler, socketaddr);
                } else {
                    super.channelRead(ctx, msg);
                }
            }
        });
    }
}
