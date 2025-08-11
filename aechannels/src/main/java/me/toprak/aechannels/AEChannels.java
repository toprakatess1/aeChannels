package me.toprak.aechannels;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AEChannels extends JavaPlugin implements Listener {

    private Map<String, ChatChannel> chatChannels = new HashMap<>();
    private String localPrefix;
    private String regionPrefix;
    private List<String> allowedRegions;
    private String regionCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("aeChannels enabled!");
    }

    private void loadConfig() {
        chatChannels.clear();
        String gSymbol = getConfig().getString("chatChannels.global.symbol", "!");
        String gPrefix = getConfig().getString("chatChannels.global.prefix", "[GLOBAL]");
        chatChannels.put(gSymbol, new ChatChannel(gSymbol, gPrefix));

        String tSymbol = getConfig().getString("chatChannels.trade.symbol", ".");
        String tPrefix = getConfig().getString("chatChannels.trade.prefix", "[TİCARET]");
        chatChannels.put(tSymbol, new ChatChannel(tSymbol, tPrefix));

        localPrefix = getConfig().getString("chatChannels.local.prefix", "[LOCAL]");
        regionPrefix = getConfig().getString("chatChannels.region.prefix", "[REGION]");

        allowedRegions = getConfig().getStringList("chatChannels.region.allowed-regions");
        regionCommand = getConfig().getString("chatChannels.region.region-command", "");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player sender = event.getPlayer();
        String message = event.getMessage();

        Optional<ChatChannel> channelOpt = chatChannels.keySet().stream()
                .filter(message::startsWith)
                .map(chatChannels::get)
                .findFirst();

        event.setCancelled(true);

        if (channelOpt.isPresent()) {
            ChatChannel channel = channelOpt.get();
            String msgWithoutSymbol = message.substring(channel.symbol.length()).trim();
            String formatted = channel.prefix + " " + sender.getName() + ": " + msgWithoutSymbol;

            if (channel.symbol.equals("!")) {
                Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(formatted));
            } else if (channel.symbol.equals(".")) {
                sender.getWorld().getPlayers().forEach(p -> p.sendMessage(formatted));
            }
        } else {
            WorldGuard wg = WorldGuard.getInstance();
            RegionContainer regionContainer = wg.getPlatform().getRegionContainer();
            RegionQuery query = regionContainer.createQuery();

            Location senderPos = BukkitAdapter.adapt(sender.getLocation());
            ApplicableRegionSet regions = query.getApplicableRegions(senderPos);

            Set<ProtectedRegion> activeRegions = new HashSet<>();
            for (ProtectedRegion region : regions) {
                if (allowedRegions.contains(region.getId())) {
                    activeRegions.add(region);
                }
            }

            if (!activeRegions.isEmpty()) {
                String formatted = regionPrefix + " " + sender.getName() + ": " + message;
                Set<Player> recipients = new HashSet<>();

                // Konsol komutunu çalıştır
                if (!regionCommand.isEmpty()) {
                    String cmd = regionCommand.replace("{player}", sender.getName());
                    Bukkit.getScheduler().runTask(this, () -> getServer().dispatchCommand(getServer().getConsoleSender(), cmd));
                }

                for (ProtectedRegion region : activeRegions) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        Location pPos = BukkitAdapter.adapt(p.getLocation());
                        if (region.contains(pPos.getBlockX(), pPos.getBlockY(), pPos.getBlockZ())) {
                            recipients.add(p);
                        }
                    }
                }

                if (!recipients.isEmpty()) {
                    recipients.forEach(p -> p.sendMessage(formatted));
                    return;
                }
            }

            // Bölge yoksa local chat
            String formatted = localPrefix + " " + sender.getName() + ": " + message;
            sender.getWorld().getPlayers().forEach(p -> p.sendMessage(formatted));
        }
    }

    private static class ChatChannel {
        String symbol;
        String prefix;

        ChatChannel(String symbol, String prefix) {
            this.symbol = symbol;
            this.prefix = prefix;
        }
    }
}
