// Controlled smoke overdraw A/B (full quad vs SpriteTrim quad) in headless Chromium/WebGL.
// Usage (from the repo root, after `./gradlew :viltrumitecore:processResources`):
//   CHROME=/path/to/chrome node tools/perf/smoke-overdraw-bench/run.js "$(cat tools/perf/smoke-overdraw-bench/scenarios.json)"
// Prints one JSON line per scenario and writes smoke-bench-results.json in the working directory.
// SwiftShader is a CPU rasterizer: absolute ms are NOT game FPS, only the full/trim ratio and the pixel diff matter.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
(async () => {
  const tex = path.resolve(__dirname, '../../../viltrumitecore/build/resources/main/assets/viltrumitecore/textures/particle');
  const sprites = [...Array(8).keys()].map(i => 'data:image/png;base64,' + fs.readFileSync(path.join(tex, `meteor_smoke_${i}.png`)).toString('base64'));
  const browser = await chromium.launch({ executablePath: process.env.CHROME, args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage();
  await page.goto('file://' + path.join(__dirname, 'bench.html'));
  const out = [];
  for (const s of JSON.parse(process.argv[2])) {
    const r = await page.evaluate(([sp, o]) => window.run(sp, o), [sprites, s]);
    if (s.shot) fs.writeFileSync(s.shot, Buffer.from(r.screenshot.split(',')[1], 'base64'));
    delete r.screenshot; r.name = s.name; out.push(r); console.log(JSON.stringify(r));
  }
  fs.writeFileSync('smoke-bench-results.json', JSON.stringify(out, null, 1));
  await browser.close(); process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
