# ===========================================================================
#  Jewellery Shop ERP - start the backend (development)
#
#  Runs the packaged jar directly rather than through the Maven plugin.
#  Two reasons: it is how the application runs in production, and the plugin's
#  forked JVM failed to start Tomcat's internal selector on this machine
#  ("Unable to establish loopback connection") while a plain java -jar works.
#
#  Build the jar first (or after any code change):
#      tools\apache-maven-3.9.9\bin\mvn.cmd -f backend package -DskipTests
#
#  Usage:   powershell -ExecutionPolicy Bypass -File start-backend.ps1
# ===========================================================================

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

$java = 'C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot\bin\java.exe'
$jar = Join-Path $root 'backend\target\jewellery-erp-1.0.0.jar'

if (-not (Test-Path $jar)) {
    Write-Error "Jar not found at $jar - run the package command above first."
}

# --- Secrets ---------------------------------------------------------------
# The database password and the JWT signing key are read from a file that is
# never committed. A key in version control is a key everyone has, and
# rotating it later invalidates every token in issue - easier not to publish
# it in the first place.
#
# First run on a new machine: copy secrets.local.ps1.example to
# secrets.local.ps1 and fill it in.
$secrets = Join-Path $root 'secrets.local.ps1'
if (-not (Test-Path $secrets)) {
    Write-Error "secrets.local.ps1 not found. Copy secrets.local.ps1.example to secrets.local.ps1 and fill it in."
}
. $secrets

foreach ($name in @('DB_PASSWORD', 'JWT_SECRET')) {
    if (-not [Environment]::GetEnvironmentVariable($name)) {
        Write-Error "$name is empty. Set it in secrets.local.ps1."
    }
}

# --- Database --------------------------------------------------------------
# Matches the role created by database\full-setup-dev.sql.
$env:DB_URL = 'jdbc:postgresql://localhost:5432/jewellery_erp'
$env:DB_USERNAME = 'jewellery'

# --- Security --------------------------------------------------------------
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
$env:SPRING_PROFILES_ACTIVE = 'dev'

# The setup script already created the admin account, so the bootstrap runner
# finds an administrator and does nothing. Harmless to leave enabled.
$env:INITIAL_ADMIN_ENABLED = 'true'

# --- AF_UNIX socket directory (machine-specific workaround) ----------------
# On Windows the JDK uses an AF_UNIX socket for the NIO selector's wakeup pipe.
# On this machine, creating that socket anywhere under %LOCALAPPDATA%\Temp
# fails with "Invalid argument: connect", so Tomcat cannot start:
#
#   java.io.IOException: Unable to establish loopback connection
#       at sun.nio.ch.PipeImpl$Initializer$LoopbackConnector.run
#
# Reproduced with a bare Selector.open() and no Spring involved, so this is an
# environment problem, not an application one - most likely security software
# or Controlled Folder Access guarding that directory. Pointing the JDK at a
# directory outside that tree fixes it. Verified: Pipe.open, Selector.open,
# 10x Selector.open, and bind :8080 all succeed with this set.
$socketTmp = Join-Path $root '.tmp'
New-Item -ItemType Directory -Force -Path $socketTmp | Out-Null

# --- Flyway ----------------------------------------------------------------
# The tables came from database\full-setup-dev.sql, not from Flyway, so Flyway
# is told the schema is already at V23 (what that script now builds) and should
# apply only later migrations. A database set up with the OLDER script already
# has its Flyway history row, so for it these flags do nothing. Once
# the baseline row exists these flags are a no-op, but they are harmless.
& $java `
    "-Djdk.net.unixdomain.tmpdir=$socketTmp" `
    '-Dspring.flyway.baseline-on-migrate=true' `
    '-Dspring.flyway.baseline-version=23' `
    '-jar' $jar
