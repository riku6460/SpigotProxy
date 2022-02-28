package nl.thijsalders.spigotproxy;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import nl.thijsalders.spigotproxy.netty.NettyChannelInitializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

public class SpigotProxy extends JavaPlugin {
    public void onEnable() {
        Mapping mapping = null;
        try {
            mapping = new Mapping();
        } catch (FileNotFoundException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }

        String version = getServer().getClass().getPackage().getName().split("\\.")[3];
        final String channelFieldName = getChannelFieldName(version, mapping);
        if (channelFieldName == null) {
            getLogger().log(Level.SEVERE, "Unknown server version " + version + ", please see if there are any updates available");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        } else {
            getLogger().info("Detected server version " + version);
        }

        try {
            getLogger().info("Injecting NettyHandler...");
            inject(channelFieldName, version, mapping);
            getLogger().info("Injection successful!");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Injection netty handler failed!", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void inject(final String channelFieldName, final String version, final Mapping mapping) throws Exception {
        Method serverGetHandle = getServer().getClass().getDeclaredMethod("getServer");
        Object minecraftServer = serverGetHandle.invoke(getServer());

        Method serverConnectionMethod = null;
        for (Method method : minecraftServer.getClass().getSuperclass().getDeclaredMethods()) {
            if (!method.getReturnType().getSimpleName().equals("ServerConnection")) {
                continue;
            }
            serverConnectionMethod = method;
            break;
        }

        Object serverConnection = serverConnectionMethod.invoke(minecraftServer);
        List<ChannelFuture> channelFutureList = ReflectionUtils.getPrivateField(serverConnection.getClass(), serverConnection, channelFieldName);

        for (ChannelFuture channelFuture : channelFutureList) {
            ChannelPipeline channelPipeline = channelFuture.channel().pipeline();
            ChannelHandler serverBootstrapAcceptor = channelPipeline.first();
            getLogger().info(serverBootstrapAcceptor.getClass().getName());
            ChannelInitializer<SocketChannel> oldChildHandler = ReflectionUtils.getPrivateField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, "childHandler");
            ReflectionUtils.setPrivateField(serverBootstrapAcceptor.getClass(), serverBootstrapAcceptor, "childHandler", new NettyChannelInitializer(oldChildHandler, minecraftServer.getClass().getPackage().getName(), version, mapping));
        }
    }

    private String getChannelFieldName(final String version, final Mapping mapping) {
        if (mapping != null) {
            return mapping.mapFieldName(
                    "net/minecraft/server/network/ServerConnectionListener",
                    "channels",
                    "Ljava/util/List;");
        }

        switch (version) {
            case "v1_16_R3":
            case "v1_16_R2":
            case "v1_16_R1":
            case "v1_15_R1":
                return "listeningChannels";
            case "v1_12_R1":
            case "v1_11_R1":
            case "v1_10_R1":
            case "v1_9_R2":
            case "v1_9_R1":
            case "v1_8_R2":
            case "v1_8_R3":
                return "g";
            case "v1_18_R2":
            case "v1_18_R1":
            case "v1_17_R1":
            case "v1_14_R1":
            case "v1_13_R1":
            case "v1_13_R2":
            case "v1_8_R1":
                return "f";
            case "v1_7_R4":
                return "e";
        }
        return null;
    }
}
