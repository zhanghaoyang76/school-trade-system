$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'frontend')
try{
  if(-not(Test-Path 'node_modules')){
    & npm.cmd ci
    if($LASTEXITCODE -ne 0){throw '前端依赖安装失败。'}
  }
  Write-Host '正在启动前端：http://localhost:5173。保留此终端，按Ctrl+C停止。'
  & npm.cmd run dev
}finally{Pop-Location}
