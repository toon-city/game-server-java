package live.toon.server.repository;

import live.toon.server.entity.PrivateMessage;
import org.springframework.data.jpa.repository.JpaRepository;

/** Write-only for now — save() is the only thing anything calls. */
public interface PrivateMessageRepository extends JpaRepository<PrivateMessage, Long> {
}
