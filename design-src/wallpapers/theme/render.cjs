// Re-renders the Phase 2 theme wallpapers from these SVG sources.
//   npm install @resvg/resvg-js@2.6.2
//   node design-src/wallpapers/theme/render.cjs
// Output: app/src/main/res/drawable-nodpi/theme_wall_*.png (1080 x 2400).
const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const input = __dirname;
const output = path.resolve(__dirname, '../../../app/src/main/res/drawable-nodpi');
for (const file of fs.readdirSync(input).filter((x) => x.endsWith('.svg'))) {
  const svg = fs.readFileSync(path.join(input, file), 'utf8');
  const image = new Resvg(svg, { fitTo: { mode: 'original' } }).render();
  const dest = path.join(output, file.replace(/\.svg$/, '.png'));
  fs.writeFileSync(dest, image.asPng());
  console.log(path.basename(dest), image.width, image.height, fs.statSync(dest).size);
}
