# Matriz inicial FR / BR / NFR / ADR -> Issues

**Status:** Proposed; el issue puede implementar subconjunto de requisito, nunca declarar FR satisfecho por existir issue.

| Requisitos | ADR / documentos | Issues ejecutables | Gate |
|---|---|---|---|
| FR-001..009; BR-001..005; NFR-001..009 | ADR-005/007, tenancy, roles | #26–33 | G1 |
| FR-010..019; BR-010..014 | schema taxpayers, domain | #34–37 | G2 |
| FR-020..032; BR-020..030 | ADR-010, state machines obligation/payment | #38–42, #44–45 | G2–3 |
| FR-040..045; BR-040..043 | task/review machines | #43–45 | G3 |
| FR-050..056; BR-050..054; NFR-006 | ADR-006, document machine | #46–48 | G3 |
| FR-060..066; BR-060..064 | fees state, schema | #49–51 | G3 |
| FR-070..075; NFR-030..036 | ADR-004/011, UX inventory | #52–56 | G4 |
| FR-080..083; BR-070..072 | CFDI/SAT doc, parser policy | #62–64 | Future |
| FR-084..085; BR-073..074 | ADR-009, OQ-009/018 | #65–67 | Future, blocking decision |
| FR-086..088; BR-075 | workflows | #68–69 | Future |
| FR-090..092 | portal UX | #57–59 | Future |
| FR-093..095; BR-080..082 | ADR-015, commercial | #60–61 | Future |
| NFR-010..026 | ADR-001/003/008/012 | #20–30, #46, #55, #70–72 | Cross-cutting |
| NFR-040..045 | ADR-012/013 | #23, #25, #70–72 | G1/G5 |
| OQ-001..006 | fiscal domain expert validation | #18, #38–45 | Before fiscal migrations |
| OQ-007/020 | commercial/brand validation | #18, #2 umbrella | Before launch |
| OQ-021 | screenshot baseline concept | #19, #52 | Before UI review |

## Criterios de rastreabilidad
Cada issue debe añadir subsección `References` con FR/BR/NFR/ADR pertinentes, dependencias numéricas y archivos exactos. Si un FR no tiene issue, registrar hueco aquí antes de Codex; si un issue no tiene FR/NFR, justificar tarea técnica/seguridad. No marcar requisito implementado sin PR merged y test verificable.
