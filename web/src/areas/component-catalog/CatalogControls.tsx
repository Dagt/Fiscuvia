import { useState } from 'react'
import { ArrowRight, Plus, Search } from 'lucide-react'
import { Button } from '../../shared/ui/button'
import { Field } from '../../shared/ui/Field'
import { ConfirmationDialog, SideDrawer } from '../../shared/ui/Panels'
import { useNotification } from '../../shared/presentation/notification-context'

export function CatalogControls() {
  const notify = useNotification()
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [pendingDemo, setPendingDemo] = useState(false)
  return (
    <section
      className="catalog-section"
      id="controles"
      aria-labelledby="controls-title"
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">COMPONENTES P0</p>
          <h2 id="controls-title">Acciones claras. Formularios alineados.</h2>
          <p>Demostración local: nada se envía ni se guarda.</p>
        </div>
      </div>
      <div className="component-actions button-specimens">
        <Button
          onClick={() => notify('Acción primaria DEMO. No se guardaron datos.')}
        >
          <Plus size={18} aria-hidden="true" />
          Acción primaria
        </Button>
        <Button
          variant="secondary"
          onClick={() =>
            notify({ message: 'Acción secundaria DEMO.', tone: 'info' })
          }
        >
          Secundaria
        </Button>
        <Button
          variant="ghost"
          onClick={() =>
            notify({ message: 'Acción discreta DEMO.', tone: 'neutral' })
          }
        >
          Discreta
          <ArrowRight size={18} aria-hidden="true" />
        </Button>
        <ConfirmationDialog
          trigger={<Button variant="danger">Reiniciar DEMO</Button>}
          title="¿Reiniciar la demostración?"
          description="Se vaciará únicamente el campo de ejemplo de esta página. No afecta a ningún despacho."
          confirmLabel="Reiniciar ejemplo"
          destructive
          onConfirm={() => {
            setName('')
            setError('')
            notify('Ejemplo reiniciado. Ningún dato real fue modificado.')
          }}
        />
        <Button disabled>Deshabilitada</Button>
        <Button
          iconOnly
          variant="secondary"
          aria-label="Buscar en demostración"
          onClick={() =>
            notify('Búsqueda DEMO: utiliza el filtro de la tabla.')
          }
        >
          <Search size={20} aria-hidden="true" />
        </Button>
        <Button pending={pendingDemo} onClick={() => setPendingDemo(true)}>
          {pendingDemo ? 'Procesando DEMO…' : 'Mostrar estado pendiente'}
        </Button>
        {pendingDemo && (
          <Button variant="ghost" onClick={() => setPendingDemo(false)}>
            Restablecer estado pendiente
          </Button>
        )}
      </div>
      <form
        className="catalog-form"
        noValidate
        onSubmit={(event) => {
          event.preventDefault()
          if (name.trim().length < 3 || name.length > 100) {
            setError('Escribe entre 3 y 100 caracteres para este ejemplo.')
            document.getElementById('demo-name')?.focus()
          } else {
            setError('')
            notify({
              message: 'Validación DEMO completada. No se guardaron datos.',
              tone: 'success',
              action: { label: 'Limpiar ejemplo', onClick: () => setName('') },
            })
          }
        }}
      >
        <Field
          id="demo-name"
          label="Nombre del ejemplo demostrativo con una etiqueta extensa"
          required
          maxLength={100}
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={error}
          description="Sólo texto sintético. De 3 a 100 caracteres."
        />
        <div className="catalog-two-columns">
          <Field
            label="Referencia de sólo lectura"
            readOnly
            value="DEMO · Sin persistencia"
          />
          <Field
            label="Campo deshabilitado"
            disabled
            value="No disponible en esta demostración"
          />
        </div>
        <Button type="submit">Validar ejemplo</Button>
      </form>
      <SideDrawer
        title="Panel lateral DEMO"
        description="Información contextual sin abandonar la página."
        trigger={<Button variant="secondary">Abrir panel lateral</Button>}
        footer={<p>Referencia visual · Sin operación de negocio</p>}
      >
        <Field
          label="Nota demostrativa"
          description="El contenido se descarta al cerrar el panel."
        />
        <p>
          El foco permanece dentro del panel. Cierra con Escape y regresa al
          control que lo abrió.
        </p>
      </SideDrawer>
    </section>
  )
}
