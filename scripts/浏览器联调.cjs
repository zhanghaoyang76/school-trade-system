// 本脚本使用真实后端和浏览器；联调凭据及会话只保存在被Git忽略的.qa-local。
const {chromium}=require('playwright');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const root=path.resolve(__dirname,'..'),local=path.join(root,'.qa-local'),output=path.join(root,'output/playwright');
fs.mkdirSync(local,{recursive:true});fs.mkdirSync(output,{recursive:true});
const mode=process.argv[2],actor=process.argv[3]||'admin',code=process.argv[4];
const fixturePath=path.join(local,'联调账号.json');
const fixture=fs.existsSync(fixturePath)?JSON.parse(fs.readFileSync(fixturePath)):Object.fromEntries(['seller','buyer'].map((role,i)=>[role,{username:`qa_${role}_${Date.now()}`,password:'Qa!'+crypto.randomUUID(),name:role==='seller'?'联调演示商家':'联调演示买家',phone:'1390000000'+(i+1),email:`qa_${role}@example.test`,city:'联调测试城市',gender:'UNSPECIFIED',bankAccount:'000000000000000'+(i+1),shopName:'联调演示店（示例商品）',role:role==='seller'?'MERCHANT':'USER'}]));
fs.writeFileSync(fixturePath,JSON.stringify(fixture,null,2));
const statePath=who=>path.join(local,who+'-session.json'),metaPath=who=>path.join(local,who+'-csrf.json');
async function api(context,url,method='GET',body,token){const response=await context.request.fetch('http://127.0.0.1:5173/api'+url,{method,headers:token?{'X-CSRF-TOKEN':token}:{},data:body});const data=await response.json();if(!response.ok())throw new Error(`${method} ${url} ${response.status()}: ${data.message}`);return data.data;}
(async()=>{const browser=await chromium.launch({channel:'msedge',headless:true});try{
 const context=await browser.newContext({storageState:fs.existsSync(statePath(actor))?statePath(actor):undefined,viewport:{width:1440,height:1100}});
 let meta=fs.existsSync(metaPath(actor))?JSON.parse(fs.readFileSync(metaPath(actor))):{};
 if(mode==='captcha'){
   meta=await api(context,'/auth/csrf');const captcha=await api(context,'/auth/captcha');fs.writeFileSync(path.join(local,actor+'-验证码.png'),Buffer.from(captcha.image.split(',')[1],'base64'));fs.writeFileSync(metaPath(actor),JSON.stringify(meta));await context.storageState({path:statePath(actor)});console.log('验证码图片已保存：'+actor);
 }else if(mode==='login'){
   let credentials=fixture[actor];if(actor==='admin'){const properties=Object.fromEntries(fs.readFileSync(path.join(root,'backend/config/application-local.properties'),'utf8').split(/\r?\n/).filter(l=>l.includes('=')&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return[l.slice(0,i).trim(),l.slice(i+1).trim()]}));credentials={username:properties['market.admin.username'],password:properties['market.admin.password']};}
   const user=await api(context,'/auth/login','POST',{username:credentials.username,password:credentials.password,captcha:code},meta.token);await context.storageState({path:statePath(actor)});console.log('真实登录成功，角色：'+user.role);
   const page=await context.newPage();await page.goto('http://127.0.0.1:5173'+(actor==='admin'?'/admin/registrations':'/'));await page.waitForTimeout(900);await page.screenshot({path:path.join(output,actor+'登录后页面.png'),fullPage:true});
 }else if(mode==='register'){
   const body={...fixture[actor],captcha:code};if(actor==='seller'){
     const page=await context.newPage();await page.setContent('<html lang="zh-CN"><body style="font:28px Microsoft YaHei;padding:45px;background:white"><h1>联调测试图片</h1><p>仅供系统注册审核流程测试</p><p>不是真实营业执照或身份证件</p></body></html>');const buffer=await page.screenshot();await page.close();
     for(const [purpose,key]of[['LICENSE','licenseFileId'],['IDENTITY','identityFileId']]){const response=await context.request.post('http://127.0.0.1:5173/api/files',{headers:{'X-CSRF-TOKEN':meta.token},multipart:{purpose,file:{name:'联调测试材料.png',mimeType:'image/png',buffer}}});const data=await response.json();if(!response.ok())throw new Error(data.message);body[key]=data.data.id;}
   }await api(context,'/auth/registrations','POST',body,meta.token);await context.storageState({path:statePath(actor)});console.log('真实注册提交成功（联调示例账号）');
 }else if(mode==='approve'){
   const rows=await api(context,'/admin/registrations');const records=Array.isArray(rows)?rows:rows.records;for(const who of['seller','buyer']){const user=records.find(u=>u.username===fixture[who].username);if(user){fixture[who].id=user.id;if(user.status==='PENDING')await api(context,'/admin/registrations/'+user.id+'/reviews','POST',{approved:true,reason:''},meta.token);}}fs.writeFileSync(fixturePath,JSON.stringify(fixture,null,2));console.log('联调账号管理员审核完成');
 }else if(mode==='products'){
   const categories=await api(context,'/categories');const c=Array.isArray(categories)?categories:categories.records;const definitions=[['耳机','无线头戴式耳机',89,'数码电子'],['书籍','Milk and Honey 英文诗集',25,'书籍教材'],['相机','入门微单相机与镜头',1680,'数码电子'],['自行车','通勤公路自行车',320,'运动户外'],['台灯','简约金属阅读灯',45,'生活用品'],['键盘','无线薄款键盘',120,'数码电子']];fixture.products=[];
   for(const [photo,name,price,cat]of definitions){const buffer=fs.readFileSync(path.join(root,'设计预览/素材',photo+'.jpg'));const upload=await context.request.post('http://127.0.0.1:5173/api/files',{headers:{'X-CSRF-TOKEN':meta.token},multipart:{purpose:'PRODUCT',file:{name:photo+'.jpg',mimeType:'image/jpeg',buffer}}});const u=await upload.json();if(!upload.ok())throw new Error(u.message);const p=await api(context,'/seller/products','POST',{name,categoryId:c.find(i=>i.name===cat)?.id||c[0].id,originalPrice:price*2,discount:0.5,price,size:'详见实物',description:'联调演示商品，不代表真实销售；示例照片用于课程功能验证。',instructions:'约定时间线下检查和交付。',negotiable:true,conditionText:'九成新',stock:3,imageIds:[u.data.id]},meta.token);fixture.products.push(p.id);}fs.writeFileSync(fixturePath,JSON.stringify(fixture,null,2));console.log('6件演示商品通过真实上传与发布接口提交审核');
 }else if(mode==='approve-products'){
   for(const id of fixture.products)await api(context,'/admin/products/'+id+'/reviews','POST',{approved:true,reason:''},meta.token);await api(context,'/admin/users/'+fixture.buyer.id+'/recharges','POST',{amount:5000,requestKey:crypto.randomUUID()},meta.token);console.log('演示商品审核及买家模拟钱包充值成功');
 }else if(mode==='screenshots'){
   const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto('http://127.0.0.1:5173/');await page.locator('.product-card').first().waitFor();await page.evaluate(()=>Promise.all([...document.images].map(i=>i.decode())));await page.screenshot({path:path.join(output,'正式页面桌面.png'),fullPage:true});await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(output,'正式页面手机.png'),fullPage:true});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw new Error('手机页面横向溢出');if(errors.length)throw new Error(errors.join(';'));console.log('真实商品桌面手机截图完成，未发现页面脚本错误或手机横向溢出');
 }else if(mode==='trade'){
   const p=await api(context,'/products/'+fixture.products[0]);const requestKey=crypto.randomUUID();const body={requestKey,points:0,items:[{productId:p.id,quantity:1}],appointments:[{shopId:p.shopId,appointmentTime:'2026-11-14T15:00:00',appointmentPlace:'联调测试：图书馆门口'}]};const batch=await api(context,'/checkouts','POST',body,meta.token);const again=await api(context,'/checkouts','POST',body,meta.token);if(batch.id!==again.id)throw new Error('结账幂等失败');fixture.batch=batch;fs.writeFileSync(fixturePath,JSON.stringify(fixture,null,2));console.log('真实MySQL结账及重复请求幂等通过');
 }else if(mode==='deliver'){
   const orders=await api(context,'/seller/orders');const order=(orders.records||orders).find(o=>o.batchId===fixture.batch.id);if(!order)throw new Error('未找到订单');fixture.orderId=order.id;fixture.itemId=order.items[0].id;await api(context,'/seller/orders/'+order.id+'/deliveries','POST',{},meta.token);fs.writeFileSync(fixturePath,JSON.stringify(fixture,null,2));console.log('真实卖家交付成功');
 }else if(mode==='return'){
   await api(context,'/orders/'+fixture.orderId+'/confirmations','POST',{},meta.token);await api(context,'/order-items/'+fixture.itemId+'/reviews','POST',{productStars:5,serviceStars:5,content:'联调流程验证：评价保存成功。'},meta.token);const item=await api(context,'/order-items/'+fixture.itemId+'/returns','POST',{reason:'联调测试：验证立即退款与交回库存'},meta.token);fixture.returnId=item?.id;fs.writeFileSync(fixturePath,JSON.stringify(fixture,null,2));console.log('真实确认收货、评价及退货申请成功');
 }else if(mode==='refund'){
   const rows=await api(context,'/seller/returns');const r=(rows.records||rows).find(r=>r.itemId===fixture.itemId);if(!r)throw new Error('未找到售后');await api(context,'/seller/returns/'+r.id+'/reviews','POST',{approved:true,reason:''},meta.token);await api(context,'/seller/returns/'+r.id+'/receipts','POST',{},meta.token);await api(context,'/seller/order-items/'+fixture.itemId+'/reviews','POST',{buyerStars:5,content:'联调验证：买家按约定完成退货。'},meta.token);console.log('真实退款、交回恢复库存及卖家评价完成');
 }else if(mode==='check'){
   const w=await api(context,'/wallet'),p=await api(context,'/products/'+fixture.products[0]),points=await api(context,'/points');if(Number(w.available)!==5000||Number(p.stock)!==3||Number(points.available)!==0||Number(points.frozen)!==0)throw new Error('退款后的余额库存积分不一致');const page=await context.newPage();await page.goto('http://127.0.0.1:5173/orders');await page.locator('.order-card').first().waitFor();await page.screenshot({path:path.join(output,'真实订单售后.png'),fullPage:true});console.log('MySQL端到端断言通过：钱包恢复5000元、库存恢复3件、积分无残留');
 }else if(mode==='ui-cancel'){
   const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
   await page.goto('http://127.0.0.1:5173/products/'+fixture.products[1]);
   await page.getByRole('button',{name:'加入购物车',exact:true}).click();
   await page.getByText('已加入购物车',{exact:true}).waitFor();
   await page.locator('header a[href="/cart"]').click();
   await page.getByRole('button',{name:'去结账',exact:true}).click();
   await page.getByPlaceholder('例如：图书馆正门').fill('联调测试：图书馆门口');
   const date=page.getByPlaceholder('选择线下交易时间');await date.fill('2026-11-14 16:00');await date.press('Tab');
   await page.getByRole('button',{name:'确认下单并扣款',exact:true}).click();
   await page.locator('.order-card').first().waitFor();
   await page.locator('.order-card').first().getByRole('button',{name:'取消订单',exact:true}).click();
   await page.locator('.el-message-box textarea').fill('联调测试：验证页面取消订单退款');
   await page.locator('.el-message-box').getByRole('button',{name:'提交',exact:true}).click();
   await page.getByText('已取消，货款和积分已退回',{exact:true}).waitFor();
   const w=await api(context,'/wallet');assertEqual(Number(w.available),5000,'页面取消订单后余额未恢复');
   if(errors.length)throw new Error(errors.join(';'));await page.screenshot({path:path.join(output,'真实页面下单取消.png'),fullPage:true});console.log('真实Vue商品加购→购物车→约定交付→下单→原因弹框取消→余额恢复通过');
 }else throw new Error('未知模式');
 await context.close();
}finally{await browser.close();}})().catch(e=>{console.error(e.message);process.exitCode=1});
function assertEqual(actual,expected,message){if(actual!==expected)throw new Error(message);}
