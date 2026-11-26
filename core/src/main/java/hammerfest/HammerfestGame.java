package hammerfest;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import hammerfest.data.GameData;
import hammerfest.data.QuestDef;
import hammerfest.game.GameResult;
import hammerfest.game.Profile;
import hammerfest.quest.QuestBook;
import hammerfest.save.SaveData;
import hammerfest.save.SaveStore;
import hammerfest.screen.TitleScreen;
import hammerfest.ui.Ui;

import java.util.List;

/** Point d'entrée libGDX : charge données et sauvegarde, puis ouvre l'écran titre (jamais directement une partie). */
public class HammerfestGame extends Game {

    public Ui ui;
    public GameData data;
    public SaveData save;
    public QuestBook quests;
    public Texture menuBackground;
    /** Musique des menus (sfx/boss_v2.3.mp3) : jouée par les écrans de menu, coupée pendant une partie. */
    public final hammerfest.render.Audio menuAudio = new hammerfest.render.Audio();
    public static final String MENU_MUSIC = "boss_v2.3";
    /** Ouverture du port de jeu (UPnP sur le bureau), fournie par le lanceur de la plateforme. */
    public final hammerfest.net.PortService ports;

    public HammerfestGame() {
        this(hammerfest.net.PortService.NONE);
    }

    public HammerfestGame(hammerfest.net.PortService ports) {
        this.ports = ports;
    }
    private SaveStore store;
    /** Dossier de save.json fixé par le lanceur (à côté du jar exécutable) ; sinon le dossier de travail. */
    public static final String SAVE_DIR_PROPERTY = "hammerfest.saveDir";

    @Override
    public void create() {
        String dir = System.getProperty(SAVE_DIR_PROPERTY);
        store = new SaveStore(dir != null ? Gdx.files.absolute(dir + "/save.json") : Gdx.files.local("save.json"));
        save = store.load();
        hammerfest.input.Gamepads.settings = save.controls; // réglages des manettes partagés par les écrans
        hammerfest.render.Audio.settings = save.audio;
        data = GameData.load(Gdx.files.internal("data"), save.lang);
        com.badlogic.gdx.files.FileHandle uiTexts = Gdx.files.internal("data/lang/ui.json"); // textes du portage (menus…)
        hammerfest.ui.Tr.load(save.lang, uiTexts.exists() ? uiTexts.readString("UTF-8") : null);
        quests = new QuestBook(data.quests);
        quests.update(save);
        persist();

        ui = new Ui();
        menuBackground = new Texture(Gdx.files.internal("images/bgMenu.jpg"));
        menuBackground.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        setScreen(new TitleScreen(this));
    }

    /** Change la langue des textes du jeu (fr, en, es) : données rechargées, quêtes recalculées, sauvegarde. */
    public void setLanguage(String code) {
        save.lang = code;
        hammerfest.ui.Tr.setLanguage(code);
        data = GameData.load(Gdx.files.internal("data"), code);
        quests = new QuestBook(data.quests);
        quests.update(save);
        persist();
    }

    public void persist() {
        store.save(save);
    }

    /** Fin de partie : frigo, statistiques et quêtes mis à jour puis sauvegardés. */
    public List<QuestDef> finishGame(GameResult result) {
        List<QuestDef> completed = Profile.applyResult(save, quests, result);
        persist();
        return completed;
    }

    /**
     * Change d'écran en libérant le précédent. Différé à la frame suivante : on est souvent
     * appelé depuis un listener du Stage de l'écran courant, qu'on ne peut pas libérer en cours d'événement.
     */
    public void show(Screen next) {
        Gdx.app.postRunnable(() -> {
            Screen previous = getScreen();
            setScreen(next);
            if (previous != null && previous != next) previous.dispose();
        });
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        if (ui != null) ui.dispose();
        if (menuBackground != null) menuBackground.dispose();
        menuAudio.dispose();
        ports.close();
    }
}
