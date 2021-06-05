package co.technove.plugins;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

public class KeepAliveTask extends BukkitRunnable {

    private long lastPacketSendTime;
    private long packetId = 0;
    private final ImprovedKeepAlive plugin;
    private final ProtocolManager protocolManager;

    public KeepAliveTask(ImprovedKeepAlive plugin) {
        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    @Override
    public void run() {
        if (++this.packetId > ImprovedKeepAlive.MAX_KEEPALIVE_ID) {
            this.packetId = 0;
        }

        PacketContainer pingPacket = new PacketContainer(PacketType.Play.Server.KEEP_ALIVE);
        pingPacket.getLongs().write(0, this.packetId);
        this.lastPacketSendTime = System.currentTimeMillis();

        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.protocolManager.sendServerPacket(player, pingPacket);
            }
        } catch (InvocationTargetException e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to write keepalive", e);
        }
    }

    public long getLastPacketSendTime() {
        return lastPacketSendTime;
    }

    public long getPacketId() {
        return packetId;
    }

}
