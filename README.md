# Backend — API Menu SaaS

Spring Boot 3.5 (Java 21) + Spring Security/JWT + PostgreSQL (Flyway) + OpenAPI.

## Ejecutar

```bash
# Con Postgres local (ver docker-compose en infrastructure/)
DB_URL=jdbc:postgresql://localhost:5432/menu_saas DB_USER=menu_saas DB_PASSWORD=menu_saas \
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Configuración por variables de entorno

| Variable | Default | Descripción |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/menu_saas` | JDBC URL |
| `DB_USER` / `DB_PASSWORD` | `menu_saas` | Credenciales |
| `JWT_SECRET` | (dev) | Clave HS256 (>= 32 bytes) — `openssl rand -base64 64` |
| `JWT_ACCESS_TTL` | 15 | Minutos del access token |
| `JWT_REFRESH_TTL` | 7 | Días del refresh token |
| `CORS_ALLOWED_ORIGINS` | localhost:4200 | Orígenes permitidos (coma separada) |
| `APP_BASE_URL` | `http://localhost:4200` | Base de la app web (para el QR) |
| `UPLOAD_DIR` | `./uploads` | Directorio de imágenes |

## Multi-tenancy

El `restaurantId` se deriva **siempre** del JWT (`rid`). Las consultas filtran por tenant;
los ids de otros restaurantes devuelven 404 (no 403) para no revelar existencia.

## Módulos

```
com.menusaas
├── auth/          # register, login, refresh (rotación), logout
├── users/         # usuarios del restaurante
├── restaurants/   # configuración del restaurante
├── categories/    # categorías del menú
├── products/      # productos
├── menus/         # menú público por slug (sin auth)
├── qr/            # QR PNG/PDF → app-base-url/menu/{slug}
├── subscriptions/ # planes y suscripción (manual en MVP)
├── files/         # upload de imágenes (local, extensible a Cloudinary)
├── shared/        # API envelope, errores, seguridad
└── config/        # security, CORS, OpenAPI, propiedades
```

## Tests

```bash
./mvnw test          # unitarios + integración (Testcontainers requiere Docker)
```