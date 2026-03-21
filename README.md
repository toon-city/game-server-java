# game-server-java

Serveur WebSocket temps réel du projet Toon City, développée avec **Spring Boot 3.2** + **STOMP**.

## Stack

- **Java 21** + Spring Boot 3.2
- **STOMP over WebSocket** — communication temps réel
- **PostgreSQL** — lecture de l'état des salles
- **JWT** — validation des tokens entrants
- **Gradle** (wrapper inclus)

## Responsabilités

- Gestion des connexions / déconnexions dans les salles
- Broadcast des events (mouvements, chat, actions)
- Exposition de l'endpoint `GET /stats` (joueurs connectés, salle courante)

## Endpoints

| Méthode | Route | Description |
|---------|-------|-------------|
| `GET` | `/stats` | Joueurs connectés + leur salle |
| `WS` | `/ws` | Point d'entrée STOMP |
| `STOMP` | `/app/room/{id}/join` | Rejoindre une salle |
| `STOMP` | `/app/room/{id}/chat` | Envoyer un message |
| `STOMP` | `/topic/room/{id}` | Broadcast d'une salle |

## Lancer en développement

```bash
# Terminal A — bootRun avec hot reload
make dev-server

# Terminal B — recompilation à chaque sauvegarde
make watch-server
```

Variables d'environnement requises :

```env
DB_URL=jdbc:postgresql://localhost:5432/toonlive
DB_USER=postgres
DB_PASSWORD=postgres
JWT_SECRET=<même secret que game-api>
```

## Build production

```bash
./gradlew bootJar
# ou via Docker
docker build -t toon-city/game-server-java .
```
