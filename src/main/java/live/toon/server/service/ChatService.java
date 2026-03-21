package live.toon.server.service;

import live.toon.server.entity.ChatMessage;
import live.toon.server.model.UserPrincipal;
import live.toon.server.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public ChatMessage save(Long roomId, UserPrincipal principal, String text) {
        return chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .userId(principal.getUserId())
                .username(principal.getUsername())
                .message(text)
                .build());
    }
}
