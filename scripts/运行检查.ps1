$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'backend')
try{& mvn '-Dmaven.repo.local=.m2' test;if($LASTEXITCODE -ne 0){throw '后端测试失败。'}}finally{Pop-Location}
Push-Location (Join-Path $projectRoot 'frontend')
try{
  & npm.cmd test;if($LASTEXITCODE -ne 0){throw '前端检查失败。'}
  & npm.cmd run build;if($LASTEXITCODE -ne 0){throw '前端构建失败。'}
}finally{Pop-Location}
Write-Host '本次后端测试、前端检查和生产构建均已通过。浏览器及真实MySQL联调另见docs/联调与测试.md。'
