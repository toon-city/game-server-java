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

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "item_type", nullable = false, length = 20)
    private String itemType;

    /** Sous-type (ex: "HAT", "PIECE"). Utilisé pour restreindre le placement aux meubles "PIECE". */
    @Column(name = "sub_type", length = 30)
    private String subType;

    /**
     * Clé de dossier de l'asset — slot avatar pour les vêtements (ex: "hair",
     * "hat"), catégorie de meuble pour le reste (ex: "jardin"). Concaténé avec
     * spritePath ("{spriteKey}/{spritePath}") pour résoudre l'URL de l'asset
     * (vêtements comme meubles) — pas null pour les meubles, contrairement à
     * ce que disait l'ancien commentaire ici.
     */
    @Column(name = "sprite_key", length = 64)
    private String spriteKey;

    /** Identifiant du sprite (ex: "hair7", "banc"). Utilisé pour charger l'asset côté client. */
    @Column(name = "sprite_path", length = 255)
    private String spritePath;

    /** Chemin relatif/URL vers l'image d'affichage — utilisé par l'aperçu de meuble (clic hors édition). */
    @Column(name = "display_image", length = 255)
    private String displayImage;
}
