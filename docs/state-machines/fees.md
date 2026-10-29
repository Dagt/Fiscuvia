# Fee charges and payments (Proposed)

**Honorarios del despacho; no impuestos SAT.** Separar `fee_agreement`, `charge`, `payment`, `allocation`, `adjustment`.

Cargo: `DRAFT` -> `ISSUED` -> `PARTIALLY_PAID` -> `PAID`, o `VOIDED` por reversión autorizada. `OVERDUE` derivado de fecha + saldo, no estado que borra cargo. Recurrencia con idempotency key por acuerdo/período.

Comandos: IssueCharge, RecordFeePayment, AllocatePayment, ReversePayment, AdjustCharge, VoidCharge con permisos y logs. Money BigDecimal/MXN y ajustes compensatorios; no modificar pago viejo directamente.

Tests: parcialidades, sobrepago, reversión, concurrencia, periodos duplicados, saldo conciliado, autorización billing, reportes sólo del mismo tenant.
