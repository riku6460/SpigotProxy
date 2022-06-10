package nl.thijsalders.spigotproxy;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import nl.thijsalders.spigotproxy.netty.NettyChannelInitializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.logging.Level;

public class SpigotProxy extends JavaPlugin {
    private List<ChannelFuture> channelFutureList;

    public void onEnable() {
        try {
            Class<?> paperConfigClass = Class.forName("com.destroystokyo.paper.PaperConfig");
            paperConfigClass.getField("useProxyProtocol").set(null, Boolean.TRUE);
            getLogger().warning("In Paper 1.18.2 344+, this plugin is no longer required.");
            getLogger().warning("Set settings.proxy-protocol to true in paper.yml and remove this plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignored) {}

        String version = getServer().getClass().getPackage().getName().split("\\.")[3];
        getLogger().info("Detected server version " + version);

        try {
            getLogger().info("Injecting NettyHandler...");
            inject();
            getLogger().info("Injection successful!");
        } catch (Exception e) {
            if (e instanceof UnknownVersionException) {
                getLogger().severe("Unknown server version " + version + ", please see if there are any updates available");
            }
            getLogger().log(Level.SEVERE, "Injection netty handler failed!", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            uninject();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Uninjection netty handler failed!", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void inject() throws Exception {
        Object minecraftServer = getServer().getClass().getMethod("getServer").invoke(getServer());

        Object serverConnection = null;
        for (Method method : minecraftServer.getClass().getSuperclass().getMethods()) {
            if (method.getReturnType().getSimpleName().equals("ServerConnection")) {
                getLogger().fine("getServerConnection: " + method);
                serverConnection = method.invoke(minecraftServer);
                break;
            }
        }

        if (serverConnection == null) {
            throw new UnknownVersionException();
        }

        for (Field field : serverConnection.getClass().getDeclaredFields()) {
            if (field.getType() == List.class && field.getGenericType() instanceof ParameterizedType) {
                Type[] types = ((ParameterizedType) field.getGenericType()).getActualTypeArguments();
                if (types.length == 1 && types[0] == ChannelFuture.class) {
                    field.setAccessible(true);
                    getLogger().fine("channels: " + field);
                    this.channelFutureList = (List<ChannelFuture>) field.get(serverConnection);
                    break;
                }
            }
        }

        if (this.channelFutureList == null) {
            throw new UnknownVersionException();
        }

        for (ChannelFuture channelFuture : this.channelFutureList) {
            ChannelPipeline channelPipeline = channelFuture.channel().pipeline();
            ChannelHandler serverBootstrapAcceptor = channelPipeline.first();
            ChannelInitializer<SocketChannel> oldChildHandler = ReflectionUtils.getPrivateField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, "childHandler");
            ReflectionUtils.setPrivateField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, "childHandler", new NettyChannelInitializer(oldChildHandler, minecraftServer.getClass().getPackage().getName(), getLogger()));
        }
    }

    private void uninject() throws Exception {
        if (this.channelFutureList == null) {
            return;
        }

        for (ChannelFuture channelFuture : this.channelFutureList) {
            ChannelPipeline channelPipeline = channelFuture.channel().pipeline();
            ChannelHandler serverBootstrapAcceptor = channelPipeline.first();
            ChannelInitializer<SocketChannel> childHandler = ReflectionUtils.getPrivateField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, "childHandler");
            if (childHandler instanceof NettyChannelInitializer) {
                ChannelInitializer<SocketChannel> oldChildHandler = ((NettyChannelInitializer) childHandler).getOldChildHandler();
                ReflectionUtils.setPrivateField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, "childHandler", oldChildHandler);
            }
        }
    }
}
