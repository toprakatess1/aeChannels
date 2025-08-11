package me.toprak.aechannels;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AEChannels extends JavaPlugin implements Listener {

    private Map<String, ChatChannel> chatChannels = new HashMap<>();
    private Map<String, String> groupPrefixes = new HashMap<>();
    private String localPrefix;
    private String regionPrefix;
    private List<String> allowedRegions;
    private String regionCommand;
    private boolean useLpcPrefix;

    private LuckPerms luckPermsApi;
    private boolean placeholderApiEnabled = false;

    // Mention config ayarları
    private boolean mentionEnabled;
    private String mentionMessage;
    private boolean mentionSoundEnabled;
    private String mentionSoundName;
    private float mentionSoundVolume;
    private float mentionSoundPitch;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("aeChannels enabled!");

        try {
            luckPermsApi = LuckPermsProvider.get();
            getLogger().info("LuckPerms API hooked successfully!");
        } catch (IllegalStateException e) {
            luckPermsApi = null;
            getLogger().warning("LuckPerms API not found, LPC prefix support disabled!");
        }

        placeholderApiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (placeholderApiEnabled) {
            getLogger().info("PlaceholderAPI detected and enabled!");
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholder support disabled.");
        }
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

        useLpcPrefix = getConfig().getBoolean("chatChannels.use-lpc-prefix", false);

        ConfigurationSection groupSec = getConfig().getConfigurationSection("chatChannels.groupPrefixes");
        if (groupSec != null) {
            for (String key : groupSec.getKeys(false)) {
                groupPrefixes.put(key.toLowerCase(), groupSec.getString(key));
            }
        }

        // Mention config ayarlarını oku
        mentionEnabled = getConfig().getBoolean("mention.enabled", false);
        mentionMessage = getConfig().getString("mention.message", "&e@{player} seni etiketledi!");
        mentionSoundEnabled = getConfig().getBoolean("mention.sound.enabled", false);
        mentionSoundName = getConfig().getString("mention.sound.name", "ENTITY_PLAYER_LEVELUP");
        mentionSoundVolume = (float) getConfig().getDouble("mention.sound.volume", 1.0);
        mentionSoundPitch = (float) getConfig().getDouble("mention.sound.pitch", 1.0);
    }

    private String getPrimaryGroup(Player player) {
        if (luckPermsApi == null) return "default";
        User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        String primaryGroup = user.getPrimaryGroup();
        if (primaryGroup == null) return "default";
        return primaryGroup.toLowerCase();
    }

    private String colorize(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String formatPrefix(Player player, String channelPrefix) {
        String prefix = channelPrefix;

        if (useLpcPrefix && luckPermsApi != null) {
            User user = luckPermsApi.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String lpcPrefix = user.getCachedData().getMetaData().getPrefix();
                if (lpcPrefix != null && !lpcPrefix.isEmpty()) {
                    prefix += " " + lpcPrefix.trim();
                }
            }
        }

        String group = getPrimaryGroup(player);
        String groupPrefix = groupPrefixes.getOrDefault(group, groupPrefixes.getOrDefault("default", ""));
        prefix += colorize(groupPrefix);

        return prefix;
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

        // Mention kontrolü
        if (mentionEnabled) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String mentionTag = "@" + p.getName().toLowerCase();
                if (message.toLowerCase().contains(mentionTag)) {
                    // Mesajı placeholder ile işle
                    String rawMentionMsg = mentionMessage.replace("{player}", sender.getName());
                    String mentionMsg = placeholderApiEnabled ? PlaceholderAPI.setPlaceholders(p, rawMentionMsg) : ChatColor.translateAlternateColorCodes('&', rawMentionMsg);

                    p.sendMessage(mentionMsg);

                    if (mentionSoundEnabled) {
                        try {
                            Sound sound = Sound.valueOf(mentionSoundName.toUpperCase());
                            p.playSound(p.getLocation(), sound, mentionSoundVolume, mentionSoundPitch);
                        } catch (IllegalArgumentException ex) {
                            getLogger().warning("Mention sound is invalid: " + mentionSoundName);
                        }
                    }
                }
            }
        }

        if (channelOpt.isPresent()) {
            ChatChannel channel = channelOpt.get();
            String msgWithoutSymbol = message.substring(channel.symbol.length()).trim();

            String prefix = formatPrefix(sender, channel.prefix);
            String rawFormatted = prefix + " " + sender.getName() + ": " + msgWithoutSymbol;

            String formatted = placeholderApiEnabled ? PlaceholderAPI.setPlaceholders(sender, rawFormatted) : rawFormatted;

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
                String prefix = formatPrefix(sender, regionPrefix);
                String rawFormatted = prefix + " " + sender.getName() + ": " + message;
                Set<Player> recipients = new HashSet<>();

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
                    String formatted = placeholderApiEnabled ? PlaceholderAPI.setPlaceholders(sender, rawFormatted) : rawFormatted;
                    recipients.forEach(p -> p.sendMessage(formatted));
                    return;
                }
            }

            // Bölge yoksa local chat
            String prefix = formatPrefix(sender, localPrefix);
            String rawFormatted = prefix + " " + sender.getName() + ": " + message;
            String formatted = placeholderApiEnabled ? PlaceholderAPI.setPlaceholders(sender, rawFormatted) : rawFormatted;
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
