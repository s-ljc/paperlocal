$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:\Users\MR\.dsh\ungrouped\tools\jdk21\jdk-21.0.12.1+1'
$env:TMP = 'C:\Users\MR\AppData\Local\Temp'
$env:TEMP = 'C:\Users\MR\AppData\Local\Temp'
$env:GRADLE_USER_HOME = 'C:\Users\MR\.gradle'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location 'C:\Users\MR\.dsh\ungrouped\paperlocal'
& 'C:\Users\MR\.dsh\ungrouped\tools\gradle-8.7\bin\gradle.bat' assembleDebug --no-daemon --console=plain 2>&1 | Select-Object -Last 40
"EXITCODE=$LASTEXITCODE"
