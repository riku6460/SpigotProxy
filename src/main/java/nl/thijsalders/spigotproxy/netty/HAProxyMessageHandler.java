package nl.thijsalders.spigotproxy.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import nl.thijsalders.spigotproxy.UnknownVersionException;
import org.bukkit.Bukkit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

@ChannelHandler.Sharable
final class HAProxyMessageHandler extends ChannelInboundHandlerAdapter {
    private static final MethodHandle SET_SOCKET_ADDRESS;

    static {
        try {
            Class<?> minecraftServerClass = Bukkit.getServer().getClass().getMethod("getServer").getReturnType();

            Class<?> networkManager;
            try {
                networkManager = Class.forName("net.minecraft.network.NetworkManager");
            } catch (ClassNotFoundException e) {
                networkManager = Class.forName(minecraftServerClass.getPackage().getName() + ".NetworkManager");
            }

            Field socketAddress = null;
            for (Field field : networkManager.getFields()) {
                if (field.getType() == SocketAddress.class) {
                    socketAddress = field;
                    break;
                }
            }

            if (socketAddress == null) {
                throw new UnknownVersionException();
            }

            SET_SOCKET_ADDRESS = MethodHandles.lookup().unreflectSetter(socketAddress);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

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
                    : ctx.channel().remoteAddress();

            ChannelHandler handler = ctx.pipeline().get("packet_handler");
            try {
                SET_SOCKET_ADDRESS.invoke(handler, socketaddr);
            } catch (Throwable throwable) {
                throw new Exception(throwable);
            }
            ctx.pipeline().remove(this);
        } else {
            super.channelRead(ctx, msg);
        }
    }
}
