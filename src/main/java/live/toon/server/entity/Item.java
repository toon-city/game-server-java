package live.toon.server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only mapping of the shared {@code items} table (owned by game-api).
 */
@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor
public class Item {

    @Id
    private Long id;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    /** Clé dans le slot de l'avatar (ex: "hair", "hat", "tshirt"). Null pour les meubles. */
    @Column(name = "sprite_key", length = 64)
    private String spriteKey;

    /** Identifiant du sprite (ex: "hair7"). Utilisé pour charger l'asset côté client. */
    @Column(name = "sprite_path", length = 255)
    private String spritePath;
}
