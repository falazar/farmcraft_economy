const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const root = path.resolve(__dirname, '..');
const gems = {
  ruby: [213, 38, 69],
  opal: [164, 225, 228],
  sapphire: [43, 101, 220],
  topaz: [239, 161, 37],
  peridot: [149, 191, 46],
};

function writeJson(file, data) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n');
}

function mergeTag(file, values) {
  const data = fs.existsSync(file) ? JSON.parse(fs.readFileSync(file, 'utf8')) : { replace: false, values: [] };
  const ids = new Set(data.values.map(value => typeof value === 'string' ? value : value.id));
  for (const value of values) if (!ids.has(value)) data.values.push(value);
  writeJson(file, data);
}

function crc32(data) {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const name = Buffer.from(type);
  const size = Buffer.alloc(4);
  size.writeUInt32BE(data.length);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([name, data])));
  return Buffer.concat([size, name, data, crc]);
}

function writePng(file, pixel) {
  const scanlines = Buffer.alloc(16 * 65);
  for (let y = 0; y < 16; y++) {
    for (let x = 0; x < 16; x++) {
      const color = pixel(x, y);
      color.forEach((v, i) => { scanlines[y * 65 + 1 + x * 4 + i] = Math.max(0, Math.min(255, Math.round(v))); });
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(16, 0);
  header.writeUInt32BE(16, 4);
  header[8] = 8;
  header[9] = 6;
  const png = Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    pngChunk('IHDR', header), pngChunk('IDAT', zlib.deflateSync(scanlines)), pngChunk('IEND', Buffer.alloc(0)),
  ]);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, png);
}

function facet(color, x, y, opal) {
  const light = x + y < 12 ? 1.35 : x < 8 ? 1.05 : 0.7;
  const shade = opal ? [((x * 13 + y * 7) % 5) * 9, ((x + y) % 4) * 5, ((x * 3 + y) % 4) * 8] : [0, 0, 0];
  return color.map((c, i) => c * light + shade[i]).concat(255);
}

function itemPixel(gem, color, x, y) {
  const shapes = {
    ruby: x >= 4 && x <= 11 && y >= 3 && y <= 12 && Math.abs(x - 7.5) + Math.abs(y - 7.5) < 7,
    opal: ((x - 7.5) ** 2) / 14 + ((y - 7.5) ** 2) / 26 <= 1,
    sapphire: Math.abs(x - 7.5) + Math.abs(y - 7.5) <= 6,
    topaz: x >= 5 && x <= 10 && y >= 2 && y <= 13 && (y > 3 && y < 12 || x > 5 && x < 10),
    peridot: Math.abs(x - 7.5) + Math.abs(y - 7.5) * 0.8 <= 5,
  };
  if (!shapes[gem]) return [0, 0, 0, 0];
  if (x === 6 && y === 5 || x === 7 && y === 4) return [244, 250, 245, 255];
  return facet(color, x, y, gem === 'opal');
}

function orePixel(color, x, y, deepslate, opal) {
  const spots = [[3, 3], [11, 4], [6, 10], [12, 12]];
  for (const [sx, sy] of spots) {
    if (Math.abs(x - sx) + Math.abs(y - sy) <= 2) return facet(color, x - sx + 6, y - sy + 5, opal);
  }
  const noise = ((x * 43 + y * 29 + x * y * 11) % 29) - 14;
  const base = deepslate ? 64 : 123;
  const layer = deepslate && y % 4 === 0 ? -12 : 0;
  return [base + noise + layer, base + noise + layer, base + noise + layer + (deepslate ? 4 : 0), 255];
}

