package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only mapping of the shared {@code metiers} table (owned by game-api).
 * Only the outfit fields matter here — RoomStateService.buildClothingMap()
 * is the sole consumer, applying the same overlay rule as game-api's
 * WorkOutfitService.
 */
@Entity
@Table(name = "metiers")
@Getter
@NoArgsConstructor
public class Metier {

    @Id
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_tshirt_item_id")
    private Item outfitTshirt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_pant_item_id")
    private Item outfitPant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outfit_hat_item_id")
    private Item outfitHat;
}
