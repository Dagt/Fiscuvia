# Índice de Issues y dependencias

**Estado:** backlog propuesto; ninguna tarea equivale a implementación entregada. [Documentación PR #1](https://github.com/Dagt/Fiscuvia/pull/1) debe revisarse antes de comenzar desarrollo.

## Primer orden recomendado
1. Aprobar PR #1 y consultar épica [#2](https://github.com/Dagt/Fiscuvia/issues/2).
2. [#20 Backend](https://github.com/Dagt/Fiscuvia/issues/20), [#21 Frontend](https://github.com/Dagt/Fiscuvia/issues/21), [#22 PostgreSQL](https://github.com/Dagt/Fiscuvia/issues/22), [#23 CI](https://github.com/Dagt/Fiscuvia/issues/23).
3. [#26 RLS](https://github.com/Dagt/Fiscuvia/issues/26) -> [#27 OIDC](https://github.com/Dagt/Fiscuvia/issues/27) -> [#28 Autorización](https://github.com/Dagt/Fiscuvia/issues/28) -> [#30 Tests multitenant](https://github.com/Dagt/Fiscuvia/issues/30).
4. [#52 Design System/AppShell](https://github.com/Dagt/Fiscuvia/issues/52) y [#56 Evidencia visual](https://github.com/Dagt/Fiscuvia/issues/56), antes de implementar pantallas CRUD.
5. [#31 Provisioning](https://github.com/Dagt/Fiscuvia/issues/31) -> [#34 Contribuyentes](https://github.com/Dagt/Fiscuvia/issues/34) -> [#38 Plantillas](https://github.com/Dagt/Fiscuvia/issues/38) -> [#40 Periodos](https://github.com/Dagt/Fiscuvia/issues/40) -> [#43 Tareas](https://github.com/Dagt/Fiscuvia/issues/43) -> [#44 Revisión](https://github.com/Dagt/Fiscuvia/issues/44), con documentos [#46](https://github.com/Dagt/Fiscuvia/issues/46) y honorarios [#49](https://github.com/Dagt/Fiscuvia/issues/49).
6. [#70 Backup](https://github.com/Dagt/Fiscuvia/issues/70), [#71 Staging](https://github.com/Dagt/Fiscuvia/issues/71), [#72 UAT](https://github.com/Dagt/Fiscuvia/issues/72) antes del piloto con datos reales.
7. **Después del MVP:** portal #57–59, billing #60–61, CFDI #62–64, SAT #65–67, conciliación #68–69; SAT bloqueado por decisión de llaves.

## Épicas y issues hijos

### [E00 · #2](https://github.com/Dagt/Fiscuvia/issues/2)
- [ ] [#18 — Entrevistas y posicionamiento](https://github.com/Dagt/Fiscuvia/issues/18)
- [ ] [#19 — Referencias UX aprobables](https://github.com/Dagt/Fiscuvia/issues/19)

### [E01 · #3](https://github.com/Dagt/Fiscuvia/issues/3)
- [ ] [#20 — Scaffold Spring Boot/Modulith](https://github.com/Dagt/Fiscuvia/issues/20)
- [ ] [#21 — Scaffold React/Vite](https://github.com/Dagt/Fiscuvia/issues/21)
- [ ] [#22 — PostgreSQL, Docker, Flyway](https://github.com/Dagt/Fiscuvia/issues/22)
- [ ] [#23 — CI](https://github.com/Dagt/Fiscuvia/issues/23)
- [ ] [#24 — OpenAPI y errores](https://github.com/Dagt/Fiscuvia/issues/24)
- [ ] [#25 — Observabilidad](https://github.com/Dagt/Fiscuvia/issues/25)

### [E02 · #4](https://github.com/Dagt/Fiscuvia/issues/4)
- [ ] [#26 — RLS y contexto tenant](https://github.com/Dagt/Fiscuvia/issues/26)
- [ ] [#27 — OIDC, sesiones y roles](https://github.com/Dagt/Fiscuvia/issues/27)
- [ ] [#28 — Autorización por expediente](https://github.com/Dagt/Fiscuvia/issues/28)
- [ ] [#29 — Auditoría/outbox](https://github.com/Dagt/Fiscuvia/issues/29)
- [ ] [#30 — Pruebas multitenant](https://github.com/Dagt/Fiscuvia/issues/30)

### [E03 · #5](https://github.com/Dagt/Fiscuvia/issues/5)
- [ ] [#31 — Provisioning de despachos](https://github.com/Dagt/Fiscuvia/issues/31)
- [ ] [#32 — Planes y límites](https://github.com/Dagt/Fiscuvia/issues/32)
- [ ] [#33 — Colaboradores/invitaciones](https://github.com/Dagt/Fiscuvia/issues/33)

### [E04 · #6](https://github.com/Dagt/Fiscuvia/issues/6)
- [ ] [#34 — API contribuyentes](https://github.com/Dagt/Fiscuvia/issues/34)
- [ ] [#35 — Directorio visual](https://github.com/Dagt/Fiscuvia/issues/35)
- [ ] [#36 — Ficha de cliente](https://github.com/Dagt/Fiscuvia/issues/36)
- [ ] [#37 — Importación de Excel](https://github.com/Dagt/Fiscuvia/issues/37)

### [E05 · #7](https://github.com/Dagt/Fiscuvia/issues/7)
- [ ] [#38 — Plantillas de obligaciones](https://github.com/Dagt/Fiscuvia/issues/38)
- [ ] [#39 — Configuración por contribuyente](https://github.com/Dagt/Fiscuvia/issues/39)
- [ ] [#40 — Ocurrencias idempotentes](https://github.com/Dagt/Fiscuvia/issues/40)
- [ ] [#41 — Calendario/lista/matriz](https://github.com/Dagt/Fiscuvia/issues/41)
- [ ] [#42 — Vencimientos y certificados](https://github.com/Dagt/Fiscuvia/issues/42)

### [E06 · #8](https://github.com/Dagt/Fiscuvia/issues/8)
- [ ] [#43 — Tareas](https://github.com/Dagt/Fiscuvia/issues/43)
- [ ] [#44 — Aprobación/revisión](https://github.com/Dagt/Fiscuvia/issues/44)
- [ ] [#45 — Presentación/pago separados](https://github.com/Dagt/Fiscuvia/issues/45)

### [E07 · #9](https://github.com/Dagt/Fiscuvia/issues/9)
- [ ] [#46 — Storage R2/S3](https://github.com/Dagt/Fiscuvia/issues/46)
- [ ] [#47 — Upload y seguridad](https://github.com/Dagt/Fiscuvia/issues/47)
- [ ] [#48 — UX documentos](https://github.com/Dagt/Fiscuvia/issues/48)

### [E08 · #10](https://github.com/Dagt/Fiscuvia/issues/10)
- [ ] [#49 — Honorarios recurrentes](https://github.com/Dagt/Fiscuvia/issues/49)
- [ ] [#50 — Pagos y aging](https://github.com/Dagt/Fiscuvia/issues/50)
- [ ] [#51 — UX cobranza](https://github.com/Dagt/Fiscuvia/issues/51)

### [E09 · #11](https://github.com/Dagt/Fiscuvia/issues/11)
- [ ] [#52 — Design tokens/AppShell](https://github.com/Dagt/Fiscuvia/issues/52)
- [ ] [#53 — Dashboard](https://github.com/Dagt/Fiscuvia/issues/53)
- [ ] [#54 — Búsqueda](https://github.com/Dagt/Fiscuvia/issues/54)
- [ ] [#55 — Reportes](https://github.com/Dagt/Fiscuvia/issues/55)
- [ ] [#56 — Pruebas visuales](https://github.com/Dagt/Fiscuvia/issues/56)

### [E10 · #12](https://github.com/Dagt/Fiscuvia/issues/12)
- [ ] [#57 — Identidad portal](https://github.com/Dagt/Fiscuvia/issues/57)
- [ ] [#58 — Solicitudes cliente](https://github.com/Dagt/Fiscuvia/issues/58)
- [ ] [#59 — UX portal](https://github.com/Dagt/Fiscuvia/issues/59)

### [E11 · #13](https://github.com/Dagt/Fiscuvia/issues/13)
- [ ] [#60 — Billing](https://github.com/Dagt/Fiscuvia/issues/60)
- [ ] [#61 — Panel suscripciones](https://github.com/Dagt/Fiscuvia/issues/61)

### [E12 · #14](https://github.com/Dagt/Fiscuvia/issues/14)
- [ ] [#62 — Parser XML](https://github.com/Dagt/Fiscuvia/issues/62)
- [ ] [#63 — CFDI índice/estado](https://github.com/Dagt/Fiscuvia/issues/63)
- [ ] [#64 — UX CFDI](https://github.com/Dagt/Fiscuvia/issues/64)

### [E13 · #15](https://github.com/Dagt/Fiscuvia/issues/15)
- [ ] [#65 — Spike SAT/llaves](https://github.com/Dagt/Fiscuvia/issues/65)
- [ ] [#66 — Cliente oficial](https://github.com/Dagt/Fiscuvia/issues/66)
- [ ] [#67 — Jobs SAT](https://github.com/Dagt/Fiscuvia/issues/67)

### [E14 · #16](https://github.com/Dagt/Fiscuvia/issues/16)
- [ ] [#68 — Estados bancarios](https://github.com/Dagt/Fiscuvia/issues/68)
- [ ] [#69 — Conciliación asistida](https://github.com/Dagt/Fiscuvia/issues/69)

### [E15 · #17](https://github.com/Dagt/Fiscuvia/issues/17)
- [ ] [#70 — Backup/restore](https://github.com/Dagt/Fiscuvia/issues/70)
- [ ] [#71 — Staging/rollback](https://github.com/Dagt/Fiscuvia/issues/71)
- [ ] [#72 — UAT/piloto](https://github.com/Dagt/Fiscuvia/issues/72)

## Reglas
- Una tarea por PR Draft; no merges automáticos.
- Cualquier cambio de frontend requiere screenshots reales y revisión de diseño.
- Los issues de SAT/CFDI no habilitan custodia de credenciales sin ADR, consentimiento y controles aprobados.
- El orden puede cambiar tras investigación, pero no se omiten las dependencias técnicas/security gates.
