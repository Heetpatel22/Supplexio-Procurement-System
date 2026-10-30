# Supplexio Procurement System - Zima Server & Cloudflare Deployment Guide

This guide provides end-to-end instructions for deploying **Supplexio Procurement System** on your **Zima Server** (ZimaOS, CasaOS, or Debian/Ubuntu Docker host) and making it securely accessible from anywhere in the world using your custom domain via **Cloudflare Tunnel** (no router port-forwarding or public IP required).

---

## Architecture Overview

```
 [User Browser / Internet]
            │
            ▼ (HTTPS / SSL via Cloudflare Edge)
   https://supplexio.yourdomain.com
            │
            ▼ (Secure Outbound Cloudflare Tunnel)
 ┌────────────────────── Zima Server (ZimaOS / CasaOS) ──────────────────────┐
 │                                                                           │
 │  ┌─────────────────────────────────────────────────────────────────────┐  │
 │  │                         Docker Network                              │  │
 │  │                                                                     │  │
 │  │   ┌─────────────────────┐               ┌───────────────────────┐   │  │
 │  │   │ Cloudflare Tunnel   │ ────────────> │ Supplexio App         │   │  │
 │  │   │ (cloudflared)       │ HTTP:8080     │ (Tomcat 9 + Java 8)   │   │  │
 │  │   └─────────────────────┘               └──────────┬────────────┘   │  │
 │  │                                                    │                │  │
 │  │                                                    │ MySQL:3306     │  │
 │  │                                                    ▼                │  │
 │  │                                         ┌───────────────────────┐   │  │
 │  │                                         │ MySQL 8.0 Database    │   │  │
 │  │                                         │ (auto-initialized)    │   │  │
 │  │                                         └───────────────────────┘   │  │
 │  └─────────────────────────────────────────────────────────────────────┘  │
 └───────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Prerequisites

1. **Zima Server** (ZimaBoard, ZimaBlade, or custom PC running ZimaOS or CasaOS).
2. **Cloudflare Account** with an active domain managed by Cloudflare DNS (free plan works 100%).
3. Local network access to your Zima server (web dashboard or SSH).

---

## 2. Cloudflare Tunnel Setup (Get Your Token)

1. Log into your [Cloudflare Dashboard](https://dash.cloudflare.com/).
2. On the left sidebar, click **Zero Trust**. (If first time, create a free team name).
3. In Zero Trust, navigate to **Networks** -> **Tunnels** (or **Access** -> **Tunnels**).
4. Click **Create a tunnel** (or **Add a tunnel**):
   - Select **Cloudflared** and click **Next**.
   - Name your tunnel (e.g. `zima-supplexio`) and click **Save tunnel**.
5. In the **Install and run a connector** step:
   - Select **Docker**.
   - You will see a command like:
     ```bash
     docker run cloudflare/cloudflared:latest tunnel --no-autoupdate run --token eyJhIjoi...
     ```
   - Copy only the token string after `--token` (the long string starting with `eyJh...`).
   - Keep this token handy for the `.env` file.
6. Click the **Public Hostname** tab (or configure it now):
   - Click **Add a public hostname**.
   - **Subdomain**: `supplexio` (or `@` if using root domain `yourdomain.com`).
   - **Domain**: Select your domain from the dropdown (e.g., `yourdomain.com`).
   - **Path**: Leave blank.
   - **Type**: `HTTP`
   - **URL**: `app:8080` (or `supplexio-app:8080`)
   > **Note**: If you ever run cloudflared outside the docker-compose network, set URL to `<ZIMA_LOCAL_IP>:8080` (e.g., `192.168.1.50:8080`).
7. Click **Save hostname**.

---

## 3. Deploy to Zima Server (Step-by-Step)

### Option A: Using SSH / Terminal (Recommended)

1. **SSH into your Zima Server**:
   ```bash
   ssh root@<zima-server-ip>
   # or: ssh casaos@<zima-server-ip>
   ```

2. **Navigate to your projects directory**:
   ```bash
   mkdir -p /DATA/AppData/supplexio
   cd /DATA/AppData/supplexio
   ```

3. **Clone the repository**:
   ```bash
   git clone https://github.com/Heetpatel22/Supplexio-Procurement-System.git .
   ```
   *(If you are deploying your latest changes with the Docker files, push your changes to your git branch or use `scp` / rsync to transfer the folder).*

4. **Configure your environment**:
   ```bash
   cp .env.example .env
   ```
   *(Your Cloudflare token is already embedded in `docker-compose.yml` and `.env.example`, so no manual editing is needed unless you want to customize ports or passwords).*

5. **Start the application with Docker Compose**:
   ```bash
   docker compose up -d --build
   ```

6. **Check container status**:
   ```bash
   docker compose ps
   ```
   You should see:
   - `supplexio-mysql` (healthy)
   - `supplexio-app` (running)
   - `supplexio-cloudflared` (running)

7. **Verify logs**:
   ```bash
   docker compose logs -f app
   ```

---

### Option B: Using ZimaOS / CasaOS Web UI (Custom App Install)

1. Open your ZimaOS / CasaOS web dashboard in your browser (`http://<zima-ip>`).
2. Click **App Store** -> Click **Custom Install** (top right icon).
3. Click the **Import** button (top right of modal) and select **Docker Compose**.
4. Paste the contents of `docker-compose.yml`.
5. Under Environment variables, add your `CLOUDFLARE_TUNNEL_TOKEN` with the value from Cloudflare.
6. Set the App Name to `Supplexio`.
7. Set Web UI port to `8080`.
8. Click **Submit** / **Install**. ZimaOS will automatically pull the images, build the app, and run it.

