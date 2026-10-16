package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.notifications.EmailContent;
import com.qullamaggie.tradingsystem.notifications.EmailDocument;
import com.qullamaggie.tradingsystem.notifications.EmailDocument.*;
import com.qullamaggie.tradingsystem.notifications.EmailRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailRendererTest {

    private final EmailRenderer renderer = new EmailRenderer();

    private EmailDocument document() {
        return new EmailDocument("Ämne", List.of(
                new Section("Köp / sälj", List.of(
                        new Item("Köp MSTR", "breakout", List.of(
                                new Row("Köp", "1 168 st när kursen går över 171,19 $"),
                                new Row("Stop loss", "163,18 $"))),
                        new Item("Minska AT&T", null, List.of(new Row("Sälj", "10 st"))))),
                new Section("Så har systemet räknat", List.of(
                        new Bullets("MSTR, köp", List.of("Stop loss 163,18 $ = dagens lägsta kurs.")))),
                new Section("Registrera i systemet", List.of(
                        new Command("Köpt MSTR?", "Invoke-RestMethod -Body '{\"shares\": 1168}'")))));
    }

    @Test
    void subjectIsPassedThrough() {
        assertEquals("Ämne", renderer.render(document()).subject());
    }

    @Test
    void text_hasHeadingsItemsBulletsAndCommands() {
        String text = renderer.render(document()).text();

        assertTrue(text.contains("━━ KÖP / SÄLJ ━━"));
        assertTrue(text.contains("KÖP MSTR (breakout)\nKöp: 1 168 st när kursen går över 171,19 $\nStop loss: 163,18 $\n"));
        assertTrue(text.contains("MINSKA AT&T\nSälj: 10 st\n"));
        assertTrue(text.contains("━━ SÅ HAR SYSTEMET RÄKNAT ━━"));
        assertTrue(text.contains("MSTR, köp\n• Stop loss 163,18 $ = dagens lägsta kurs.\n"));
        assertTrue(text.contains("Köpt MSTR?\nInvoke-RestMethod -Body '{\"shares\": 1168}'\n"));
    }

    @Test
    void html_hasBoldTitleTagAndLabelValueTable() {
        String html = renderer.render(document()).html();

        assertTrue(html.contains("<b>Köp MSTR</b> – breakout"));
        assertTrue(html.contains(">Stop loss</td>"));
        assertTrue(html.contains(">163,18 $</td>"));
        assertTrue(html.contains("Arial"));
    }

    @Test
    void html_escapesSpecialCharacters() {
        EmailContent email = renderer.render(document());

        assertTrue(email.html().contains("Minska AT&amp;T"));
        assertTrue(email.html().contains("&#39;{&quot;shares&quot;: 1168}&#39;"));
        assertFalse(email.html().contains("AT&T"));
    }
}
