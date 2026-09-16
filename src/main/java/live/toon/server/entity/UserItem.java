package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Mapping of the shared {@code user_items} table (owned by game-api, schema in
 * its Flyway migrations). Was read-only until furniture placement — game-server-java
 * now also writes placedInRoomId/x/y/orientation directly, same pattern already
 * used for equipped/item_id reads (see RoomStateService.buildClothingMap()):
 * this app shares the DB but not an entity module with game-api, so both must be
 * kept in sync by hand when the schema changes.
 */
@Entity
@Table(name = "user_items")
@Getter
@Setter
@NoArgsConstructor
public class UserItem {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private boolean equipped;

    /** Room this instance is currently placed in — null while it's just in inventory. */
    @Column(name = "placed_in_room_id")
    private Long placedInRoomId;

    private Double x;
    private Double y;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.SMALLINT)
    private Integer orientation;

    /** "WALL" or "FLOOR" — set together with zoneIndex when this instance is a
     *  wallpaper/floor texture applied to one of the room's own zones instead
     *  of an x/y placement. See TextureStateService. */
    @Column(name = "zone_type")
    private String zoneType;

    /** Index into house_data's walls[]/floors[] array (matching zoneType) that
     *  this texture currently covers. */
    @Column(name = "zone_index")
    private Integer zoneIndex;
}