---

## 4. Verifying Local & Domain Access

1. **Local Network Test**:
   Visit: `http://<zima-server-ip>:8080/`
   The Supplexio Procurement login page should appear.

2. **Cloudflare Custom Domain Test**:
   Visit: `https://supplexio.yourdomain.com/` (or your chosen domain).
   Cloudflare automatically provisions the SSL certificate, and your Supplexio portal is fully online!

---

## 5. Default Logins & Credentials

| Role | Username / Email | Password | Access / Purpose |
|------|-----------------|----------|------------------|
| **Admin** | `admin` (or `admin@example.com`) | `admin123` | Full Admin Dashboard, Users, Settings, Products, Auctions, Kanban |
| **Buyer** | `buyer1` (or `buyer1@example.com`) | `buyer123` | Procurement, PO generation, Stock review |
| **Supplier** | `supplier1` (or `supplier1@example.com`) | `supplier123` | Supplier bidding portal, active auction bids |

---

## 6. Maintenance, Backups & Commands

- **Restart containers**:
  ```bash
  docker compose restart
  ```
- **Stop containers**:
  ```bash
  docker compose down
  ```
- **Update to latest version**:
  ```bash
  git pull
  docker compose up -d --build
  ```
- **Backup Database**:
  ```bash
  docker compose exec mysql mysqldump -u root -proot axalta > backup_axalta.sql
  ```
- **Restore Database**:
  ```bash
  cat backup_axalta.sql | docker compose exec -T mysql mysql -u root -proot axalta
  ```

---

## 7. Troubleshooting

- **502 Bad Gateway on Domain**:
  Ensure the Cloudflare Tunnel hostname service is set to `HTTP` and URL `app:8080` (or `http://supplexio-app:8080`). If cloudflared is running on host mode, use `http://<zima-local-ip>:8080`.
- **Database Connection Error on startup**:
  Tomcat waits up to 45 seconds for MySQL to be healthy. Check `docker compose logs -f mysql` to confirm the MySQL engine finished initializing.
- **Port 8080 is already used on Zima**:
  Change `APP_PORT=8090` in `.env` and adjust the port in the compose file and Cloudflare hostname.

