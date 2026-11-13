// 仅验证本地设计草图，不代表正式业务功能通过测试。
const { chromium } = require('playwright');
const path = require('path');
const { pathToFileURL } = require('url');
const fs = require('fs');
(async () => {
  const output = path.resolve('output/playwright');
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({channel:'msedge',headless:true});
  const results = [];
  try {
    for (const name of ['Airbnb','Shopify']) {
      const page = await browser.newPage({viewport:{width:1440,height:1100},deviceScaleFactor:1});
      const errors=[];
      page.on('pageerror', e=>errors.push(e.message));
      await page.goto(pathToFileURL(path.resolve('设计预览', name+'参考方案.html')).href);
      await page.locator('.product img').first().waitFor();
      await page.evaluate(()=>Promise.all([...document.images].map(i=>i.decode())));
      if(await page.locator('.product').count()!==6)throw new Error(name+'商品数量错误');
      await page.screenshot({path:path.join(output,name+'商品浏览.png'),fullPage:true});
      await page.selectOption('#sort','low');
      if(!(await page.locator('.product-title').first().textContent()).includes('Milk'))throw new Error('排序失败');
      const categoryButtons=page.locator(name==='Airbnb'?'#categories button':'#sideCategories button');
      await categoryButtons.filter({hasText:'书籍教材'}).click();
      if(await page.locator('.product').count()!==1)throw new Error('分类失败');
      await page.locator('.image-button').first().click();
      if(!await page.locator('#modal').isVisible())throw new Error('详情失败');
      await page.screenshot({path:path.join(output,name+'商品详情.png')});
      await page.keyboard.press('Escape');
      if(await page.locator('#modal').isVisible())throw new Error('关闭详情失败');
      await categoryButtons.filter({hasText:'全部商品'}).click();
      await page.locator('footer button').filter({hasText:'卖家中心'}).click();
      await page.screenshot({path:path.join(output,name+'卖家中心.png'),fullPage:true});
      await page.locator('.worktabs button').filter({hasText:'待审核'}).click();
      if(await page.locator('#sellerRows tr').count()!==1)throw new Error('卖家状态筛选失败');
      await page.locator('footer button').filter({hasText:'管理后台'}).click();
      await page.screenshot({path:path.join(output,name+'管理后台.png'),fullPage:true});
      await page.locator('footer button').filter({hasText:'商品市场'}).click();
      await page.selectOption('#sort','default');
      const search=page.locator('form.search:visible input').first();
      await search.fill('耳机');
      await search.press('Enter');
      if(await page.locator('.product').count()!==1)throw new Error('搜索失败');
      await search.fill('');await search.press('Enter');
      await page.setViewportSize({width:390,height:844});
      await page.screenshot({path:path.join(output,name+'手机浏览.png'),fullPage:true});
      const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
      if(overflow)throw new Error(name+'手机布局横向溢出');
      if(errors.length)throw new Error(errors.join(';'));
      results.push({scheme:name,products:6,photosDecoded:true,search:true,sort:true,category:true,detail:true,sellerFilter:true,mobileOverflow:false,pageErrors:errors});
      await page.close();
    }
    fs.writeFileSync(path.join(output,'草图验证结果.json'),JSON.stringify(results,null,2));
    console.log(JSON.stringify(results,null,2));
  } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
