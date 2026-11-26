package hammerfest.save;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Contenu de save.json (sérialisé par Jackson, cf. {@link hammerfest.json.Jsons}). */
public class SaveData {

    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    /** Nom saisi dans le menu Profil ; vide tant que non défini. */
    public String playerName = "";
    /** Identifiant stable et caché, distingue deux joueurs de même nom en multi. */
    public String playerId = UUID.randomUUID().toString();
    public String lang = "fr";

    /** Frigo : itemId → quantité. */
    public TreeMap<Integer, Integer> fridge = new TreeMap<>();
    public TreeSet<Integer> completedQuests = new TreeSet<>();
    /** Recalculé par QuestBook à partir des quêtes terminées, conservé pour lecture directe. */
    public TreeSet<Integer> unlockedFamilies = new TreeSet<>();
    public TreeSet<String> unlockedOptions = new TreeSet<>();
    public TreeSet<String> unlockedModes = new TreeSet<>();

    public Stats stats = new Stats();

    /** Derniers choix de l'écran Jouer. */
    public String lastMode = "adventure";
    public Set<String> lastOptions = new LinkedHashSet<>();

    /** Volumes et musique coupée (écran Options, touche M, en jeu et dans les menus). */
    public AudioSettings audio = new AudioSettings();

    /** Réglages des manettes (écran Commandes). */
    public ControlSettings controls = new ControlSettings();

    public static class Stats {
        public long bestScore;
        /** Niveau le plus profond atteint dans le puits (0..103). */
        public int maxLevel;
        public int gamesPlayed;
        public long itemsCollected;
        /** Meilleur temps du Time Attack (ms), 0 = aucun parcours terminé. */
        public long bestTime;
    }

    /** Remet d'aplomb une sauvegarde lue (champs absents ou invalides d'un ancien fichier). */
    public SaveData sanitize() {
        playerName = PlayerName.normalize(playerName);
        if (playerId == null || playerId.isEmpty()) playerId = UUID.randomUUID().toString();
        if (lang == null) lang = "fr";
        if (fridge == null) fridge = new TreeMap<>();
        fridge.values().removeIf(q -> q == null || q <= 0);
        if (completedQuests == null) completedQuests = new TreeSet<>();
        if (unlockedFamilies == null) unlockedFamilies = new TreeSet<>();
        if (unlockedOptions == null) unlockedOptions = new TreeSet<>();
        if (unlockedModes == null) unlockedModes = new TreeSet<>();
        if (stats == null) stats = new Stats();
        if (lastMode == null) lastMode = "adventure";
        if (lastOptions == null) lastOptions = new LinkedHashSet<>();
        if (audio == null) audio = new AudioSettings();
        audio.sanitize();
        if (lang == null || !java.util.List.of("fr", "en", "es").contains(lang)) lang = "fr";
        if (controls == null) controls = new ControlSettings();
        controls.sanitize();
        version = CURRENT_VERSION;
        return this;
    }

    public boolean hasPlayerName() {
        return PlayerName.isValid(playerName);
    }

    public int fridgeCount(int itemId) {
        return fridge.getOrDefault(itemId, 0);
    }

    public void addToFridge(int itemId, int qty) {
        if (qty <= 0) return;
        fridge.merge(itemId, qty, Integer::sum);
        stats.itemsCollected += qty;
    }
}
