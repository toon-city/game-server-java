package live.toon.server.controller;

import live.toon.server.dto.StatsResponse;
import live.toon.server.service.RoomStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final RoomStateService roomStateService;

    /** Retourne le nombre de joueurs connectés et la liste simplifiée. */
    @GetMapping
    public StatsResponse getStats() {
        List<Map.Entry<String, live.toon.server.model.ConnectedUser>> entries =
                roomStateService.getAllConnectedUsersWithRoom();

        List<StatsResponse.ConnectedUserStat> stats = entries.stream()
                .map(e -> StatsResponse.ConnectedUserStat.builder()
                        .username(e.getValue().getUsername())
                        .roomId(e.getKey())
                        .roomName("")
                        .gender(e.getValue().getGender())
                        .rank(e.getValue().getRank())
                        .toonizLevel(e.getValue().getToonizLevel())
                        .build())
                .toList();

        return StatsResponse.builder()
                .totalConnected(stats.size())
                .users(stats)
                .build();
    }
}
