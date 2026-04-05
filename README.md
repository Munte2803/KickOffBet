# KickOffBet

KickOffBet este o platforma de pariuri sportive dezvoltata ca monorepo, cu backend Spring Boot, frontend Vue 3 si infrastructura locala rulata prin Docker Compose. Aplicatia acopera autentificare, verificare KYC, administrare de ligi si echipe, gestionare de meciuri, calcul intern de cote, portofel, tranzactii si bilete.

## Ce face proiectul

- inregistrare, login, confirmare email si resetare parola
- upload de act de identitate si validare KYC din admin
- administrare utilizatori, ligi, echipe, meciuri, tranzactii si bilete
- sincronizare automata de meciuri din Football Data API
- generare si gestionare de cote
- portofel, depuneri, retrageri si istoric tranzactii
- protectie cu JWT, rate limiting si controale de securitate

## Stack tehnic

### Backend

- Java 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- PostgreSQL
- MinIO
- MapStruct
- Bucket4j
- Spring Mail
- Springdoc OpenAPI

### Frontend

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Vue Query
- Axios
- Vee Validate + Zod
- Tailwind CSS 4

### Infrastructura locala

- Docker Compose
- PostgreSQL
- MinIO
- Adminer
- Nginx pentru servirea frontendului containerizat

## Structura proiectului

```text
.
|-- src/                          # backend Spring Boot
|-- Kickoffbet-ui/                # frontend Vue 3
|   |-- src/api
|   |-- src/components
|   |-- src/composables
|   |-- src/constants
|   |-- src/layouts
|   |-- src/pages
|   |   |-- public
|   |   |-- user
|   |   `-- admin
|   |-- src/stores
|   |-- src/types
|   |-- src/utils
|   `-- src/validation
|-- docker-compose.yml
|-- Dockerfile
`-- README.md
```

## Cerinte

- Java 21
- Node.js 20+
- npm
- Docker Desktop sau Docker Engine cu Docker Compose

## Configurare

1. Cloneaza repository-ul:

```bash
git clone https://github.com/Munte2803/KickOffBet.git
cd KickOffBet
```

2. Creeaza fisierul `.env` pornind de la exemplu:

```bash
cp .env.example .env
```

3. Completeaza variabilele necesare in `.env`.


- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `SWAGGER_ENABLED`
- `MAIL_USERNAME`
- `MAIL_PASSWORD`
- `MAIL_FROM`
- `FRONTEND_URL`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `MINIO_PUBLIC_URL`
- `FOOTBALL_DATA_TOKEN`
- `VITE_API_BASE_URL`

## Rulare cu Docker Compose

Pentru a porni tot stack-ul local:

```bash
docker compose up -d --build
```

Serviciile expuse local sunt:

- frontend: `http://localhost:3000`
- backend API: `http://localhost:8080`
- Adminer: `http://localhost:8888`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`

Swagger este disponibil la:

`http://localhost:8080/swagger-ui/index.html`

doar daca `SWAGGER_ENABLED=true`.

## Rulare pentru dezvoltare

### Varianta 1: infrastructura in Docker, backend si frontend local

Porneste serviciile suport:

```bash
docker compose up -d db minio adminer
```

Porneste backendul:

```bash
./mvnw spring-boot:run
```

Porneste frontendul:

```bash
cd Kickoffbet-ui
npm install
npm run dev
```

### Varianta 2: totul in Docker

```bash
docker compose up -d --build
```

## Scripturi utile

### Backend

Compilare:

```bash
./mvnw -DskipTests compile
```

### Frontend

Dezvoltare:

```bash
npm run dev
```

Build productie:

```bash
npm run build
```

## Acces API

Regulile actuale de acces sunt:

- `/api/auth/**` este public
- `/api/admin/**` este accesibil doar administratorilor
- restul endpointurilor necesita autentificare, sau chiar validarea contului
- Swagger este disponibil doar cand este activat din configurare

## Zone functionale

### Utilizator

- meciuri, rezultate, ligi si echipe
- profil si documente KYC
- portofel si tranzactii
- bilete si istoric

### Administrator

- management utilizatori
- verificare documente
- management ligi, echipe si meciuri
- management tranzactii si bilete
- sincronizare si administrare cote
