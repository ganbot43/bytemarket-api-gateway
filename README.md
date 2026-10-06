# bytemarket-api-gateway

Punto de entrada único (Spring Cloud Gateway). Todo `/api/**` entra por aquí y se reparte al microservicio que corresponda.

Parte del sistema **ByteMarket**, una tienda de repuestos y accesorios para
celulares construida con microservicios Spring Boot y un frontend Nuxt.

**Puerto 8085** · No usa base de datos

## Qué hace

Enruta por prefijo hacia los cuatro servicios de negocio, descubriéndolos por
Eureka (`lb://`). También:

- **Reenvía el JWT.** Si la petición no trae cabecera `Authorization`, la rellena con la cookie `jwt_token`. Si ya la trae, **no la pisa**: el proxy de Nuxt manda el token de la sesión y una cookie caducada invalidaría una sesión buena.
- **CORS** configurado para el frontend.

## Mapa de ruteo

| Prefijo | Destino |
|---|---|
| `/api/auth/**` `/api/users/**` `/api/mi-perfil` `/api/business-config` | user-service `:8081` |
| `/api/admin/users/**` `/api/admin/business-config` | user-service `:8081` |
| `/api/products/**` `/api/categories/**` `/api/subcategories/**` `/api/banners/**` `/api/landing/**` `/api/reviews/**` `/api/favorites/**` `/api/catalog/**` | catalog-service `:8082` |
| `/api/admin/products/**` `/api/admin/categories/**` `/api/admin/subcategories/**` `/api/admin/banners/**` `/api/admin/inventory/**` `/api/admin/reviews/**` `/api/upload/**` | catalog-service `:8082` |
| `/api/orders/**` `/api/my-orders/**` `/api/cart/**` `/api/payment-methods/**` `/api/coupons/**` | order-service `:8083` |
| `/api/admin/orders/**` `/api/admin/payment-methods/**` `/api/admin/coupons/**` `/api/admin/reports/**` `/api/admin/automation/**` | order-service `:8083` |
| `/api/admin/inventory/backfill` | order-service `:8083` |
| `/api/reclamaciones/**` `/api/complaints/**` | support-service `:8084` |
| `/api/admin/reclamaciones/**` | support-service `:8084` |

### El orden de las rutas importa

Spring Cloud Gateway evalúa en orden de declaración y gana la primera que
casa. Dos consecuencias:

- Las rutas `/api/admin/**` van **antes** que los comodines, porque catálogo
  y pedidos comparten ese prefijo.
- `/api/admin/inventory/backfill` se declara **la primera de todas**: lo
  sirve order-service (es dueño de los pedidos), pero el
  `/api/admin/inventory/**` de catálogo también lo capturaría.

### Lo que no se enruta, a propósito

`/api/internal/**` es la API que order-service consume de catalog-service por
Feign. **No tiene ruta en el gateway**: expone descuentos de stock y precios
sin autenticar, así que solo debe alcanzarse dentro del clúster.

## Cómo levantarlo

### Con Docker (recomendado)

Este servicio es una pieza del sistema; lo normal es levantarlo junto a los
demás desde la carpeta padre, que trae el `docker-compose.yml`:

```bash
cd ..
docker compose up -d
```

Para ver solo su log o reiniciarlo:

```bash
docker compose logs -f gateway
docker compose restart gateway
```

El `Dockerfile` de este repo es multietapa: compila con Maven y la imagen
final solo lleva el JRE y el jar. No hace falta empaquetar antes.

### A mano

Requisitos: **Java 17+** y `bytemarket-eureka-server` ya arrancado. No usa
base de datos.

```bash
./mvnw spring-boot:run
```

> La configuración sale del `.env` de la **carpeta padre**, uno solo para
> todos los servicios. Lo carga `spring-dotenv` gracias a
> `src/main/resources/.env.properties`, que apunta a `..`; al arrancar desde
> Eclipse el directorio de trabajo es esta carpeta, así que lo encuentra. Si
> falta, `JWT_SECRET` queda vacío: el servicio levanta igual pero rechaza
> cualquier token con 401 y sin dejar rastro en el log.


Queda escuchando en el puerto **8085**.


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
