# CarOneStop — Singapore Carpark & EV Charging Assistant

**SC2006 Software Engineering — NTU Group Project (TCE2 G27)**

CarOneStop is a mobile application that helps drivers in Singapore find nearby HDB carparks and EV charging stations, get intelligent ranked recommendations, book EV chargers, and manage payments — all in one place.

---

## Features

- **Carpark Search** — Find all HDB carparks within a configurable radius of your current location
- **EV Charger Search** — Discover nearby EV charging stations (LTA live + OpenChargeMap)
- **Smart Recommendations** — Top 3 ranked recommendations scored by price, ETA, and availability
- **Preference Modes** — Cheapest / Nearest / Most Available / Custom weighted
- **Real Driving ETA** — Google Routes API (traffic-aware) used for accurate drive time to top 3 picks
- **EV Charger Booking** — Reserve a charging slot, check in, and complete via the app
- **Wallet** — Top up balance via card or PayNow; wallet deducted on booking
- **Booking History** — View active and past bookings with live countdown timer
- **User Profiles** — Save car type and EV charging type for compatibility matching

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Mobile | React Native, Expo 54, TypeScript, Expo Router |
| Maps | React Native Maps |
| HTTP Client | Axios |
| Backend | Spring Boot 4.0.3, Java 21 |
| Database | PostgreSQL (JPA / Hibernate) |
| Auth | JWT + OTP email verification |
| Build | Maven |

---

## Prerequisites

Before running the project, ensure you have the following installed:

- **Java 21** (JDK)
- **Maven 3.9+**
- **PostgreSQL 14+**
- **Node.js 18+** and **npm**
- **Expo Go** app on your physical device (iOS or Android)
- API keys for: **data.gov.sg**, **LTA DataMall**, **Google Maps / Routes**, **URA**

---

## Repository Structure

```
SC2006-TCE2-G27/
├── lab3/
│   ├── backend/car-one-stop/       # Spring Boot backend
│   └── mobile/car-one-stop-mobile/ # React Native / Expo frontend
├── lab3_deliverables/              # Architecture & design docs
├── lab4/                           # Demo scripts
├── lab1/, lab2/                    # Requirements & design docs
├── CLAUDE.md                       # Developer context for AI assistant
└── README.md                       # This file
```

---

## Backend Setup

### 1. Create the PostgreSQL Database

```sql
CREATE DATABASE car_onestop;
```

> **Important:** The database name is `car_onestop` — underscore between `car` and `one`, NOT between `one` and `stop`.

### 2. Create `application-local.yml`

This file is **gitignored** and must be created manually on each machine.

Create it at:
```
lab3/backend/car-one-stop/src/main/resources/application-local.yml
```

With the following content:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/car_onestop
    username: postgres
    password: YOUR_POSTGRES_PASSWORD
  jpa:
    hibernate:
      ddl-auto: update

datagov:
  api-key: "YOUR_DATAGOV_API_KEY"

lta:
  api:
    key: "YOUR_LTA_DATAMALL_API_KEY"
    base-url: https://datamall2.mytransport.sg/ltaodataservice

hdb:
  api:
    key: "${datagov.api-key}"
  sync:
    availability:
      fixed-delay-ms: 120000

google:
  maps:
    api-key: "YOUR_GOOGLE_MAPS_API_KEY"

ura:
  api:
    access-key: "YOUR_URA_ACCESS_KEY"
    base-url: https://eservice.ura.gov.sg/uraDataService

ocm:
  api:
    key: ""

facility:
  force-reseed: false
```

**Where to get API keys:**
| Key | Source |
|-----|--------|
| `datagov.api-key` | data.gov.sg developer portal |
| `lta.api.key` | LTA DataMall (datamall.lta.gov.sg) |
| `google.maps.api-key` | Google Cloud Console — enable Routes API + Maps SDK |
| `ura.api.access-key` | URA eService portal |

### 3. Run the Backend

```powershell
cd lab3/backend/car-one-stop
mvn spring-boot:run "-Dspring-boot.run.profiles=local,googlemaps"
```

The backend starts at `http://localhost:8080`.

> The `googlemaps` profile activates real Google Routes API calls. Without it, the app falls back to haversine (straight-line) distance estimation.

On first startup, the backend will:
- Auto-create all database tables via Hibernate (`ddl-auto: update`)
- Seed carpark and EV charger facility data from external APIs
- Hydrate pricing data from LTA rates

