package com.qullamaggie.tradingsystem.notifications;

import com.qullamaggie.tradingsystem.notifications.EmailDocument.*;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Locale;

/**
 * Renders an EmailDocument as plain text and as HTML.
 *
 * The HTML deliberately looks like an ordinary email: Arial, bold headings
 * with a thin rule, label/value tables, no coloured boxes. All styling is
 * inline because many mail clients ignore style blocks.
 */
@Component
public class EmailRenderer {

    private static final String FONT = "font-family:Arial,Helvetica,sans-serif;";
    private static final String MUTED = "color:#555555;";

    public EmailContent render(EmailDocument document) {
        return new EmailContent(document.subject(), text(document), html(document));
    }

    // --- Vanlig text ---

    String text(EmailDocument document) {
        StringBuilder out = new StringBuilder();
        for (Section section : document.sections()) {
            out.append("━━ ").append(section.heading().toUpperCase(Locale.ROOT)).append(" ━━\n\n");
            for (Block block : section.blocks()) {
                switch (block) {
                    case Item item -> {
                        out.append(item.title().toUpperCase(Locale.ROOT));
                        if (item.tag() != null) {
                            out.append(" (").append(item.tag()).append(')');
                        }
                        out.append('\n');
                        for (Row row : item.rows()) {
                            out.append(row.label()).append(": ").append(row.value()).append('\n');
                        }
                    }
                    case Paragraph paragraph -> out.append(paragraph.text()).append('\n');
                    case Bullets bullets -> {
                        out.append(bullets.title()).append('\n');
                        for (String line : bullets.lines()) {
                            out.append("• ").append(line).append('\n');
                        }
                    }
                    case Command command -> out.append(command.label()).append('\n')
                            .append(command.command()).append('\n');
                }
                out.append('\n');
            }
        }
        return out.toString();
    }

    // --- HTML ---

    String html(EmailDocument document) {
        StringBuilder out = new StringBuilder();
        out.append("<div style=\"").append(FONT).append("font-size:14px;line-height:1.5;color:#1a1a1a;max-width:640px\">");
        for (Section section : document.sections()) {
            out.append("<h3 style=\"font-size:15px;font-weight:bold;margin:22px 0 8px;padding-bottom:3px;")
                    .append("border-bottom:1px solid #cccccc\">")
                    .append(esc(section.heading())).append("</h3>");
            for (Block block : section.blocks()) {
                switch (block) {
                    case Item item -> item(out, item);
                    case Paragraph paragraph -> out.append("<p style=\"margin:0 0 10px\">")
                            .append(esc(paragraph.text())).append("</p>");
                    case Bullets bullets -> {
                        out.append("<p style=\"margin:0 0 2px;font-weight:bold;font-size:13px\">")
                                .append(esc(bullets.title())).append("</p>");
                        out.append("<ul style=\"margin:0 0 12px;padding-left:20px;font-size:13px;").append(MUTED).append("\">");
                        for (String line : bullets.lines()) {
                            out.append("<li style=\"margin:0 0 2px\">").append(esc(line)).append("</li>");
                        }
                        out.append("</ul>");
                    }
                    case Command command -> {
                        out.append("<p style=\"margin:0 0 4px;font-size:13px;").append(MUTED).append("\">")
                                .append(esc(command.label())).append("</p>");
                        out.append("<div style=\"font-family:Consolas,'Courier New',monospace;font-size:12px;")
                                .append("background:#f4f4f4;border:1px solid #dddddd;padding:8px 10px;margin:0 0 12px;")
                                .append("word-break:break-all\">")
                                .append(esc(command.command())).append("</div>");
                    }
                }
            }
        }
        out.append("</div>");
        return out.toString();
    }

    private void item(StringBuilder out, Item item) {
        out.append("<p style=\"margin:14px 0 4px\"><b>").append(esc(item.title())).append("</b>");
        if (item.tag() != null) {
            out.append(" – ").append(esc(item.tag()));
        }
        out.append("</p>");
        out.append("<table style=\"border-collapse:collapse;font-size:14px\">");
        for (Row row : item.rows()) {
            out.append("<tr><td style=\"padding:2px 16px 2px 0;vertical-align:top;white-space:nowrap;").append(MUTED).append("\">")
                    .append(esc(row.label())).append("</td><td style=\"padding:2px 0;vertical-align:top\">")
                    .append(esc(row.value())).append("</td></tr>");
        }
        out.append("</table>");
    }

    /** Escapes &, <, > and quotes so names like "AT&T" can't break the HTML. */
    private static String esc(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
