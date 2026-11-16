[CmdletBinding()]
param([switch]$InstallOnly,[switch]$VerifyOnly,[string]$Root=(Join-Path $env:LOCALAPPDATA 'RustCraft-1.16.3'),[string]$JavaHome,[string]$CacheAssets)
$ErrorActionPreference='Stop'
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::DefaultConnectionLimit=24
$ProgressPreference='SilentlyContinue'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$Utf8=New-Object Text.UTF8Encoding($false)
function Say($s) { Write-Host $s -ForegroundColor Cyan }
function SaveText($p,$s) { [IO.File]::WriteAllText($p,$s,$Utf8) }
function Good($p,$hash,$alg='SHA1') { return ((Test-Path -LiteralPath $p) -and (!$hash -or (Get-FileHash -LiteralPath $p -Algorithm $alg).Hash -eq $hash)) }
function Fetch($url,$p,$hash='',$alg='SHA1',[int]$TimeoutSec=120,[int]$Attempts=3,[bool]$NoRedirect=$false) {
 if(Good $p $hash $alg){return}
 if(!$url){throw "Нет URL для $p. Переустановите Forge, удалив файл forge.ready."}
 [IO.Directory]::CreateDirectory((Split-Path $p))|Out-Null
 Write-Host ('Загрузка: '+[IO.Path]::GetFileName($p))
 for($attempt=1;$attempt -le $Attempts;$attempt++){
  try {
   if($NoRedirect){Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$p.part" -TimeoutSec $TimeoutSec -MaximumRedirection 0}else{Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$p.part" -TimeoutSec $TimeoutSec}
   if(!(Good "$p.part" $hash $alg)){throw "Контрольная сумма не совпала: $url"}
   Move-Item -LiteralPath "$p.part" -Destination $p -Force
   return
  } catch { if($attempt -eq $Attempts){throw}; Start-Sleep -Seconds 2 }
 }
}
function ReadJson($p) { Get-Content -LiteralPath $p -Raw -Encoding UTF8|ConvertFrom-Json }
function OwnedMod($name) { return $name -match '^rustcraft-1\.16\.3-\d+\.\d+\.\d+(?:\.\d+)?\.jar$' }
function ModVersion($name) { return [version]($name -replace '^rustcraft-1\.16\.3-','' -replace '\.jar$','') }
function InstallModFile($source,$destination,$hash,$mods) {
 # Test locks before publishing a replacement. A running game must never get two versions.
 $owned=@(Get-ChildItem -LiteralPath $mods -Filter 'rustcraft-*.jar'|Where-Object {OwnedMod $_.Name})
 foreach($old in $owned){try{$lock=[IO.File]::Open($old.FullName,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$lock.Dispose()}catch{throw 'Закрой Minecraft и повтори запуск: установленный мод занят игрой.'}}
 $staged=Join-Path $mods ('.rustcraft-update-'+[guid]::NewGuid().ToString('N')+'.tmp')
 try{
  Copy-Item -LiteralPath $source -Destination $staged
  if(!(Good $staged $hash 'SHA256')){throw 'Контрольная сумма подготовленного мода не совпала. Обновление отменено.'}
  if(Test-Path -LiteralPath $destination){$backup=$staged+'.previous';[IO.File]::Replace($staged,$destination,$backup);Remove-Item -LiteralPath $backup -Force}else{[IO.File]::Move($staged,$destination)}
  foreach($old in $owned){if($old.FullName -ne $destination){Remove-Item -LiteralPath $old.FullName -Force}}
 }catch{throw "Мод не заменён безопасно. Закрой Minecraft и повтори запуск. $($_.Exception.Message)"}finally{if(Test-Path -LiteralPath $staged){Remove-Item -LiteralPath $staged -Force}}
}
function UpdateRustCraft($config,$bundle,$rootPath) {
 $mods=Join-Path $rootPath 'game\mods'
 if(!(OwnedMod ([string]$config.modFile))){throw 'Некорректное имя мода в server.json.'}
 $installed=@(Get-ChildItem -LiteralPath $mods -Filter 'rustcraft-*.jar'|Where-Object {OwnedMod $_.Name}|Sort-Object @{Expression={ModVersion $_.Name};Descending=$true},LastWriteTime)
 $manifest=$null
 try{
  $endpoint=[uri]$config.updateUrl
  if(!$endpoint.IsAbsoluteUri -or $endpoint.Scheme -notin @('http','https') -or $endpoint.UserInfo){throw 'Некорректный адрес обновлений.'}
  $builder=New-Object UriBuilder($endpoint);$builder.Query='nocache='+[guid]::NewGuid().ToString('N')
  Say 'Проверка свежей версии RustCraft у хозяина...'
  $response=Invoke-WebRequest -UseBasicParsing -Uri $builder.Uri -TimeoutSec 2 -MaximumRedirection 0 -Headers @{'Cache-Control'='no-cache, no-store';'Pragma'='no-cache'}
 }catch{
  if($installed.Count){Write-Host 'Сервер обновлений недоступен. Сохраняю установленный мод; свежесть версии проверить не удалось.' -ForegroundColor Yellow;return}
  if(!(Test-Path -LiteralPath $bundle)){throw 'Сервер обновлений недоступен, установленного мода и JAR в комплекте нет.'}
  Write-Host 'Сервер обновлений недоступен. Ставлю версию из комплекта; она может отличаться от версии сервера. Подключись к Radmin и запусти PLAY ещё раз.' -ForegroundColor Yellow
  $bundleHash=(Get-FileHash -LiteralPath $bundle -Algorithm SHA256).Hash
  InstallModFile $bundle (Join-Path $mods $config.modFile) $bundleHash $mods
  return
 }
 try{$manifest=$response.Content|ConvertFrom-Json}catch{throw 'Сервер обновлений вернул некорректный JSON. Установленный мод сохранён.'}
 $version=[string]$manifest.version;$file=[string]$manifest.file;$sha=[string]$manifest.sha256
 if($version -notmatch '^\d+\.\d+\.\d+(?:\.\d+)?$' -or $file -cne "rustcraft-1.16.3-$version.jar" -or !(OwnedMod $file) -or $sha -notmatch '^[a-fA-F0-9]{64}$'){throw 'Некорректное имя, версия или SHA256 мода в manifest.json. Установленный мод сохранён.'}
 try{$download=[uri]$manifest.url}catch{throw 'Некорректный URL мода в manifest.json.'}
 $sameHttp=$download.Scheme -eq 'http' -and $endpoint.Scheme -eq 'http' -and $download.Host -eq $endpoint.Host -and $download.Port -eq $endpoint.Port
 if(!$download.IsAbsoluteUri -or $download.UserInfo -or $download.Fragment -or ($download.Scheme -ne 'https' -and !$sameHttp)){throw 'Адрес JAR должен использовать HTTPS или тот же HTTP-сервер, что и updateUrl.'}
 if($installed.Count -and (ModVersion $installed[0].Name) -gt [version]$version){Write-Host "Установленный RustCraft новее версии $version на сервере обновлений. Откат не выполняется." -ForegroundColor Yellow;return}
 $destination=Join-Path $mods $file
 if(Good $destination $sha 'SHA256'){
  foreach($old in $installed){if($old.FullName -ne $destination){try{Remove-Item -LiteralPath $old.FullName -Force}catch{throw 'Закрой Minecraft и повтори запуск: старая версия мода занята игрой.'}}}
  Say "RustCraft $version актуален. Скачивание не требуется."
  return
 }
 $cache=Join-Path $rootPath ('cache\mod-'+$sha.ToLowerInvariant()+'.jar')
 try{Fetch ([string]$download.AbsoluteUri) $cache $sha 'SHA256' 30 1 $true}catch{
  if($_.Exception.Message -like '*Контрольная сумма*'){throw}
  if($installed.Count){Write-Host 'Обновление не скачалось. Установленный мод сохранён; версия может отличаться от серверной. Повтори PLAY, когда хозяин будет доступен.' -ForegroundColor Yellow;return}
  if(!(Test-Path -LiteralPath $bundle)){throw}
  Write-Host 'Свежий мод недоступен. Временно устанавливаю версию из комплекта. Для обновления повтори PLAY через Radmin.' -ForegroundColor Yellow
  InstallModFile $bundle (Join-Path $mods $config.modFile) (Get-FileHash -LiteralPath $bundle -Algorithm SHA256).Hash $mods
  return
 }
 InstallModFile $cache $destination $sha $mods
 Say "RustCraft автоматически обновлён до $version."
}
function Allowed($o) {
 if(!$o.rules){return $true}; $ok=$false
 foreach($r in $o.rules){
  $match=$true
  if($r.os){
   if($r.os.name -and $r.os.name -ne 'windows'){$match=$false}
   if($r.os.arch -and $r.os.arch -ne 'x86_64'){$match=$false}
   if($r.os.version -and [Environment]::OSVersion.Version.ToString() -notmatch $r.os.version){$match=$false}
  }
  if($r.features){$match=$false}
  if($match){$ok=($r.action -eq 'allow')}
 }; return $ok
}
function ExtractNatives($jar,$dest) {
 $z=[IO.Compression.ZipFile]::OpenRead($jar)
 try{foreach($e in $z.Entries){if($e.FullName -match '\.(dll)$'){
  [IO.Compression.ZipFileExtensions]::ExtractToFile($e,(Join-Path $dest $e.Name),$true)
 }}}finally{$z.Dispose()}
}
function ExpandArgs($items,$vars){
 foreach($item in $items){
  if($item -is [string]){$values=@($item)}elseif(Allowed $item){$values=@($item.value)}else{continue}
  foreach($s in $values){foreach($key in $vars.Keys){$s=$s.Replace('${'+$key+'}',[string]$vars[$key])}; $s}
 }
}
function QuoteArg([string]$s) {
 if($s -notmatch '[\s"]' -and $s.Length){return $s}
 return '"'+([regex]::Replace(([regex]::Replace($s,'(\\*)"','$1$1\"')),'(\\+)$','$1$1'))+'"'
}
try {
 if(![Environment]::Is64BitOperatingSystem){throw 'Требуется Windows x64.'}
 $Root=[IO.Path]::GetFullPath($Root)
 $config=ReadJson (Join-Path $PSScriptRoot 'server.json')
 # Keep private Radmin address outside the shared package and repository.
 $addressFile=Join-Path $Root 'radmin-host.txt'
 if (!(Test-Path -LiteralPath $Root)) { [IO.Directory]::CreateDirectory($Root)|Out-Null }
 $address=if (Test-Path -LiteralPath $addressFile) { (Get-Content -LiteralPath $addressFile -Raw).Trim() } else { '' }
 if (!$address) { $address=Read-Host 'Введите Radmin IPv4 хоста (спросите у владельца сервера)' }
 $parsed=$null
 if (![Net.IPAddress]::TryParse($address,[ref]$parsed) -or $parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork -or !$address.StartsWith('26.')) { throw 'Нужен IPv4-адрес Radmin VPN, начинающийся с 26.' }
 SaveText $addressFile $address
 $config.host=$address; $config.updateUrl="http://${address}:25577/manifest.json"
 $mod=Join-Path $PSScriptRoot $config.modFile
 if(!(OwnedMod ([string]$config.modFile))){throw 'Некорректное имя мода в server.json.'}
 foreach($dir in @($Root,"$Root\cache","$Root\versions\1.16.3","$Root\libraries","$Root\assets\indexes","$Root\natives","$Root\game\mods")){[IO.Directory]::CreateDirectory($dir)|Out-Null}
 $readyPath=Join-Path $Root 'install-v2.ready.json'
 $fastReady=$false;$ready=$null
 if(!$VerifyOnly -and !$CacheAssets -and (Test-Path -LiteralPath $readyPath)){
  try{
   $ready=ReadJson $readyPath
   $fastReady=$ready.schema -eq 2 -and @($ready.classpath).Count -gt 20 -and @($ready.essential).Count -gt 20
   if($JavaHome -and [IO.Path]::GetFullPath((Join-Path $JavaHome 'bin\java.exe')) -ne [IO.Path]::GetFullPath([string]$ready.java)){$fastReady=$false}
   foreach($essential in @($ready.essential)){if(!(Test-Path -LiteralPath $essential -PathType Leaf) -or (Get-Item -LiteralPath $essential).Length -eq 0){$fastReady=$false;break}}
  }catch{$fastReady=$false}
 }
 if(!$fastReady){
 Say 'RustCraft: проверка Java 8 и файлов игры. Первая установка требует интернет и несколько минут.'
 if($JavaHome){$java=Join-Path $JavaHome 'bin\java.exe'}else{
  $java=Join-Path $Root 'runtime\jdk8u512-b09-jre\bin\java.exe'
  if(!(Test-Path -LiteralPath $java)){
   $zip=Join-Path $Root 'cache\java8.zip'
   Fetch 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u512-b09/OpenJDK8U-jre_x64_windows_hotspot_8u512b09.zip' $zip 'ae1ae12f817d7735477c6555107eece73854da0654da69077309ef9bff0b78bf' 'SHA256'
   [IO.Directory]::CreateDirectory("$Root\runtime")|Out-Null
   [IO.Compression.ZipFile]::ExtractToDirectory($zip,"$Root\runtime")
  }
 }
 if(!(Test-Path -LiteralPath $java)){throw "Не найдена Java: $java"}
 $basePath="$Root\versions\1.16.3\1.16.3.json"
 if(!(Test-Path -LiteralPath $basePath)){
  Fetch 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json' "$Root\cache\manifest.json"
  $entry=(ReadJson "$Root\cache\manifest.json").versions|Where-Object id -eq '1.16.3'
  Fetch $entry.url $basePath $entry.sha1
 }
 $base=ReadJson $basePath
 $client="$Root\versions\1.16.3\1.16.3.jar"
 Fetch $base.downloads.client.url $client $base.downloads.client.sha1
 $forgePath="$Root\versions\1.16.3-forge-34.1.42\1.16.3-forge-34.1.42.json"
 $patched="$Root\libraries\net\minecraftforge\forge\1.16.3-34.1.42\forge-1.16.3-34.1.42-client.jar"
 $bootstrap="$Root\libraries\net\minecraftforge\forge\1.16.3-34.1.42\forge-1.16.3-34.1.42.jar"
 if(!(Test-Path -LiteralPath "$Root\forge.ready") -or !(Test-Path -LiteralPath $forgePath) -or !(Good $patched 'df0a4d2091de117d2e6d35500d321c19437d21fc') -or !(Good $bootstrap '289a994a836574cc8664d8cc2af173120e543e4a')){
  Say 'Установка Forge 34.1.42 (обработка файлов может занять несколько минут)...'
  Fetch 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.16.3-34.1.42/forge-1.16.3-34.1.42-installer.jar' "$Root\cache\forge-installer.jar" '10e3c85aa71f9f3b92a2c129c66f312e74e0c6df'
  if(!(Test-Path -LiteralPath "$Root\launcher_profiles.json")){SaveText "$Root\launcher_profiles.json" '{"profiles":{}}'}
  Push-Location "$Root\cache"
  try{
   & $java '-Xmx1G' '-jar' "$Root\cache\forge-installer.jar" '--installClient' $Root *> "$Root\forge-install.log"
   if($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $forgePath)){throw "Forge не установился. Журнал: $Root\forge-install.log"}
  }finally{Pop-Location}
  if(!(Good $patched 'df0a4d2091de117d2e6d35500d321c19437d21fc')){throw 'Forge: контрольная сумма обработанного клиента не совпала.'}
  SaveText "$Root\forge.ready" '34.1.42'
 }
 $forge=ReadJson $forgePath
 # ModLauncher 8.0.6 shipped with Forge fails with modern Java 8 updates.
 $ml=$forge.libraries|Where-Object name -like 'cpw.mods:modlauncher:*'
 $ml.name='cpw.mods:modlauncher:8.1.3'
 $ml.downloads.artifact.path='cpw/mods/modlauncher/8.1.3/modlauncher-8.1.3.jar'
 $ml.downloads.artifact.url='https://maven.minecraftforge.net/cpw/mods/modlauncher/8.1.3/modlauncher-8.1.3.jar'
 $ml.downloads.artifact.sha1='6d0bfd78e025a87d43985e30907aa9ecbc51b2d6'
 SaveText $forgePath ($forge|ConvertTo-Json -Depth 80)
 Say 'Проверка библиотек и нативных компонентов...'
 $classpath=New-Object 'Collections.Generic.List[string]'
 $seen=@{}
 foreach($lib in (@($forge.libraries)+@($base.libraries))){
  if(!(Allowed $lib)){continue}
  $parts=$lib.name.Split(':'); $key=$parts[0]+':'+$parts[1]
  $a=$lib.downloads.artifact
  if($a -and !$seen.ContainsKey($key)){$p=Join-Path "$Root\libraries" $a.path; Fetch $a.url $p $a.sha1; $classpath.Add($p); $seen[$key]=$true}
  if($lib.natives -and $lib.natives.windows){
   $c=$lib.natives.windows.Replace('${arch}','64'); $n=$lib.downloads.classifiers.$c
   if(!$n){throw "Не найден Windows native: $($lib.name)"}
   $p=Join-Path "$Root\libraries" $n.path; Fetch $n.url $p $n.sha1; ExtractNatives $p "$Root\natives"
  }
 }
 $classpath.Add($client)
 Say 'Проверка ресурсов Minecraft (звук, языки и текстуры)...'
 $index="$Root\assets\indexes\$($base.assetIndex.id).json"
 Fetch $base.assetIndex.url $index $base.assetIndex.sha1
 $assets=ReadJson $index
 $pending=New-Object 'Collections.Generic.List[object]'
 $objects=@($assets.objects.PSObject.Properties)
 $count=0
 foreach($o in $objects){
  $h=$o.Value.hash; $rel='objects\'+$h.Substring(0,2)+'\'+$h; $p=Join-Path "$Root\assets" $rel
  if(!(Good $p $h)){
   [IO.Directory]::CreateDirectory((Split-Path $p))|Out-Null
   if($CacheAssets -and (Good (Join-Path $CacheAssets $rel) $h)){Copy-Item -LiteralPath (Join-Path $CacheAssets $rel) -Destination $p}else{
    $wc=New-Object Net.WebClient
    $task=$wc.DownloadFileTaskAsync([Uri]('https://resources.download.minecraft.net/'+$h.Substring(0,2)+'/'+$h),"$p.part")
    $pending.Add([pscustomobject]@{Client=$wc;Task=$task;Path=$p;Hash=$h})
   }
  }
  $count++
  if($pending.Count -ge 16 -or $count -eq $objects.Count){
   foreach($job in $pending){try{$job.Task.GetAwaiter().GetResult()}catch{Write-Host 'Повторная загрузка ресурса...'}finally{$job.Client.Dispose()}
    if(Good ($job.Path+'.part') $job.Hash){Move-Item -LiteralPath ($job.Path+'.part') -Destination $job.Path -Force}else{Fetch ('https://resources.download.minecraft.net/'+$job.Hash.Substring(0,2)+'/'+$job.Hash) $job.Path $job.Hash}
   }; $pending.Clear()
   if($count%128 -lt 16){Write-Host "Ресурсы: $count / $($objects.Count)"}
  }
 }
 $essential=@($classpath)+@($java,$basePath,$forgePath,$index,$patched,$bootstrap,"$Root\forge.ready")+@(Get-ChildItem -LiteralPath "$Root\natives" -Filter '*.dll'|ForEach-Object FullName)
 SaveText $readyPath (([ordered]@{schema=2;java=$java;basePath=$basePath;forgePath=$forgePath;classpath=@($classpath);essential=$essential})|ConvertTo-Json -Depth 10)
 }else{
  Say 'Игра уже установлена. Быстрая проверка готовых файлов; полная проверка ресурсов не требуется.'
  $java=[string]$ready.java;$basePath=[string]$ready.basePath;$forgePath=[string]$ready.forgePath
  $base=ReadJson $basePath;$forge=ReadJson $forgePath
  $classpath=New-Object 'Collections.Generic.List[string]'
  foreach($entry in @($ready.classpath)){$classpath.Add([string]$entry)}
 }
 UpdateRustCraft $config $mod $Root
 if(!(Test-Path -LiteralPath "$Root\game\options.txt")){SaveText "$Root\game\options.txt" "lang:ru_ru`nrenderDistance:8`nmaxFps:120`n"}
 if($InstallOnly){Say 'Установка завершена. Для игры запустите PLAY-RustCraft.bat.'; exit 0}
 $nickPath="$Root\nickname.txt"
 if($VerifyOnly){$nick='RustCraftTest'}else{
  $previous=if(Test-Path -LiteralPath $nickPath){(Get-Content -LiteralPath $nickPath -Raw).Trim()}else{'Player21'}
  do{$nick=Read-Host "Ваш ник (3-16 латинских букв, цифр или _, Enter = $previous)"; if(!$nick){$nick=$previous}}while($nick -notmatch '^[A-Za-z0-9_]{3,16}$')
  SaveText $nickPath $nick
 }
 $md5=[Security.Cryptography.MD5]::Create()
 try{$uuidBytes=$md5.ComputeHash([Text.Encoding]::UTF8.GetBytes('OfflinePlayer:'+$nick))}finally{$md5.Dispose()}
 $uuidBytes[6]=($uuidBytes[6] -band 15) -bor 48; $uuidBytes[8]=($uuidBytes[8] -band 63) -bor 128
 $uuid=([BitConverter]::ToString($uuidBytes)).Replace('-','').ToLowerInvariant()
 $vars=@{auth_player_name=$nick;version_name='1.16.3-forge-34.1.42';game_directory="$Root\game";assets_root="$Root\assets";assets_index_name=$base.assetIndex.id;auth_uuid=$uuid;auth_access_token='0';user_type='legacy';version_type='release';natives_directory="$Root\natives";launcher_name='RustCraftLAN';launcher_version='1';classpath=($classpath -join ';');user_properties='{}';library_directory="$Root\libraries";classpath_separator=';'}
 $jvm=@(ExpandArgs $base.arguments.jvm $vars)+@('-Xms512M','-Xmx2G','-XX:+UseG1GC','-Dfile.encoding=UTF-8')
 if($base.logging.client){
  $logConfig=$base.logging.client.file
  $logPath=Join-Path "$Root\assets\log_configs" $logConfig.id
  Fetch $logConfig.url $logPath $logConfig.sha1
  $jvm+=@($base.logging.client.argument.Replace('${path}',$logPath))
 }
 $game=@(ExpandArgs $base.arguments.game $vars)+@(ExpandArgs $forge.arguments.game $vars)+@('--server',[string]$config.host,'--port',[string]$config.port)
 $args=@($jvm)+@($forge.mainClass)+@($game)
 foreach($arg in $args){if($arg -match '\$\{'){throw "Необработанный аргумент: $arg"}}
 SaveText "$Root\launch-check.json" (([ordered]@{java=$java;classpathFiles=$classpath.Count;mainClass=$forge.mainClass;server=($config.host+':'+$config.port);arguments=$args})|ConvertTo-Json -Depth 10)
 if($VerifyOnly){
  if(@(Get-ChildItem -LiteralPath "$Root\natives" -Filter '*.dll').Count -lt 6){throw 'Не установлены нативные библиотеки Windows.'}
  & $java '-cp' (($classpath -join ';')+';'+$PSScriptRoot) 'VerifyClasspath' *> "$Root\verify-java.log"
  if($LASTEXITCODE -ne 0){throw "Проверка Java/ModLauncher не прошла: $Root\verify-java.log"}
  Say "Проверка готова: $($classpath.Count) файлов classpath, Java и ModLauncher запускаются. Окно игры не открывалось."; exit 0
 }
 Say "Подключитесь к сети хозяина в Radmin VPN. Запуск Minecraft, сервер $($config.host):$($config.port)..."
 $tcp=New-Object Net.Sockets.TcpClient
 try{$t=$tcp.ConnectAsync([string]$config.host,[int]$config.port);if(!$t.Wait(2500) -or !$tcp.Connected){Write-Host 'Сервер пока недоступен. Проверьте Radmin VPN и запуск сервера у хозяина.' -ForegroundColor Yellow}}catch{Write-Host 'Сервер пока недоступен. Проверьте Radmin VPN.' -ForegroundColor Yellow}finally{$tcp.Dispose()}
 $psi=New-Object Diagnostics.ProcessStartInfo
 $psi.FileName=$java; $psi.Arguments=(($args|ForEach-Object{QuoteArg $_}) -join ' '); $psi.WorkingDirectory="$Root\game"; $psi.UseShellExecute=$false
 $proc=[Diagnostics.Process]::Start($psi); $proc.WaitForExit()
 if($proc.ExitCode -ne 0){throw "Игра завершилась с кодом $($proc.ExitCode). Журнал: $Root\game\logs\latest.log"}
} catch { Write-Host "ОШИБКА: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
