package example.game;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerPhoneServiceTest {
    @Test
    void flaggedAssetGoesToReviewButCleanAssetDoesNot() {
        assertEquals("review", PlayerPhoneService.moderationDecision(
                new PlayerPhoneService.Asset("player-7", "skin-4", "tournament-2", true)));
        assertEquals("clear", PlayerPhoneService.moderationDecision(
                new PlayerPhoneService.Asset("player-7", "skin-5", "tournament-2", false)));
    }
}
