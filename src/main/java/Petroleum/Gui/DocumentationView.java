package Petroleum.Gui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class DocumentationView {
    private static final String PAGE_STYLE = buildPageStyle();

    private static String buildPageStyle() {
        return "<style>"
                + "body { background: " + Theme.hex(Theme.CANVAS) + "; color: " + Theme.hex(Theme.TEXT)
                + "; font-family: -apple-system, Segoe UI, sans-serif; margin: 0; padding: 16px; }"
                + "h1 { color: " + Theme.hex(Theme.TEXT_STRONG) + "; font-size: " + (int) Theme.FONT_TITLE
                + "px; margin: 0 0 4px 0; }"
                + ".summary { color: " + Theme.hex(Theme.TEXT_DIM) + "; margin: 0 0 16px 0; }"
                + "p { line-height: 1.5; }"
                + "h2 { color: " + Theme.hex(Theme.ACCENT) + "; font-size: " + (int) Theme.FONT_BODY
                + "px; text-transform: uppercase; letter-spacing: 0.05em; margin: 20px 0 8px 0; }"
                + "ul { margin: 0; padding-left: 20px; }"
                + "li { line-height: 1.6; }"
                + "li b { color: " + Theme.hex(Theme.TEXT_STRONG) + "; }"
                + "pre { background: " + Theme.hex(Theme.WAVE_BACKGROUND) + "; border: 1px solid "
                + Theme.hex(Theme.BORDER) + "; border-radius: 6px; padding: 10px 12px; color: "
                + Theme.hex(Theme.ACCENT) + "; font-family: Consolas, Monaco, monospace; font-size: "
                + (int) Theme.FONT_BODY + "px; white-space: pre-wrap; overflow-x: auto; }"
                + "</style>";
    }

    private final BorderPane root = new BorderPane();
    private final WebView articleView = new WebView();
    private final Map<String, Button> rowsByTitle = new LinkedHashMap<>();
    private final List<Section> sections = new ArrayList<>();
    private final TextField searchField = new TextField();
    private final Label noMatches = new Label("No matching articles.");
    private Button selectedRow;

    private record Row(Button button, Docs.Article article, String text) {
    }

    private record Section(Label header, List<Row> rows) {
    }

    DocumentationView() {
        root.getStyleClass().add("card");
        root.setLeft(buildIndex());
        root.setCenter(articleView);
        showWelcome();
    }

    Parent getRoot() {
        return root;
    }

    void showWelcome() {
        selectRow(null);
        articleView.getEngine().loadContent(PAGE_STYLE
                + "<h1>Petroleum Documentation</h1>"
                + "<p class=\"summary\">Pick a node or a guide topic on the left to read about it,"
                + " or type in the search box to narrow the list.</p>"
                + "<p>Every node placed in the graph editor also has its own \"?\" button in its header, "
                + "which jumps straight to that node's article here.</p>");
    }

    void showNode(String key) {
        showNode(key, "");
    }

    void showNode(String key, String extraNotes) {
        Docs.nodeArticle(key).ifPresentOrElse(article -> renderArticle(article, extraNotes), this::showWelcome);
    }

    private void renderArticle(Docs.Article article) {
        renderArticle(article, "");
    }

    private void renderArticle(Docs.Article article, String extraNotes) {
        selectRow(article.title());
        StringBuilder html = new StringBuilder(PAGE_STYLE);
        html.append("<h1>").append(escape(article.title())).append("</h1>");
        html.append("<p class=\"summary\">").append(escape(article.summary())).append("</p>");
        for (String paragraph : article.body().split("\\n\\s*\\n")) {
            html.append("<p>").append(escape(paragraph.trim())).append("</p>");
        }
        if (extraNotes != null && !extraNotes.isBlank()) {
            html.append("<h2>Notes</h2>");
            for (String paragraph : extraNotes.split("\\n\\s*\\n")) {
                html.append("<p>").append(escape(paragraph.trim())).append("</p>");
            }
        }
        if (!article.inputs().isEmpty()) {
            html.append("<h2>Inputs</h2><ul>");
            for (Docs.PortInfo input : article.inputs()) {
                html.append("<li><b>").append(escape(input.name())).append("</b> - ")
                        .append(escape(input.description())).append("</li>");
            }
            html.append("</ul>");
        }
        if (!article.output().isBlank()) {
            html.append("<h2>Output</h2><p>").append(escape(article.output())).append("</p>");
        }
        if (!article.example().isBlank()) {
            html.append("<h2>Example</h2>");
            html.append("<pre>").append(escape(article.example())).append("</pre>");
        }
        articleView.getEngine().loadContent(html.toString());
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void selectRow(String title) {
        if (selectedRow != null) {
            selectedRow.getStyleClass().remove("macro-active");
        }
        selectedRow = title == null ? null : rowsByTitle.get(title);
        if (selectedRow != null) {
            selectedRow.getStyleClass().add("macro-active");
        }
    }

    private VBox buildIndex() {
        VBox list = new VBox(Theme.GAP);
        for (String category : NodeCatalog.CATEGORY_ORDER) {
            List<Docs.Article> inCategory = new ArrayList<>();
            for (Docs.Article article : Docs.nodeArticlesByCategory()) {
                if (article.category().equals(category)) {
                    inCategory.add(article);
                }
            }
            if (!inCategory.isEmpty()) {
                addSection(list, category, inCategory);
            }
        }
        addSection(list, Docs.GUIDE_CATEGORY, Docs.guideArticles());

        noMatches.setTextFill(Theme.TEXT_FAINT);
        noMatches.setVisible(false);
        noMatches.setManaged(false);
        list.getChildren().add(noMatches);

        searchField.setPromptText("Search documentation");
        searchField.textProperty().addListener((obs, old, text) -> applyFilter(text));
        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                firstMatch().ifPresent(this::renderArticle);
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE && !searchField.getText().isEmpty()) {
                searchField.clear();
                e.consume();
            }
        });

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("flat-scroll");

        VBox panel = new VBox(Theme.GAP);
        Label title = new Label("Documentation");
        title.setFont(Font.font("System", FontWeight.BOLD, Theme.FONT_HEADING));
        title.setTextFill(Theme.TEXT);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        panel.getChildren().addAll(title, searchField, scroll);
        panel.setPadding(new Insets(Theme.PAD));
        panel.setPrefWidth(Theme.PANEL_WIDTH);
        panel.setMinWidth(Theme.PANEL_WIDTH);
        panel.getStyleClass().add("side-panel");
        return panel;
    }

    private void addSection(VBox list, String category, List<Docs.Article> articles) {
        Label header = new Label(category);
        header.getStyleClass().add("section-title");
        list.getChildren().add(header);
        List<Row> rows = new ArrayList<>();
        for (Docs.Article article : articles) {
            Button row = new Button(article.title());
            row.getStyleClass().add("icon-button");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setOnAction(e -> renderArticle(article));
            rowsByTitle.put(article.title(), row);
            list.getChildren().add(row);
            rows.add(new Row(row, article, searchText(category, article)));
        }
        sections.add(new Section(header, rows));
    }

    static String searchText(String category, Docs.Article article) {
        StringBuilder text = new StringBuilder()
                .append(category).append('\n')
                .append(article.title()).append('\n')
                .append(article.summary()).append('\n')
                .append(article.body()).append('\n')
                .append(article.output()).append('\n')
                .append(article.example());
        for (Docs.PortInfo input : article.inputs()) {
            text.append('\n').append(input.name()).append(' ').append(input.description());
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    static boolean matches(String text, String query) {
        for (String term : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!text.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private void applyFilter(String query) {
        boolean any = false;
        for (Section section : sections) {
            boolean sectionVisible = false;
            for (Row row : section.rows()) {
                boolean visible = matches(row.text(), query);
                show(row.button(), visible);
                sectionVisible |= visible;
            }
            show(section.header(), sectionVisible);
            any |= sectionVisible;
        }
        show(noMatches, !any);
    }

    private Optional<Docs.Article> firstMatch() {
        String query = searchField.getText().toLowerCase(Locale.ROOT).trim();
        Docs.Article first = null;
        for (Section section : sections) {
            for (Row row : section.rows()) {
                if (!row.button().isVisible()) {
                    continue;
                }
                if (row.article().title().toLowerCase(Locale.ROOT).contains(query)) {
                    return Optional.of(row.article());
                }
                if (first == null) {
                    first = row.article();
                }
            }
        }
        return Optional.ofNullable(first);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
