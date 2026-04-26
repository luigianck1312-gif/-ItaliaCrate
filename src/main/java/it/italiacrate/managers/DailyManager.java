package it.italiacrate.managers;

import it.italiacrate.ItaliaCrate;
import it.italiacrate.models.CrateRarity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DailyManager {

    private final ItaliaCrate plugin;
    private final Map<UUID, Integer> streaks = new HashMap<>();       // giorni consecutivi
    private final Map<UUID, String> lastClaim = new HashMap<>();      // ultima data claim

    // Schema ricompense giornaliere (0-based index, si ripete)
    private static final CrateRarity[] DAILY_SCHEDULE = {
        CrateRarity.COMUNE,       // Giorno 1
        CrateRarity.COMUNE,       // Giorno 2
        CrateRarity.COMUNE,       // Giorno 3
        CrateRarity.EPICA,        // Giorno 4
        CrateRarity.COMUNE,       // Giorno 5
        CrateRarity.COMUNE,       // Giorno 6
        CrateRarity.EPICA,        // Giorno 7
        CrateRarity.COMUNE,       // Giorno 8
        CrateRarity.EPICA,        // Giorno 9
        CrateRarity.LEGGENDARIA,  // Giorno 10
        CrateRarity.COMUNE,       // Giorno 11
        CrateRarity.COMUNE,       // Giorno 12
        CrateRarity.EPICA,        // Giorno 13
        CrateRarity.LEGGENDARIA,  // Giorno 14
        CrateRarity.COMUNE,       // Giorno 15
        CrateRarity.EPICA,        // Giorno 16
        CrateRarity.LEGGENDARIA,  // Giorno 17
        CrateRarity.COMUNE,       // Giorno 18
        CrateRarity.EPICA,        // Giorno 19
        CrateRarity.LEGGENDARIA,  // Giorno 20
        CrateRarity.MITICA,       // Giorno 21
    };

    public DailyManager(ItaliaCrate plugin) {
        this.plugin = plugin;
        loadData();
    }

    public boolean canClaim(Player player) {
        String today = LocalDate.now().toString();
        String last = lastClaim.get(player.getUniqueId());
        return !today.equals(last);
    }

    public CrateRarity getTodayReward(Player player) {
        int streak = streaks.getOrDefault(player.getUniqueId(), 0);
        return DAILY_SCHEDULE[streak % DAILY_SCHEDULE.length];
    }

    public int getStreak(Player player) {
        return streaks.getOrDefault(player.getUniqueId(), 0);
    }

    public int getNextDayIndex(Player player) {
        int streak = streaks.getOrDefault(player.getUniqueId(), 0);
        return (streak % DAILY_SCHEDULE.length) + 1;
    }

    public CrateRarity claim(Player player) {
        if (!canClaim(player)) return null;

        UUID uuid = player.getUniqueId();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        String last = lastClaim.get(uuid);

        // Reset streak se ha saltato un giorno
        if (last != null && !last.equals(yesterday)) {
            streaks.put(uuid, 0);
        }

        int currentStreak = streaks.getOrDefault(uuid, 0);
        CrateRarity reward = DAILY_SCHEDULE[currentStreak % DAILY_SCHEDULE.length];

        streaks.put(uuid, currentStreak + 1);
        lastClaim.put(uuid, today);
        saveData();

        return reward;
    }

    public CrateRarity[] getDailySchedule() { return DAILY_SCHEDULE; }

    private void loadData() {
        File file = new File(plugin.getDataFolder(), "daily.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                streaks.put(uuid, config.getInt(key + ".streak"));
                lastClaim.put(uuid, config.getString(key + ".lastClaim"));
            } catch (Exception ignored) {}
        }
    }

    public void saveData() {
        File file = new File(plugin.getDataFolder(), "daily.yml");
        YamlConfiguration config = new YamlConfiguration();
        for (UUID uuid : streaks.keySet()) {
            config.set(uuid + ".streak", streaks.get(uuid));
            config.set(uuid + ".lastClaim", lastClaim.getOrDefault(uuid, ""));
        }
        try { config.save(file); } catch (Exception e) { e.printStackTrace(); }
    }
}
