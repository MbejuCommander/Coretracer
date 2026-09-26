package dev.coretrace.client;

import dev.coretrace.core.LookupCommand;
import dev.coretrace.core.MessageData;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Optional;

public final class ComponentReader {
    private ComponentReader() {}
    public static MessageData read(Component component, boolean hovers, long now) {
        var hoverText = new LinkedHashSet<String>();
        var pageLinks = new LinkedHashSet<String>();
        boolean[] struckThrough = {false};
        component.visit((style, text) -> {
            struckThrough[0] |= style.isStrikethrough();
            if (hovers && hoverText.size() < 16 && style.getHoverEvent() instanceof HoverEvent.ShowText hover)
                hoverText.add(hover.value().getString());
            if (style.getClickEvent() instanceof ClickEvent.RunCommand click
                    && LookupCommand.parse(click.command()).filter(c -> !c.query()).isPresent()) pageLinks.add(click.command());
            return Optional.empty();
        }, Style.EMPTY);
        return new MessageData(component.getString(), new ArrayList<>(hoverText), new ArrayList<>(pageLinks), now, struckThrough[0]);
    }
}
