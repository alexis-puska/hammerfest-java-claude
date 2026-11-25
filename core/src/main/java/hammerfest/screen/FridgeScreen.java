package hammerfest.screen;

import hammerfest.ui.Tr;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import hammerfest.HammerfestGame;
import hammerfest.data.ItemDef;
import hammerfest.data.ItemFamily;
import hammerfest.render.ItemIcon;
import hammerfest.render.Sprites;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Frigo : objets ramassés (icône du jeu, nom traduit, quantité), filtre par famille, tri, objets non trouvés en silhouette. */
public class FridgeScreen extends MenuScreen {

    private final SelectBox<String> familyBox;
    private final SelectBox<String> sortBox;
    private final CheckBox showMissing;
    private final Table grid = new Table();
    private final Label summary;
    private final List<ItemFamily> familyChoices = new ArrayList<>();
    /** Clips des objets (bibliothèque anims) ; null si les sprites ne sont pas exportés. */
    private final Sprites sprites;

    public FridgeScreen(HammerfestGame game) {
        super(game);
        sprites = loadSprites();
        Table panel = panel(Tr.t("Frigo"));

        Array<String> famNames = new Array<>();
        famNames.add(Tr.t("Toutes les familles"));
        for (ItemFamily f : game.data.families) {
            familyChoices.add(f);
            famNames.add(game.data.lang.get(f.nameKey) + " (" + f.id + ")");
        }
        familyBox = new SelectBox<>(skin);
        familyBox.setItems(famNames);
        familyBox.setMaxListCount(14);
        sortBox = new SelectBox<>(skin);
        sortBox.setItems(Tr.t("Tri par id"), Tr.t("Tri par quantité"));
        showMissing = new CheckBox(Tr.t(" Objets non trouvés"), skin);
        showMissing.getImageCell().padRight(6);

        Table filters = new Table();
        filters.add(familyBox).width(270).padRight(8);
        filters.add(sortBox).width(140).padRight(8);
        filters.add(showMissing);
        panel.add(filters).left().padBottom(8).row();

        summary = label("", "dim");
        panel.add(summary).padBottom(6).row();

        grid.top().left();
        ScrollPane scroll = new ScrollPane(grid, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        panel.add(scroll).expand().fill().row();
        addBackButton(panel);

        ChangeListener refresh = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                refresh();
            }
        };
        familyBox.addListener(refresh);
        sortBox.addListener(refresh);
        showMissing.addListener(refresh);
        refresh();
        stage.setScrollFocus(scroll);
    }

    private static final float ICON = 40;

    private static Sprites loadSprites() {
        try {
            Sprites s = new Sprites("anims");
            return s.available() ? s : null;
        } catch (RuntimeException e) {
            return null; // frigo sans icônes plutôt qu'un écran qui plante
        }
    }

    private void refresh() {
        int sel = familyBox.getSelectedIndex();
        List<ItemDef> items = new ArrayList<>();
        for (ItemFamily f : game.data.families) {
            if (sel > 0 && familyChoices.get(sel - 1) != f) continue;
            for (ItemDef i : f.items) items.add(i);
        }
        if (sortBox.getSelectedIndex() == 1) {
            items.sort(Comparator.comparingInt((ItemDef i) -> -game.save.fridgeCount(i.id)).thenComparingInt(i -> i.id));
        } else {
            items.sort(Comparator.comparingInt(i -> i.id));
        }

        grid.clearChildren();
        int found = 0, shown = 0;
        for (ItemDef i : items) {
            int qty = game.save.fridgeCount(i.id);
            if (qty > 0) found++;
            if (qty == 0 && !showMissing.isChecked()) continue;
            shown++;
            String name = qty > 0 ? game.data.itemName(i.id) : "? ? ?";
            grid.add(new ItemIcon(sprites, i.id, qty == 0, ICON)).size(ICON).padRight(8).padBottom(2);
            grid.add(label("#" + i.id, "dim")).width(60).left();
            grid.add(label(name, qty > 0 ? "default" : "dim")).width(330).left();
            grid.add(label(qty > 0 ? "x" + qty : "", "accent")).width(70).right().row();
        }
        if (shown == 0) grid.add(label(Tr.t("Le frigo est vide pour l'instant : ramassez des objets en jeu !"), "dim")).colspan(4).left();
        summary.setText(found + Tr.t(" objet(s) différent(s) trouvé(s) sur ") + items.size());
    }

    @Override
    public void dispose() {
        super.dispose();
        if (sprites != null) sprites.dispose();
    }
}
