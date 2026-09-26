package dev.coretrace.client;

import dev.coretrace.core.CoreProtectParser;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against the real, unobfuscated Minecraft 26.2 Component classes. */
class ComponentReaderTest {
    @Test void readsInheritedHoverAndKeepsExactTimestamp() {
        Component m=Component.literal("0.05/h ago").withStyle(Style.EMPTY.withHoverEvent(
                new HoverEvent.ShowText(Component.literal("2026-09-06 01:23:45 UTC"))))
                .append(Component.literal(" - jose broke minecraft:stone."));
        var data=ComponentReader.read(m,true,10);
        assertEquals(1,data.hovers().size());assertEquals("2026-09-06 01:23:45 UTC",data.hovers().getFirst());
        assertTrue(CoreProtectParser.entry(data));
    }
    @Test void recognizesPageComponentButNeverCopiesTeleportOrGiveAsNavigation() {
        Component m=Component.literal("Página 1/15 ")
                .append(Component.literal("▶").withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/co l 2"))))
                .append(Component.literal("otro").withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/co teleport wid:1 1 64 1"))))
                .append(Component.literal("↓").withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/co give #99"))));
        var data=ComponentReader.read(m,true,10);
        assertEquals(java.util.List.of("/co l 2"),data.lookupLinks());
        assertEquals(15,CoreProtectParser.pagination(data).orElseThrow().total());
    }
    @Test void canDisableHoverStorage() {
        Component m=Component.literal("x").withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal("detalle"))));
        assertTrue(ComponentReader.read(m,false,1).hovers().isEmpty());
    }
    @Test void preservesStrikethroughAsEvidenceMetadata() {
        Component m=Component.literal("1.00/h ago - jose broke minecraft:stone.").withStyle(Style.EMPTY.withStrikethrough(true));
        assertTrue(ComponentReader.read(m,true,1).struckThrough());
    }
    @Test void normalChatCannotImpersonateFooterByIncludingPageLabel() {
        assertTrue(CoreProtectParser.pagination(ComponentReader.read(Component.literal("<jose> Page 1/15"),true,1)).isEmpty());
    }
}
