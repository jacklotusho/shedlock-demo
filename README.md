# shedlock-demo

A demo repository containing two Spring Boot implementations of a distributed task workflow using ShedLock and a multi-threaded worker pattern.

## Modules

### `jpa`
- Spring Boot + Spring Data JPA
- ShedLock provider: `shedlock-provider-jdbc-template`
- Default DB: H2 in-memory
- Optional DB: PostgreSQL via `postgres` profile
- Web stack: Spring MVC (`spring-boot-starter-web`)
- Core behavior: scheduler obtains a JDBC-backed lock, loads pending tasks, claims them with an atomic JPA update, and dispatches blocking worker threads to process task stages.

### `r2dbc`
- Spring Boot + Spring Data R2DBC
- ShedLock provider: `shedlock-provider-r2dbc`
- Default DB: H2 in-memory via R2DBC
- Optional DB: PostgreSQL via `postgres` profile
- Web stack: Spring WebFlux (`spring-boot-starter-webflux`)
- Core behavior: reactive controller and repository layers, with a scheduled bridge from reactive DB access to blocking worker threads and an atomic task claim via R2DBC.

## Why both modules exist

This repository demonstrates two approaches to the same problem:

- `jpa`: traditional blocking persistence with JPA and JDBC
- `r2dbc`: reactive persistence with R2DBC plus a blocking worker thread model

Both modules implement the same task workflow, ShedLock-based singleton scheduling, and duplicate-safe task creation.

## Running

Each module is a standalone Maven project.

From the module directory:

```bash
cd jpa
mvn spring-boot:run
```

or:

```bash
cd r2dbc
mvn spring-boot:run
```

For PostgreSQL support:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Quick API

Create a request:

```bash
curl -X POST http://localhost:8080/api/requests \
  -H "Content-Type: application/json" \
  -d '{"label":"server-01"}'
```

Get request progress:

```bash
curl http://localhost:8080/api/requests/{uuid}
```

Get task stats:

```bash
curl http://localhost:8080/api/tasks/stats
```

## Notes

- Both modules use Java 21.
- `jpa` is useful for JDBC-based applications and traditional Spring Boot services.
- `r2dbc` is useful for reactive service architectures that still need a blocking worker thread pool for long-running work.
