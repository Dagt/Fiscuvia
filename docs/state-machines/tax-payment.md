# Tax payment workflow (Proposed)

**Este flujo representa el impuesto que paga el contribuyente al SAT, NO los honorarios que paga al despacho.**

Estados: `NOT_REQUIRED` (obligación sin pago según reglas revisadas), `PENDING`, `PARTIALLY_RECORDED` (si aplica) y `PAYMENT_RECORDED`; fuente y fecha de verificación explícitas. Política final de parcialidades OQ-004.

| Comando | Precondición | Efecto |
|---|---|---|
| SetPaymentRequired | autorizado, base fiscal revisada | PENDING |
| RecordTaxPayment | evidencia, actor, fecha e importe | apéndice payment_event, saldo actualizado |
| CorrectPayment | motivo + usuario autorizado | evento de ajuste reversible |
| MarkNotRequired | motivo y política autorizada | NOT_REQUIRED con evidencia/razón |

Invariantes: nunca derivar pagado de 'presentada'; no mezclar con cargo de honorarios; dinero como BigDecimal; eventos/acuse no equivalen a cobro conciliado/validado SAT. Fechas LocalDate y timestamp de registro separados.

Pruebas: dos pagos parciales, intento de duplicar evidencia, pagos sin permiso, exceso/saldo no calculado, uso de condición sin pago, reversión y auditoría.
