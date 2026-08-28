package com.mohistmc.youer.ai;

import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class AiResponseAudience {

    private final Set<Audience> audiences;

    AiResponseAudience(Set<? extends Audience> audiences) {
        this.audiences = Set.copyOf(audiences);
    }

    public void send(Component message) {
        for (Audience audience : audiences) {
            if (!(audience instanceof Player player) || player.isOnline()) {
                audience.sendMessage(message);
            }
        }
    }

    public int size() {
        return audiences.size();
    }
}