for (const target of ['forge-1.20.1', 'neoforge-26.2']) {
  const neo = target.startsWith('neoforge');
  const resources = path.join(root, target, 'src', 'main', 'resources');
  const assets = path.join(resources, 'assets', 'farmupcraft');
  const data = path.join(resources, 'data');
  const blockTag = neo ? 'block' : 'blocks';
  const itemTag = neo ? 'item' : 'items';
  const lootPath = neo ? 'loot_table' : 'loot_tables';
  const ores = [];
  writeJson(path.join(data, 'farmupcraft', 'tags', blockTag, 'gem_replaceable.json'), {
    replace: false,
    values: [
      '#minecraft:stone_ore_replaceables', '#minecraft:deepslate_ore_replaceables', '#minecraft:dirt',
      'minecraft:sand', 'minecraft:red_sand', 'minecraft:gravel', 'minecraft:clay',
      { id: neo ? '#c:stones' : '#forge:stone', required: false },
    ],
  });
  const langFile = path.join(assets, 'lang', 'en_us.json');
  const lang = fs.existsSync(langFile) ? JSON.parse(fs.readFileSync(langFile, 'utf8')) : {};
  for (const [gem, color] of Object.entries(gems)) {
    const title = gem[0].toUpperCase() + gem.slice(1);
    lang[`item.farmupcraft.${gem}`] = title;
    writePng(path.join(assets, 'textures', 'item', `${gem}.png`), (x, y) => itemPixel(gem, color, x, y));
    writeJson(path.join(assets, 'models', 'item', `${gem}.json`), { parent: 'minecraft:item/generated', textures: { layer0: `farmupcraft:item/${gem}` } });
    if (neo) writeJson(path.join(assets, 'items', `${gem}.json`), { model: { type: 'minecraft:model', model: `farmupcraft:item/${gem}` } });
    for (const deepslate of [false, true]) {
      const name = `${deepslate ? 'deepslate_' : ''}${gem}_ore`;
      const id = `farmupcraft:${name}`;
      ores.push(id);
      lang[`block.farmupcraft.${name}`] = `${deepslate ? 'Deepslate ' : ''}${title} Ore`;
      writePng(path.join(assets, 'textures', 'block', `${name}.png`), (x, y) => orePixel(color, x, y, deepslate, gem === 'opal'));
      writeJson(path.join(assets, 'blockstates', `${name}.json`), { variants: { '': { model: `farmupcraft:block/${name}` } } });
      writeJson(path.join(assets, 'models', 'block', `${name}.json`), { parent: 'minecraft:block/cube_all', textures: { all: `farmupcraft:block/${name}` } });
      writeJson(path.join(assets, 'models', 'item', `${name}.json`), { parent: `farmupcraft:block/${name}` });
      if (neo) writeJson(path.join(assets, 'items', `${name}.json`), { model: { type: 'minecraft:model', model: `farmupcraft:item/${name}` } });
      const enchantments = [{ enchantments: 'minecraft:silk_touch', levels: { min: 1 } }];
      const predicate = neo ? { predicates: { 'minecraft:enchantments': enchantments } } : { enchantments };
      writeJson(path.join(data, 'farmupcraft', lootPath, 'blocks', `${name}.json`), {
        type: 'minecraft:block',
        pools: [{
          rolls: 1,
          entries: [{
            type: 'minecraft:alternatives',
            children: [
              { type: 'minecraft:item', name: id, conditions: [{ condition: 'minecraft:match_tool', predicate }] },
              { type: 'minecraft:item', name: `farmupcraft:${gem}`, functions: [
                { function: 'minecraft:apply_bonus', enchantment: 'minecraft:fortune', formula: 'minecraft:ore_drops' },
                { function: 'minecraft:explosion_decay' },
              ] },
            ],
          }],
        }],
        random_sequence: `farmupcraft:blocks/${name}`,
      });
    }
    const namespace = neo ? 'c' : 'forge';
    mergeTag(path.join(data, namespace, 'tags', itemTag, 'gems', `${gem}.json`), [`farmupcraft:${gem}`]);
    mergeTag(path.join(data, namespace, 'tags', itemTag, 'gems.json'), [`farmupcraft:${gem}`]);
    mergeTag(path.join(data, namespace, 'tags', blockTag, 'ores', `${gem}.json`), [`farmupcraft:${gem}_ore`, `farmupcraft:deepslate_${gem}_ore`]);
  }
  writeJson(langFile, lang);
  mergeTag(path.join(data, 'minecraft', 'tags', blockTag, 'mineable', 'pickaxe.json'), ores);
  mergeTag(path.join(data, 'minecraft', 'tags', blockTag, 'needs_diamond_tool.json'), ores);
  mergeTag(path.join(data, neo ? 'c' : 'forge', 'tags', blockTag, 'ores.json'), ores);
}
console.log('Generated original 16x16 gem art, models, loot, localization, and mining tags for both targets.');
