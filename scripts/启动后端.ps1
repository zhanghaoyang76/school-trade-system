$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$backendPath=Join-Path $projectRoot 'backend'
if(-not(Test-Path (Join-Path $backendPath 'config/application-local.properties'))){throw '请先复制backend/config/application-local.example.properties为application-local.properties并填写本地数据库及管理员配置。'}
Push-Location $backendPath
try{
  if(-not(Test-Path 'target/market-1.0.0.jar')){
    & mvn '-Dmaven.repo.local=.m2' package
    if($LASTEXITCODE -ne 0){throw '后端构建失败，请查看上方错误。'}
  }
  Write-Host '正在启动后端：http://localhost:8080。保留此终端，按Ctrl+C停止。'
  & java -jar 'target/market-1.0.0.jar'
  if($LASTEXITCODE -ne 0){throw '后端启动失败，请检查数据库服务、连接配置及8080端口。'}
}finally{Pop-Location}
