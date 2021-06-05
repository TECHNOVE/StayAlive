package co.technove.plugins;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ImprovedKeepAlive extends JavaPlugin {

    public static final long KEEPALIVE_LIMIT = Long.getLong("paper.playerconnection.keepalive", 30) * 1000;
    public static final long MAX_KEEPALIVE_ID = 2048;

    private KeepAliveTask keepAliveTask;
    private KeepAlivePacketAdapter keepAlivePacketAdapter;
    private ProtocolManager protocolManager;
    private long packetInterval;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.packetInterval = getConfig().getLong("interval", 5000L);

        this.keepAliveTask = new KeepAliveTask(this);
        this.keepAliveTask.runTaskTimerAsynchronously(this, 20L * 5L, 20L * 5);

        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.keepAlivePacketAdapter = new KeepAlivePacketAdapter(this);
        this.protocolManager.addPacketListener(this.keepAlivePacketAdapter);
    }

    @Override
    public void onDisable() {
        this.protocolManager.removePacketListeners(this);
        this.keepAliveTask.cancel();
        this.keepAlivePacketAdapter.shutdown();
    }

    public KeepAliveTask getKeepAliveTask() {
        return keepAliveTask;
    }

    public long getPacketInterval() {
        return this.packetInterval;
    }
}
