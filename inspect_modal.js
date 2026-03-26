const puppeteer = require('puppeteer');
(async () => {
  const browser = await puppeteer.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'], headless: true });
  const page = await browser.newPage();
  await page.setViewport({ width: 1280, height: 900 });
  await page.goto('http://localhost:9001', { waitUntil: 'domcontentloaded', timeout: 15000 });
  await page.evaluate(() => {
    const modal = document.getElementById('starter-modal');
    if (modal) modal.classList.add('d-none');
  });
  await new Promise(r => setTimeout(r, 2000));
  await page.evaluate(() => {
    const btns = Array.from(document.querySelectorAll('button, [role=button]'));
    const walletBtn = btns.find(el => el.textContent && el.textContent.includes('Connect'));
    if (walletBtn) walletBtn.click();
  });
  await new Promise(r => setTimeout(r, 2500));
  const structure = await page.evaluate(() => {
    const modal = document.querySelector('w3m-modal') || document.querySelector('appkit-modal');
    if (!modal) return 'no modal element found';
    const sr = modal.shadowRoot;
    if (!sr) return 'no shadowRoot';
    return sr.innerHTML.substring(0, 4000);
  });
  console.log(structure);
  await browser.close();
})().catch(e => { console.error(e.message); process.exit(1); });
