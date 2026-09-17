package live.toon.server.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Server-side port of game-core's HouseParser + project() (src/modules/house/
 * HouseParser.ts, src/utils/project.ts) — just enough of it to answer "where
 * is the door, in the same world-pixel space the client renders the room in".
 *
 * Needed because {@code rooms.house_data} is authored in raw, un-projected
 * floor-plan coordinates (a point's XPOS/YPOS from the original room editor —
 * see project.ts's own comment on how that space relates to screen space) and
 * only the CLIENT used to turn that into pixel positions. A join's spawn spot
 * has to land in that same projected space, which means either trusting
 * whatever x/y the client sends (self-reported, and wrong for a brand new
 * join anyway — there was no "last known position" yet) or replicating the
 * projection here. This mirrors HouseParser.parseStructureFromJson's pipeline
 * exactly: rotate every point -90°, run project() (angle -45°, depthFactor
 * sqrt(2) — see project.ts's own derivation from the original Flash game's
 * projection), then shift the whole room so nothing is negative, exactly
 * like HouseParser's offsetX/offsetY.
 *
 * Kept in sync by hand with the TypeScript version; the two only need to
 * agree on this one pipeline, which is small and has been stable across this
 * session's projection/depth fixes — revisit both together if project.ts's
 * angle or depthFactor ever changes again.
 */
@Slf4j
public final class HouseGeometry {

    private static final double ANGLE = -Math.PI / 4;
    private static final double DEPTH_FACTOR = Math.sqrt(2);

    /**
     * How far to nudge the door-center spawn point toward the room's floor
     * centroid, in world px. Without this, the raw door-center point sits
     * exactly on the wall segment's own baseline — the same line
     * collision.ts's buildWallPolygons centers its (solid, door-agnostic —
     * see that file's own comment on why doors no longer carve a gap) wall
     * band on. Spawning a joiner directly on that line reads as "standing in
     * the wall/doorway" rather than through it.
     *
     * Kept small on purpose: door spans in the recovered public rooms run as
     * narrow as ~90px end to end (e.g. Quizz), so anything much bigger than
     * the collision band pushes the spawn out of the door's own footprint
     * and onto the open floor beside it — confirmed live (Playwright) after
     * first trying 50, which visibly missed the door. 18 clears the widest
     * band (16, hidden walls — see WALL_THICKNESS in collision.ts) by 2px,
     * enough to not re-overlap it, while staying inside the door gap.
     */
    private static final double DOOR_SPAWN_INSET = 18;

    private HouseGeometry() {}

    public record Point(double x, double y) {}

    /**
     * World-pixel center of the room's entry door (the wall segment with
     * {@code enter: true} and a {@code door.offset}), in the same space the
     * client places furniture/avatars in. Empty if houseData is missing,
     * malformed, or defines no entry door — callers should fall back to
     * whatever they'd otherwise use (e.g. a client-supplied position).
     */
    public static Optional<Point> findDoorCenter(String houseData, ObjectMapper mapper) {
        if (houseData == null || houseData.isBlank()) return Optional.empty();
        try {
            JsonNode root = mapper.readTree(houseData);
            JsonNode rawPoints = root.path("points");
            if (!rawPoints.isArray() || rawPoints.isEmpty()) return Optional.empty();

            // 1) Project every point (rotate -90°, then the isometric projection).
            Point[] projected = new Point[rawPoints.size()];
            for (int i = 0; i < rawPoints.size(); i++) {
                JsonNode p = rawPoints.get(i);
                double rawX = p.path("x").asDouble();
                double rawY = p.path("y").asDouble();
                Point rotated = rotateMinus90(rawX, rawY);
                projected[i] = project(rotated.x(), rotated.y());
            }

            // 2) Shift so nothing is negative, exactly like HouseParser's offsetX/offsetY.
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            for (Point p : projected) {
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
            }
            double offsetX = minX < 0 ? -minX : 0;
            double offsetY = minY < 0 ? -minY : 0;
            for (int i = 0; i < projected.length; i++) {
                projected[i] = new Point(projected[i].x() + offsetX, projected[i].y() + offsetY);
            }

            // 3) First wall with an entry door.
            for (JsonNode wall : root.path("walls")) {
                if (!wall.path("enter").asBoolean(false)) continue;
                JsonNode door = wall.path("door");
                if (door.isMissingNode() || door.isNull()) continue;

                int iA = wall.path("ptA").asInt(-1);
                int iB = wall.path("ptB").asInt(-1);
                if (iA < 0 || iB < 0 || iA >= projected.length || iB >= projected.length) continue;

                Point pA = projected[iA];
                Point pB = projected[iB];
                double dx = pB.x() - pA.x();
                double dy = pB.y() - pA.y();
                double len = Math.hypot(dx, dy);
                if (len < 1e-6) continue;
                double nx = dx / len;
                double ny = dy / len;

                double off = door.path("offset").asDouble();
                // Center of the door span — HouseParser computes its start/end
                // as off ± doorWidth/2 along the wall, which average back out
                // to exactly pA + off*(nx,ny); the door's width only affects
                // the span's extent, not its midpoint, so it's not needed here.
                double doorX = pA.x() + off * nx;
                double doorY = pA.y() + off * ny;

                // Nudge inward, off the wall's own line, toward the room's
                // floor — see DOOR_SPAWN_INSET. Direction comes from the
                // floor polygon centroid rather than the wall normal because
                // the normal's sign is arbitrary (depends on ptA→ptB winding,
                // which isn't guaranteed consistent across walls), while
                // "toward the floor" is always correct regardless of which
                // wall or side of the room the door is on.
                Point floorCentroid = floorCentroid(root.path("floors"), projected);
                if (floorCentroid != null) {
                    double toFx = floorCentroid.x() - doorX;
                    double toFy = floorCentroid.y() - doorY;
                    double toFLen = Math.hypot(toFx, toFy);
                    if (toFLen > 1e-6) {
                        doorX += toFx / toFLen * DOOR_SPAWN_INSET;
                        doorY += toFy / toFLen * DOOR_SPAWN_INSET;
                    }
                }

                return Optional.of(new Point(doorX, doorY));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to parse house_data for door center: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Centroid of every vertex referenced by any {@code floors[].points[]} entry, or null if none. */
    private static Point floorCentroid(JsonNode floors, Point[] projected) {
        double sumX = 0, sumY = 0;
        int count = 0;
        for (JsonNode floor : floors) {
            for (JsonNode idxNode : floor.path("points")) {
                int idx = idxNode.asInt(-1);
                if (idx < 0 || idx >= projected.length) continue;
                sumX += projected[idx].x();
                sumY += projected[idx].y();
                count++;
            }
        }
        return count == 0 ? null : new Point(sumX / count, sumY / count);
    }

    private static Point rotateMinus90(double x, double y) {
        // rotatePoint(x, y, -90) in geometry.ts: (x*cos - y*sin, x*sin + y*cos)
        // with cos(-90°)=0, sin(-90°)=-1 → (y, -x).
        return new Point(y, -x);
    }

    private static Point project(double x, double y) {
        double screenX = x + y * Math.cos(ANGLE) * DEPTH_FACTOR;
        double screenY = y * Math.sin(ANGLE) * DEPTH_FACTOR; // z=0 for a floor-plan point
        return new Point(screenX, screenY);
    }
}
