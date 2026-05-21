# Student Management Hub

A real-time academic intelligence dashboard for the CSE 2023-27 cohort. Spring Boot backend + React (Vite) frontend with **Server-Sent Events** for live updates, multi-role auth, rich analytics, and a polished UI/UX.

## Highlights

- **Realtime stream (SSE)** at `/api/students/stream` — UI updates instantly on every create / update / delete / login.
- **MongoDB optional**: backend falls back to an in-memory store seeded with realistic synthetic data if Mongo is not running. No setup required to demo.
- **Two-role auth**: ADMIN (full CRUD, bulk delete, attendance/grade edit, sync) and STUDENT (read-only, profile self-edit).
- **Live activity feed & notifications** broadcast over SSE.
- **Search, multi-status filter, sortable columns, pagination, CSV export, bulk select.**
- **Quick attendance update** via inline slider in the expanded row.
- **Two-step student form** with live CGPA preview and validation.
- **Theme toggle** (dark / light), responsive sidebar + mobile bottom nav.

## Run

### Backend (Spring Boot)
```
cd backend
mvn spring-boot:run
```
The API listens on `http://localhost:8080`. MongoDB is optional — without it, an in-memory store is used.

### Frontend (Vite)
```
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173`.

## Demo accounts
| Role    | Username  | Password    |
|---------|-----------|-------------|
| Admin   | admin     | admin123    |
| Admin   | faculty   | faculty123  |
| Student | demo      | demo123     |

Imported students also get a per-student login (username = reg-no, password = first-2-letters-of-name + last-3-of-reg-no, e.g. `AA001`).

## REST API (selection)
- `GET  /api/students?q=&status=&sortBy=&order=` — search/filter/sort
- `POST /api/students` — create
- `PUT  /api/students/{id}` — update
- `PATCH /api/students/{id}/attendance` — quick attendance
- `PATCH /api/students/{id}/grade` — semester GPA
- `POST /api/students/bulk-delete` — bulk remove
- `GET  /api/students/analytics` — dashboard data
- `GET  /api/students/activity` — recent activity log
- `GET  /api/students/notifications` — notifications
- `GET  /api/students/stream` — **SSE realtime feed**
- `GET  /api/students/health` — service health
