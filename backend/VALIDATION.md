# Evidencia local — issue #20

Fecha: 2026-10-29, America/Mexico_City. Entorno: Windows 11 amd64, PowerShell, Oracle JDK 25.0.3. Base: `origin/main` en `ce561482fda7889b005deaad3d822395b89a82df` (PR #1 integrado). El SHA final de esta entrega se registra en el PR Draft; estos comandos permiten verificarlo junto a los resultados del checkout.

## Comandos ejecutados y resultados

Desde `backend/`:

```powershell
java -version
.\mvnw.cmd --version
.\mvnw.cmd -B -ntp clean verify
.\mvnw.cmd -B -ntp dependency:tree '-Dincludes=org.junit.jupiter:*,org.springframework.modulith:*,com.tngtech.archunit:*,org.springframework:spring-core' '-DoutputFile=target/dependency-versions.txt'
Get-FileHash target/fiscuvia-backend-0.0.1-SNAPSHOT.jar -Algorithm SHA256
```

| Verificación | Resultado local |
|---|---|
| Java / Maven reales | 25.0.3 / 3.9.16; Enforcer pasó ambos rangos |
| Compilación | `javac release 25`, recursos y empaquetado Spring Boot exitosos |
| `ScaffoldRuntimeConfigurationTests` | 7 ejecuciones: perfiles local/test admitidos; perfiles desconocidos rechazados |
| `FiscuviaApplicationTests` | 16 ejecuciones: arranque test, HTTP real, health/probes, endpoints ausentes y errores sin detalles internos |
| `ModuleBoundariesTests` | 3 pruebas: módulo real verificado; acceso interno y ciclos rechazados sobre fixtures negativos |
| `FiscuviaApplicationIT` | 3 pruebas: JAR local saludable; perfil prod y puerto inválido fallan con salida no cero |
| Total | **29 ejecuciones, 0 fallos, 0 errores, 0 omitidas** por `clean verify` |
| Dependencias resueltas | Boot 4.1.2, Framework 7.0.10, Modulith 2.1.2, Jupiter 6.0.3, ArchUnit 1.4.2 |
| Artefacto | JAR ejecutable; sin fixtures, clases de prueba ni `application-test.yml` |

La prueba de arranque no depende de DB, OIDC, Docker, SAT ni servicios externos. La resolución inicial de herramientas/dependencias sí necesita Maven Central.

## Reproducibilidad del artefacto

Se ejecutaron dos `clean verify` consecutivos sobre el mismo código, con todas las pruebas habilitadas, comparando con `Get-FileHash` después de cada build. Ambos produjeron:

```text
SHA-256 de5f602aa78649fddea1efc888584ce54baabdd5496c93a5b3a705c02f532ae8
```

Reproducción, conservando el resultado anterior antes del segundo build:

```powershell
$firstHash = (Get-FileHash target/fiscuvia-backend-0.0.1-SNAPSHOT.jar -Algorithm SHA256).Hash
.\mvnw.cmd -B -ntp clean verify
$secondHash = (Get-FileHash target/fiscuvia-backend-0.0.1-SNAPSHOT.jar -Algorithm SHA256).Hash
if ($firstHash -ne $secondHash) { throw 'JAR reproducibility comparison failed' }
```

Esta comparación cubre el entorno local indicado, no todas las combinaciones de OS/JDK. El código fuente y POM no cambiaron entre builds.

## Maven Wrapper

- Scripts oficiales generados con `org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.9.16 -Dtype=only-script`, mediante Maven 3.9.11 disponible como bootstrap. Los builds finales usan exclusivamente el wrapper 3.9.16.
- La distribución ZIP se descargó desde Maven Central y se comparó con su checksum oficial SHA-512. Su SHA-256, fijado en `.mvn/wrapper/maven-wrapper.properties`, es `5af3b743dd8b876b5c45da33b676251e5f1687712644abb4ee519ca56e1d89ce`.
- `.\mvnw.cmd --version`: descarga/ejecución exitosa en PowerShell, Maven 3.9.16 y Java 25.0.3.
- `& 'C:\Program Files\Git\bin\bash.exe' ./mvnw --version`: descarga/ejecución exitosa del script POSIX bajo Git Bash en Windows; no se afirma prueba en Linux/macOS nativos.
- Prueba negativa: se copiaron `mvnw.cmd` y sus propiedades a un directorio temporal aislado, se sustituyó `distributionSha256Sum` por 64 ceros y se apuntó `MAVEN_USER_HOME` a una caché nueva; `.\mvnw.cmd --version` salió con código 1 y `Failed to validate Maven distribution SHA-256`. No se alteraron las propiedades ni la caché del proyecto.
- `mvnw` se registra como ejecutable en Git; `.gitattributes` fija LF para POSIX y CRLF para `.cmd`.

## Revisión de alcance y limitaciones

Desde la raíz se revisan los archivos con:

```powershell
git diff --check
git diff --cached --check
git diff --cached --stat
git status --short
git rev-parse HEAD
```

Sólo se añade el scaffold de #20, convenciones y evidencia. `.gitignore` permite específicamente `backend/pom.xml`, conservando la prohibición general de XML/fixtures fiscales. No hay secretos, datos reales, migraciones, dependencias de DB, entidades, repositorios, lógica fiscal ni pantallas nuevas. La revisión local de archivos no equivale a un escáner de seguridad/dependencias independiente.

Java 25 muestra un aviso de carga dinámica de Byte Buddy al inicializar el soporte de Mockito de Spring Test y un aviso de CDS. Son avisos de herramientas de prueba, sin fallos ni omisiones; no se han ocultado con flags JVM. El JAR independiente arranca sin necesitar ese agente de pruebas.

**REMOTE CI NOT VERIFIED.** No hay workflows en este checkout; su implementación pertenece a #23. No se atribuye un bloqueo a billing sin evidencia, ni se declara CI verde. El estado remoto del SHA final se registra en el PR. No hay UI que requiera capturas, datos tenant-owned para probar RLS, comandos de dominio que auditar ni migraciones que validar en esta issue. Estos requisitos siguen pendientes en sus entregas respectivas.

Los requisitos aplicables de #20 están implementados localmente; su aceptación final depende de revisión humana del PR Draft. Sin merge, despliegue, cierre manual de issues ni Ready for Review.
