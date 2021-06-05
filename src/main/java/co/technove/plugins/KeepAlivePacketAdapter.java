package co.technove.plugins;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static net.kyori.adventure.text.Component.translatable;

public class KeepAlivePacketAdapter extends PacketAdapter implements Listener {

    private final ImprovedKeepAlive plugin;
    private final ProtocolManager protocolManager;
    private final KeepAliveTask task;

    // admittedly the executor isn't great, but it lets us middleman very efficiently
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, Long> playerPing = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastConfirm = new ConcurrentHashMap<>();

    public KeepAlivePacketAdapter(ImprovedKeepAlive plugin) {
        super(plugin, ListenerPriority.LOWEST, PacketType.Play.Server.KEEP_ALIVE, PacketType.Play.Client.KEEP_ALIVE);

        this.plugin = plugin;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.task = plugin.getKeepAliveTask();
    }

    public void shutdown() {
        this.executorService.shutdownNow();
    }

    // goal with packet sending:
    // - stop packets from the server, then return the server a fake packet at the exact timing of the ping
    @Override
    public void onPacketSending(PacketEvent event) {
        long id = event.getPacket().getLongs().read(0);
        if (id > ImprovedKeepAlive.MAX_KEEPALIVE_ID) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            if (System.currentTimeMillis() - lastConfirm.getOrDefault(player.getUniqueId(), 0L) < ImprovedKeepAlive.KEEPALIVE_LIMIT) {
                long actualPing = playerPing.getOrDefault(player.getUniqueId(), (long) player.getPing());
                executorService.schedule(() -> {
                    // send ourselves a fake packet to lock in the ping
                    if (player.isOnline()) {
                        PacketContainer packet = new PacketContainer(PacketType.Play.Client.KEEP_ALIVE);
                        packet.getLongs().write(0, id);
                        try {
                            this.protocolManager.recieveClientPacket(player, packet);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            this.plugin.getLogger().log(Level.WARNING, "Failed to receive keep alive packet", e);
                        }
                    }
                }, actualPing, TimeUnit.MILLISECONDS);
            }
        }
    }

    // goals with packet receiving:
    // - ignore the fake packet we send above
    // - calculate their actual ping based off the id of the packet sent back (kicking if it's timed out, or more likely being faked)
    // - mark that they've recently had a successful keepalive
    @Override
    public void onPacketReceiving(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        long id = packet.getLongs().read(0);

        if (id > ImprovedKeepAlive.MAX_KEEPALIVE_ID || id < 0) {
            // it's our fake packet, ignore
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();

        long latestPacket = this.task.getPacketId();
        if (latestPacket < id) { // wraparound
            latestPacket += ImprovedKeepAlive.MAX_KEEPALIVE_ID;
        }

        long millis = System.currentTimeMillis();
        long ping = (latestPacket - id) * this.plugin.getPacketInterval() + (millis - this.task.getLastPacketSendTime());
        if (ping > ImprovedKeepAlive.KEEPALIVE_LIMIT) {
            this.plugin.getLogger().log(Level.INFO, player.getName() + " was kicked due to keepalive timeout.");
            player.kick(translatable("disconnect.timeout"), PlayerKickEvent.Cause.TIMEOUT);
            return;
        }
        this.playerPing.put(player.getUniqueId(), ping);
        this.lastConfirm.put(player.getUniqueId(), millis);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.playerPing.remove(event.getPlayer().getUniqueId());
        this.lastConfirm.remove(event.getPlayer().getUniqueId());
    }
}
