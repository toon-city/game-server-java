package live.toon.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class StatsResponse {

    private int totalConnected;
    private List<ConnectedUserStat> users;

    @Data
    @Builder
    public static class ConnectedUserStat {
        private String username;
        private String roomId;
        private String roomName;
        /** MALE, FEMALE, NON_BINARY — peut être null */
        private String gender;
        /** 0 = user, 1 = modérateur, 2 = admin */
        private int rank;
        /** 0 = aucun, 1/2/3 */
        private int toonizLevel;
    }
}
