param([switch]$Rmi, [switch]$Screens, [switch]$Map3D, [switch]$Admin)
$ErrorActionPreference = "Stop"
$project = Split-Path $PSScriptRoot -Parent
Push-Location $project
try {
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/java.exe" } else { "java" }
    $repository = Join-Path $env:USERPROFILE ".m2/repository/com/fasterxml/jackson/core"
    $paths = @("target/classes")
    foreach ($name in @("jackson-databind","jackson-core","jackson-annotations")) {
        $paths += (Join-Path $repository "$name/2.17.2/$name-2.17.2.jar")
    }
    if ($Admin) {
        $modules = Get-ChildItem (Join-Path $env:USERPROFILE ".m2/repository/org/openjfx") -Recurse -Include "*25.0.1-win.jar","jdk-jsobject-25.0.1*.jar"
        $fx = $modules.FullName -join ";"
        & $java "-Djavafx.cachedir=$env:TEMP/lorax-javafx-cache" --module-path $fx --add-modules javafx.controls,javafx.web --enable-native-access=javafx.graphics,javafx.web -cp ($paths -join ";") verification/AdminScreens.java
        if ($LASTEXITCODE -ne 0) { throw "Administration pages verification failed." }
        return
    }
    if ($Map3D) {
        $modules = Get-ChildItem (Join-Path $env:USERPROFILE ".m2/repository/org/openjfx") -Recurse -Include "*25.0.1-win.jar","jdk-jsobject-25.0.1*.jar"
        $fx = $modules.FullName -join ";"
        & $java "-Djavafx.cachedir=$env:TEMP/lorax-javafx-cache" --module-path $fx --add-modules javafx.controls,javafx.web --enable-native-access=javafx.graphics,javafx.web -cp ($paths -join ";") verification/Map3DScreens.java
        if ($LASTEXITCODE -ne 0) { throw "3D Forest Map verification failed." }
        return
    }
    if ($Screens) {
        if ($Rmi) { throw "Run the RMI and screen checks separately." }
        $modules = Get-ChildItem (Join-Path $env:USERPROFILE ".m2/repository/org/openjfx") -Recurse -Include "*25.0.1-win.jar","jdk-jsobject-25.0.1*.jar"
        $fx = $modules.FullName -join ";"
        & $java "-Djavafx.cachedir=$env:TEMP/lorax-javafx-cache" "-Dcom.sun.webkit.useHTTP2Loader=false" --module-path $fx --add-modules javafx.controls,javafx.web --enable-native-access=javafx.graphics,javafx.web -cp ($paths -join ";") verification/RegistryScreens.java
        if ($LASTEXITCODE -ne 0) { throw "Registry screen verification failed." }
        return
    }
    $options = if ($Rmi) { @("--rmi") } else { @() }
    & $java -cp ($paths -join ";") verification/RegistryWorkflow.java @options
    if ($LASTEXITCODE -ne 0) { throw "Registry verification failed." }
} finally { Pop-Location }