---

## Frontend Setup

### 1. Install Dependencies

```powershell
cd lab3/mobile/car-one-stop-mobile
npm install
```

### 2. Configure Backend URL

**Android Emulator** (default, no change needed):
The default backend URL is `http://10.0.2.2:8080` which routes to your PC's localhost.

**Physical Device** (required):
Your phone must be on the same Wi-Fi network as your PC. Set the backend URL to your PC's local IP:

```powershell
$env:EXPO_PUBLIC_BACKEND_URL="http://192.168.x.x:8080"
npx expo start
```

Replace `192.168.x.x` with your PC's actual local IP address (run `ipconfig` on Windows to find it).

**Using ngrok** (for external access):
```powershell
ngrok http 8080
# Copy the https URL shown, e.g. https://xxxx.ngrok-free.app
$env:EXPO_PUBLIC_BACKEND_URL="https://xxxx.ngrok-free.app"
npx expo start
```

### 3. Run the Frontend

```powershell
cd lab3/mobile/car-one-stop-mobile
npx expo start
```

Scan the QR code with **Expo Go** on your phone.

### 4. Key Config Values

Located in `constants/config.ts`:

| Config | Default | Description |
|--------|---------|-------------|
| `SEARCH_RADIUS_KM` | `1.0` | Search radius in km for carparks and EV chargers |
| `BACKEND_URL` | `http://10.0.2.2:8080` | Backend base URL fallback (Android emulator only) |

---

## Running Both Together

1. Start PostgreSQL
2. Start the backend (`mvn spring-boot:run ...`)
3. Wait for backend to finish seeding (check logs — takes ~30 seconds on first run)
4. Start the frontend (`npx expo start`)
5. Open Expo Go and scan the QR code

---

## External APIs Used

| API | Purpose | Live Data |
|-----|---------|-----------|
| HDB (data.gov.sg) | Carpark coordinates, total lots, availability | Yes (synced every 2 min) |
| LTA DataMall | EV charger locations and live availability | Yes |
| URA | Carpark pricing rates | On startup |
| Google Routes API | Real driving ETA + distance to top 3 recommendations | Yes (per search) |
| OpenChargeMap (OCM) | Additional EV charger locations | Static (no live availability) |

---

## How the Recommendation Works

### Carpark
All carparks within the search radius are shown as small maroon pins. The **top 3** are scored and shown as numbered rank pins (1, 2, 3).

Scoring uses three factors, weighted by your chosen preference:

| Factor | Description |
|--------|-------------|
| Price | Effective cost for your parking duration |
| ETA | Real driving time from your location (Google Routes) |
| Availability | Blended score: relative to own capacity + relative to other candidates |

**Preference modes and base weights:**

| Mode | Price | ETA | Availability |
|------|-------|-----|--------------|
| Cheapest | 0.7 | 0.2 | 0.1 |
| Nearest | 0.2 | 0.7 | 0.1 |
| Most Available | 0.2 | 0.1 | 0.7 |
| Custom | User-set | User-set | User-set |

Weights are **dynamically adjusted** — if all candidates have similar prices/ETAs, that factor's weight is reduced and redistributed so rankings remain meaningful.

### EV Chargers
- Only **LTA live stations** are ranked (OpenChargeMap has no real-time data)
- Only charging points **compatible with your saved charging type** are counted
- Total cost = EV charging cost + nearest carpark cost (within 150m)
- Same weighted scoring as carpark

---

## Key Screens

| Screen | Description |
|--------|-------------|
| Main Map | Search carparks and EV chargers, view pins, navigate |
| Wallet | Top up balance via card or PayNow |
| Transactions | Active booking with countdown timer + booking history |
| Profile | Set car type, EV charging type, parking duration preference |
| Check-In | Scan/enter code to check in to an EV charger booking |

---

## Notes

- The booking fee is **$2.00** (non-refundable) deducted from your wallet on confirmation
- The active booking countdown timer only appears for `RESERVED` status — it disappears once you check in
- OCM stations appear on the map but cannot be booked (no live availability data)
- On physical devices, `10.0.2.2` does **not** work — you must set `EXPO_PUBLIC_BACKEND_URL` to your PC's IP
- The `googlemaps` Spring profile must be active for real ETA data; without it, straight-line distance is used as a fallback

---

## Team

SC2006 Software Engineering — NTU TCE2 Group 27
