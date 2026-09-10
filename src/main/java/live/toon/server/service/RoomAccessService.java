package live.toon.server.service;

import live.toon.server.entity.Room;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The one room-management permission rule, used everywhere something needs
 * to know "can this user manage this room" (furniture placement, the
 * yourPermission field on join, room kick/ban): the room's owner, or any
 * admin. Moderators get nothing extra here — that's a deliberate, narrower
 * rule than site-wide moderation power (see RoomModerationService, which
 * uses this too).
 *
 * Extracted because this exact check was independently duplicated in
 * FurnitureStateService and RoomStateService before this — a third copy for
 * room kick/ban would have made it worse, not better.
 */
@Service
public class RoomAccessService {

    /** Mirrors live.toon.api.security.UserRank.ROLE_ADMIN.getLevel(). */
    public static final int ADMIN_RANK = 2;

    public boolean canManageRoom(Room room, UUID userId, int rank) {
        if (rank >= ADMIN_RANK) return true;
        return userId != null && userId.equals(room.getOwnerId());
    }
}
