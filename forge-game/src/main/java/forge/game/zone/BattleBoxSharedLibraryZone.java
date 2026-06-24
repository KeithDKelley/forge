package forge.game.zone;

import forge.game.card.CardCollection;
import forge.game.event.EventValueChangeType;
import forge.game.event.GameEventZone;
import forge.game.player.Player;

import java.util.List;

public class BattleBoxSharedLibraryZone extends PlayerZone {
    private static final long serialVersionUID = -5687652485777639176L;

    private final List<Player> players;

    public BattleBoxSharedLibraryZone(final Player player, final CardCollection cards, final List<Player> sharedPlayers) {
        super(ZoneType.Library, player, cards);
        players = sharedPlayers;
    }

    @Override
    protected void onChanged() {
        for (final Player player : players) {
            player.updateZoneForView(player.getZone(ZoneType.Library));
            if (player != getPlayer()) {
                game.fireEvent(new GameEventZone(ZoneType.Library, player, EventValueChangeType.ComplexUpdate, null));
            }
        }
    }
}
