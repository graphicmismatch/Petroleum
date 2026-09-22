package Petroleum.Gui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private Button selectedRow;

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
                + "<h1>JSynth Documentation</h1>"
                + "<p class=\"summary\">Pick a node or a guide topic on the left to read about it.</p>"
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

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("flat-scroll");

        VBox panel = new VBox(Theme.GAP);
        Label title = new Label("Documentation");
        title.setFont(Font.font("System", FontWeight.BOLD, Theme.FONT_HEADING));
        title.setTextFill(Theme.TEXT);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        panel.getChildren().addAll(title, scroll);
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
        for (Docs.Article article : articles) {
            Button row = new Button(article.title());
            row.getStyleClass().add("icon-button");
            row.setMaxWidth(Double.MAX_VALUE);
            row.setOnAction(e -> renderArticle(article));
            rowsByTitle.put(article.title(), row);
            list.getChildren().add(row);
        }
    }
}
