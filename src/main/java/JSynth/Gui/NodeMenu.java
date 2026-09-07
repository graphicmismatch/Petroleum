package JSynth.Gui;

import JSynth.Graph.NodeFactory;
import JSynth.Oscillators.OscillatorFactory;
import JSynth.Serialization.PatchCodec;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NodeMenu {
    private final List<NodeCatalog.Entry> entries;
    private ContextMenu activeMenu;

    public NodeMenu(NodeFactory nodeFactory, OscillatorFactory oscillatorFactory, PatchCodec patchCodec) {
        this.entries = new NodeCatalog(nodeFactory, oscillatorFactory, patchCodec).getEntries();
    }

    public void hide() {
        if (activeMenu != null) {
            activeMenu.hide();
            activeMenu = null;
        }
    }

    public void show(GraphEditor editor, double canvasX, double canvasY,
                      double screenX, double screenY, Window window) {
        show(editor, canvasX, canvasY, screenX, screenY, window, null);
    }

    public void show(GraphEditor editor, double canvasX, double canvasY,
                      double screenX, double screenY, Window window, PortHandle wireSource) {
        hide();
        ContextMenu menu = new ContextMenu();
        menu.setAutoHide(true);

        Map<String, List<NodeCatalog.Entry>> byCategory = new LinkedHashMap<>();
        for (String category : NodeCatalog.CATEGORY_ORDER) {
            byCategory.put(category, new ArrayList<>());
        }
        for (NodeCatalog.Entry entry : entries) {
            byCategory.computeIfAbsent(entry.category(), c -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<String, List<NodeCatalog.Entry>> categoryEntry : byCategory.entrySet()) {
            List<NodeCatalog.Entry> categoryEntries = categoryEntry.getValue();
            if (categoryEntries.isEmpty()) {
                continue;
            }
            Menu categoryMenu = new Menu(categoryEntry.getKey());
            populate(categoryMenu, categoryEntries, editor, canvasX, canvasY, window, wireSource);
            menu.getItems().add(categoryMenu);
        }

        activeMenu = menu;
        menu.show(editor.getCanvas(), screenX, screenY);
    }

    /**
     * Places each entry under nested {@code Menu}s matching its
     * {@link NodeCatalog.Entry#subPath()}, creating a submenu the first time
     * a given path prefix is seen and reusing it for later entries that
     * share it - so a category's tree can go arbitrarily deep (submenus of
     * submenus) purely from the path each entry declares, with no per-case
     * menu-building logic here.
     */
    private void populate(Menu categoryMenu, List<NodeCatalog.Entry> categoryEntries, GraphEditor editor,
                           double x, double y, Window window, PortHandle wireSource) {
        Map<List<String>, Menu> submenus = new LinkedHashMap<>();
        for (NodeCatalog.Entry entry : categoryEntries) {
            Menu parent = categoryMenu;
            List<String> path = new ArrayList<>();
            for (String segment : entry.subPath()) {
                path.add(segment);
                List<String> key = List.copyOf(path);
                Menu submenu = submenus.get(key);
                if (submenu == null) {
                    submenu = new Menu(segment);
                    parent.getItems().add(submenu);
                    submenus.put(key, submenu);
                }
                parent = submenu;
            }
            parent.getItems().add(buildItem(editor, entry, x, y, window, wireSource));
        }
    }

    private MenuItem buildItem(GraphEditor editor, NodeCatalog.Entry entry, double x, double y,
                               Window window, PortHandle wireSource) {
        MenuItem item = new MenuItem(displayLabel(entry));
        item.setOnAction(e -> {
            try {
                entry.action().apply(window)
                        .ifPresent(node -> editor.addNodeWiredFrom(node, x, y, wireSource));
            } catch (RuntimeException ex) {
                Dialogs.showError("Could not create node", ex.getMessage());
            }
        });
        return item;
    }

    /**
     * Drops a redundant leading "&lt;submenu name&gt; " from the label once
     * the submenu itself already says it (e.g. "Biquad Lowpass" reads as
     * plain "Lowpass" under Filters &gt; Biquad) - generalizes what used to
     * be a Biquad-only special case to any submenu whose entries share its
     * name as a prefix, with no per-menu code.
     */
    private static String displayLabel(NodeCatalog.Entry entry) {
        List<String> path = entry.subPath();
        if (path.isEmpty()) {
            return entry.label();
        }
        String prefix = path.get(path.size() - 1) + " ";
        return entry.label().startsWith(prefix) ? entry.label().substring(prefix.length()) : entry.label();
    }
}
