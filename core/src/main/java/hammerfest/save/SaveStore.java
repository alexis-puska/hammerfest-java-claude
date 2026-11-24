package hammerfest.save;

import com.badlogic.gdx.files.FileHandle;
import hammerfest.json.Jsons;

/** Persistance de {@link SaveData} dans save.json (Gdx.files.local), via Jackson. */
public class SaveStore {

    private final FileHandle file;

    public SaveStore(FileHandle file) {
        this.file = file;
    }

    public static SaveData parse(String json) {
        return Jsons.read(json, SaveData.class).sanitize();
    }

    public static String format(SaveData data) {
        return Jsons.write(data);
    }

    /** Charge la sauvegarde, ou une sauvegarde neuve si absente. Un fichier illisible est mis de côté. */
    public SaveData load() {
        if (!file.exists()) return new SaveData();
        try {
            return parse(file.readString("UTF-8"));
        } catch (RuntimeException e) {
            FileHandle backup = file.sibling(file.name() + ".corrupt");
            file.copyTo(backup);
            System.err.println("save.json illisible, copie dans " + backup.path() + " : " + e.getMessage());
            return new SaveData();
        }
    }

    /** Écriture atomique : fichier temporaire puis remplacement. */
    public void save(SaveData data) {
        FileHandle tmp = file.sibling(file.name() + ".tmp");
        tmp.writeString(format(data), false, "UTF-8");
        tmp.moveTo(file);
    }
}
