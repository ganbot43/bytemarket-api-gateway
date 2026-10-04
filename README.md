# bytemarket-api-gateway

Punto de entrada único (Spring Cloud Gateway). Todo `/api/**` entra por aquí y se reparte al microservicio que corresponda.

Parte del sistema **ByteMarket**, una tienda de repuestos y accesorios para
celulares construida con microservicios Spring Boot y un frontend Nuxt.

## Qué hace

Enruta por prefijo de ruta hacia los cuatro servicios de negocio, descubriéndolos por Eureka (`lb://`). También:

- **Reenvía el JWT.** Si la petición no trae cabecera `Authorization`, la rellena con la cookie `jwt_token`. Si ya la trae, **no la pisa**: el proxy de Nuxt manda el token de la sesión y una cookie caducada invalidaría una sesión buena.
- **CORS** configurado para el frontend.

Las rutas `/api/admin/**` se declaran **antes** que los comodines, porque
catálogo y pedidos comparten ese prefijo y el primero que casa gana.

`/api/internal/**` **no** se enruta a propósito: es la API que usan los
servicios entre sí y no debe quedar expuesta.

## Cómo levantarlo

Requisitos: **Java 17+**, **MySQL 8** en `localhost:3306` y el
`bytemarket-eureka-server` ya arrancado (salvo que este repo *sea* Eureka).

```bash
cp .env.example .env     # y rellena los valores
./mvnw spring-boot:run
```

Queda escuchando en el puerto **8085**. El esquema de base de datos se crea
solo al arrancar (`createDatabaseIfNotExist=true`).

## Configuración

Las credenciales se leen del `.env`, que **no se versiona**. Los
`application*.yml` solo traen marcadores: si falta el `.env`, los valores
sensibles quedan vacíos. Mira `.env.example` para saber qué rellenar.

> El `JWT_SECRET` debe ser **idéntico** en user, catalog, order y support:
> user-service firma el token y los demás verifican la firma. Si difieren,
> todas las peticiones autenticadas fallan con 401 sin dejar rastro en el log.

## El sistema completo

| Repositorio | Puerto | Función |
|---|---|---|
| `bytemarket-eureka-server` | 8761 | Registro de servicios |
| `bytemarket-api-gateway` | 8085 | Punto de entrada único; enruta a los demás |
| `bytemarket-user-service` | 8081 | Cuentas, autenticación JWT, perfiles |
| `bytemarket-catalog-service` | 8082 | Productos, categorías, banners, inventario |
| `bytemarket-order-service` | 8083 | Pedidos, métodos de pago, cupones, reportes |
| `bytemarket-support-service` | 8084 | Libro de reclamaciones |
| `frontend-bytemarket` | 3000 | Tienda y panel de administración (Nuxt 3) |

Orden de arranque: **Eureka primero**, luego los servicios de negocio, el
gateway al final y el frontend cuando el gateway responda.
