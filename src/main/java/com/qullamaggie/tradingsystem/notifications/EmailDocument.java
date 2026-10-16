package com.qullamaggie.tradingsystem.notifications;

import java.util.List;

/**
 * The content of an email, independent of how it is shown. EmailRenderer
 * turns the same document into both plain text and HTML, so the two
 * versions can never say different things.
 *
 * A document is a subject plus sections; each section is a heading and
 * a list of blocks.
 */
public record EmailDocument(String subject, List<Section> sections) {

    public record Section(String heading, List<Block> blocks) {}

    /** The kinds of content a section can hold. */
    public sealed interface Block permits Item, Paragraph, Bullets, Command {}

    /**
     * Something to do, e.g. "Köp MSTR", shown as a title with label/value rows.
     *
     * @param tag short context after the title, e.g. "breakout"; null for none
     */
    public record Item(String title, String tag, List<Row> rows) implements Block {}

    public record Row(String label, String value) {}

    public record Paragraph(String text) implements Block {}

    /** A titled list, used for "how the system calculated". */
    public record Bullets(String title, List<String> lines) implements Block {}

    /** A PowerShell command with an explanation above it. */
    public record Command(String label, String command) implements Block {}
}
