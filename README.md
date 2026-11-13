# 校园二手商品交易平台

Java21、Spring Boot3.5.16、MyBatis Plus3.5.18、MySQL8、Vue3、Vite、Element Plus。前后端分离、Session登录；商品及证件图片保存在本地，证件通过权限接口访问。

## 开发与启动

1. 启动本机MySQL服务，创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS `2024011490` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 将`backend/config/application-local.example.properties`复制为同目录`application-local.properties`，填写MySQL连接与首次管理员账号密码。当前开发机器已有用户自行填写的真实配置，无需覆盖。真实配置不会被提交Git。
3. 在项目根目录的两个PowerShell终端分别启动：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\启动后端.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\启动前端.ps1
```

4. 打开[本地平台](http://localhost:5173)。后端8080，前端5173，API由Vite代理，所有浏览器操作通过前端地址进行。终端按Ctrl+C停止对应服务。

后端首次启动创建表、类别和平台账户；只有数据库不存在管理员且本地管理员配置符合规则时才创建管理员。管理员密码至少8位，已有管理员不随本地配置变化而重置。不会自动生成真实交易或伪造测试结果。

## 首次使用流程

先注册普通用户或商家 → 管理员审核账号 → 卖家登录上传商品图片并发布 → 管理员审核商品 → 管理员给买家模拟钱包充值 → 买家购物车结账并约定线下交付 → 卖家标记已交付 → 买家确认收货。

普通用户可作为个人卖家且免手续费。商家需提交营业执照及身份证图片，按等级费率收费。确认收货后的款项和奖励积分冻结24小时；退货审核通过立即退款，实际交回后恢复库存。详见[已确认需求](docs/需求确认记录.md)和[系统详细设计](docs/系统详细设计.md)。

## 检查与构建

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\运行检查.ps1
```

也可分别执行：

```powershell
cd backend
mvn -Dmaven.repo.local=.m2 test
mvn -Dmaven.repo.local=.m2 package
cd ..\frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
```

测试使用独立H2内存数据库，正式运行使用MySQL；不能用H2通过替代MySQL联调。`frontend/dist`为静态构建结果，`backend/target/market-1.0.0.jar`为后端可执行包。最终检查结果见[联调与测试](docs/联调与测试.md)。

## 目录与文档

- `backend/`：Controller、Service、Mapper与Entity、配置、JUnit集成测试。
- `frontend/`：Vue路由、公共组件、交易/卖家/后台页面、请求层及校验测试。
- `database/schema.sql`：本人学号数据库及表结构，应用也会幂等建表。
- `scripts/`：启动、验证及真实浏览器联调脚本。
- `docs/`：全部简体中文阶段记录、设计、验收、问题和讲解；从[阶段文档目录](docs/阶段文档目录.md)阅读。
- `设计预览/`：已选及备选设计草图与示例素材，属于开发设计记录。

真实凭据、上传文件、联调会话、缓存和构建产物均不提交Git。源码已按用户指定推送至[GitHub development分支](https://github.com/zhanghaoyang76/school-trade-system/tree/development)。实验报告正文由用户自行撰写。
