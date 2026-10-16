import { createHash } from 'node:crypto';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const labDir = path.dirname(scriptDir);
const planPath = path.join(scriptDir, 'catalog-plan.json');
const dataDir = path.join(labDir, 'scraped-data');
const dataImagesDir = path.join(dataDir, 'images');
const textureImagesDir = path.join(labDir, 'custom-catalogs', 'textures', 'images');
const textureCatalogPath = path.join(labDir, 'custom-catalogs', 'textures', 'PluginTexturesCatalog.properties');

const userAgent = 'ElSalab-SweetHome3D-PoC/0.1 (small internal evaluation sample)';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function decodeHtml(value = '') {
  const named = new Map([
    ['amp', '&'], ['quot', '"'], ['apos', "'"], ['lt', '<'], ['gt', '>'], ['nbsp', ' '],
  ]);
  return value.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (match, entity) => {
    if (entity[0] === '#') {
      const hex = entity[1].toLowerCase() === 'x';
      const number = Number.parseInt(entity.slice(hex ? 2 : 1), hex ? 16 : 10);
      return Number.isFinite(number) ? String.fromCodePoint(number) : match;
    }
    return named.get(entity.toLowerCase()) ?? match;
  });
}

function stripTags(value = '') {
  return decodeHtml(value)
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]+>/g, ' ')
    .replace(/[\t\r ]+/g, ' ')
    .replace(/\n\s+/g, '\n')
    .trim();
}

function htmlAttribute(tag, name) {
  const match = tag.match(new RegExp(`\\b${name}\\s*=\\s*(["'])(.*?)\\1`, 'i'));
  return match ? decodeHtml(match[2]) : null;
}

async function fetchResponse(url, attempts = 3) {
  let lastError;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetch(url, {
        redirect: 'follow',
        headers: { 'user-agent': userAgent, accept: 'text/html,image/avif,image/webp,image/apng,image/*,*/*;q=0.8' },
        signal: AbortSignal.timeout(30_000),
      });
      if (!response.ok) throw new Error(`HTTP ${response.status} ${response.statusText}`);
      return response;
    } catch (error) {
      lastError = error;
      if (attempt < attempts) await sleep(750 * attempt);
    }
  }
  throw lastError;
}

async function fetchText(url) {
  return (await fetchResponse(url)).text();
}

function extractProductLinks(html) {
  const links = [];
  for (const match of html.matchAll(/<a\b[^>]*product-item-link[^>]*>/gi)) {
    const href = htmlAttribute(match[0], 'href');
    if (href?.startsWith('https://www.ahmedelsallab.com/ar/') && !links.includes(href)) links.push(href);
  }
  return links;
}

function metaContent(html, key, value) {
  for (const tag of html.matchAll(/<meta\b[^>]*>/gi)) {
    if (htmlAttribute(tag[0], key)?.toLowerCase() === value.toLowerCase()) {
      return htmlAttribute(tag[0], 'content');
    }
  }
  return null;
}

function extractAttributes(html) {
  const attributes = {};
  const tableMatch = html.match(/<table\b[^>]*id=["']product-attribute-specs-table["'][^>]*>([\s\S]*?)<\/table>/i);
  if (!tableMatch) return attributes;
  for (const row of tableMatch[1].matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi)) {
    const label = row[1].match(/<th\b[^>]*>([\s\S]*?)<\/th>/i);
    const data = row[1].match(/<td\b[^>]*>([\s\S]*?)<\/td>/i);
    if (label && data) attributes[stripTags(label[1])] = stripTags(data[1]);
  }
  return attributes;
}

function findAttribute(attributes, fragment) {
  const entry = Object.entries(attributes).find(([key]) => key.includes(fragment));
  return entry?.[1] ?? null;
}

function parseNumber(value) {
  if (!value) return null;
  const normalized = value
    .replace(/[٠-٩]/g, (digit) => '٠١٢٣٤٥٦٧٨٩'.indexOf(digit))
    .replace(/٫/g, '.')
    .replace(/,/g, '');
  const match = normalized.match(/\d+(?:\.\d+)?/);
  return match ? Number.parseFloat(match[0]) : null;
}

function extractDimensions(attributes, title) {
  let widthCm = parseNumber(findAttribute(attributes, 'عرض البلاط'));
  let heightCm = parseNumber(findAttribute(attributes, 'طول البلاط'));
  if (!widthCm || !heightCm) {
    const normalizedTitle = title.replace(/[٠-٩]/g, (digit) => '٠١٢٣٤٥٦٧٨٩'.indexOf(digit));
    const pair = normalizedTitle.match(/(\d+(?:[.,]\d+)?)\s*[×xX*]\s*(\d+(?:[.,]\d+)?)/);
    if (pair) {
      heightCm ||= Number.parseFloat(pair[1].replace(',', '.'));
      widthCm ||= Number.parseFloat(pair[2].replace(',', '.'));
    }
  }
  return { widthCm, heightCm };
}

function extractGalleryImages(html) {
  const gallery = html.match(/<div\b[^>]*id=["']gallery-images["'][^>]*>([\s\S]*?)<div\b[^>]*class=["'][^"']*group_btns/i)?.[1] ?? '';
  const images = [];
  for (const tag of gallery.matchAll(/<[^>]+\bdata-src\s*=\s*(["']).*?\1[^>]*>/gi)) {
    const source = htmlAttribute(tag[0], 'data-src');
    if (source?.startsWith('https://') && !source.startsWith('data:') && !images.includes(source)) images.push(source);
  }
  return images;
}

function extractOverview(html) {
  const match = html.match(/<div\b[^>]*class=["'][^"']*product attribute overview[^"']*["'][^>]*>[\s\S]*?<div\b[^>]*class=["']value["'][^>]*>([\s\S]*?)<\/div>/i);
  return match ? stripTags(match[1]) : null;
}

function inferTextureCategory(product) {
  const text = `${product.name} ${product.overview ?? ''}`;
  const porcelain = /بورسلين|بروسلين/i.test(text);
  const wall = /حوائط|حائط/i.test(text);
  if (porcelain && wall) return 'ElSalab / Porcelain / Wall';
  if (porcelain) return 'ElSalab / Porcelain / Floor';
  if (wall) return 'ElSalab / Ceramic / Wall';
  return 'ElSalab / Ceramic / Floor';
}

function safeStem(value) {
  const cleaned = value.normalize('NFKD').replace(/[^a-z0-9_-]+/gi, '-').replace(/^-+|-+$/g, '');
  return cleaned || createHash('sha256').update(value).digest('hex').slice(0, 16);
}

function extensionForUrl(url) {
  const extension = path.extname(new URL(url).pathname).toLowerCase();
  return ['.jpg', '.jpeg', '.png'].includes(extension) ? extension : '.jpg';
}

async function downloadImage(url, outputPath) {
  const response = await fetchResponse(url);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length < 1000) throw new Error(`Downloaded image is unexpectedly small (${bytes.length} bytes)`);
  await writeFile(outputPath, bytes);
  return bytes.length;
}

async function scrapeProduct(url, sourceCategory) {
  const html = await fetchText(url);
  const name = metaContent(html, 'name', 'title') ?? stripTags(html.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)?.[1] ?? 'Unnamed product');
  const sku = stripTags(html.match(/<div\b[^>]*class=["'][^"']*product attribute sku[^"']*["'][^>]*>[\s\S]*?<div\b[^>]*class=["']value["'][^>]*>([\s\S]*?)<\/div>/i)?.[1] ?? '') || null;
  const productId = html.match(/data-product-id=["'](\d+)["']/i)?.[1] ?? null;
  const attributes = extractAttributes(html);
  const images = extractGalleryImages(html);
  const overview = extractOverview(html);
  const brand = stripTags(html.match(/product-image-main-labels[\s\S]*?<span\b[^>]*>([\s\S]*?)<\/span>/i)?.[1] ?? '') || null;
  const currentPrice = parseNumber(metaContent(html, 'property', 'product:price:amount'));
  const listPrice = parseNumber(findAttribute(attributes, 'السعر'));
  const { widthCm, heightCm } = extractDimensions(attributes, name);

  return {
    sourceCategoryId: sourceCategory.id,
    sourceCategoryAr: sourceCategory.labelAr,
    sourceCategoryEn: sourceCategory.labelEn,
    simulationMode: sourceCategory.mode,
    productId,
    sku,
    name,
    brand,
    currentPrice,
    listPrice,
    currency: metaContent(html, 'property', 'product:price:currency') ?? 'EGP',
    inStock: /class=["'][^"']*stock available/i.test(html),
    overview,
    color: findAttribute(attributes, 'لون المنتج') ?? overview?.match(/اللون\s*:\s*([^\n]+)/)?.[1]?.trim() ?? null,
    countryOfOrigin: findAttribute(attributes, 'بلد المنشأ'),
    grade: findAttribute(attributes, 'فرز السيراميك'),
    tileWidthCm: widthCm,
    tileHeightCm: heightCm,
    tilesPerBox: parseNumber(findAttribute(attributes, 'عدد البلاطات')),
    squareMetersPerBox: parseNumber(findAttribute(attributes, 'السعة الكرتونية')),
    imageUrls: images,
    primaryImageUrl: images[0] ?? null,
    textureImageUrl: sourceCategory.mode === 'texture' ? images.at(-1) ?? images[0] ?? null : null,
    productUrl: url,
    attributes,
  };
}

function escapeProperty(value) {
  let result = '';
  const text = String(value ?? '');
  for (let index = 0; index < text.length; index += 1) {
    const code = text.charCodeAt(index);
    const char = text[index];
    if (char === '\\') result += '\\\\';
    else if (char === '\n') result += '\\n';
    else if ('=:#\!'.includes(char)) result += `\\${char}`;
    else if (code < 32 || code > 126) result += `\\u${code.toString(16).padStart(4, '0')}`;
    else result += char;
  }
  return result;
}

function buildTextureCatalog(products, scrapedAt) {
  const lines = [
    '# Generated by scraping/scrape-elsallab.mjs for an internal proof of concept.',
    '# Product names and images remain attributable to their source URLs.',
    '',
    'id=ElSalab#ScrapedTextures',
    'name=ElSalab sampled tiles and materials',
    `description=${escapeProperty(`Small evaluation sample refreshed ${scrapedAt.slice(0, 10)}`)}`,
    'version=0.2.0',
    'license=Internal proof of concept; verify product asset usage rights before redistribution',
    'provider=ElSalab PoC',
    '',
  ];
  products.forEach((product, index) => {
    const number = index + 1;
    const stableId = product.sku ?? product.productId ?? safeStem(product.productUrl);
    lines.push(
      `id#${number}=${escapeProperty(`ElSalab#${stableId}`)}`,
      `name#${number}=${escapeProperty(product.name)}`,
      `category#${number}=${escapeProperty(inferTextureCategory(product))}`,
      `image#${number}=${escapeProperty(`/images/${product.textureFileName}`)}`,
      `width#${number}=${product.tileWidthCm}`,
      `height#${number}=${product.tileHeightCm}`,
      `creator#${number}=Ahmed El Sallab`,
      '',
    );
  });
  return `${lines.join('\n')}\n`;
}

function csvCell(value) {
  if (value === null || value === undefined) return '';
  const text = typeof value === 'object' ? JSON.stringify(value) : String(value);
  return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function buildCsv(products) {
  const columns = [
    'sourceCategoryId', 'sku', 'productId', 'name', 'brand', 'currentPrice', 'listPrice', 'currency',
    'inStock', 'color', 'countryOfOrigin', 'grade', 'tileWidthCm', 'tileHeightCm', 'tilesPerBox',
    'squareMetersPerBox', 'localImageFile', 'textureFileName', 'productUrl', 'primaryImageUrl', 'textureImageUrl',
  ];
  const rows = [columns.join(',')];
  for (const product of products) rows.push(columns.map((column) => csvCell(product[column])).join(','));
  return `\uFEFF${rows.join('\r\n')}\r\n`;
}

async function main() {
  const plan = JSON.parse(await readFile(planPath, 'utf8'));
  await mkdir(dataImagesDir, { recursive: true });
  await mkdir(textureImagesDir, { recursive: true });

  const products = [];
  const errors = [];
  const categorySummary = [];

  for (const category of plan.categories) {
    process.stdout.write(`Listing ${category.labelEn} ... `);
    try {
      const listingHtml = await fetchText(category.url);
      const productLinks = extractProductLinks(listingHtml).slice(0, category.sampleLimit);
      console.log(`${productLinks.length} products selected`);
      let completed = 0;
      for (const productUrl of productLinks) {
        try {
          const product = await scrapeProduct(productUrl, category);
          const textureImageIndex = plan.textureImageIndexBySku?.[product.sku];
          if (category.mode === 'texture' && Number.isInteger(textureImageIndex)) {
            product.textureImageUrl = product.imageUrls[textureImageIndex] ?? product.textureImageUrl;
          }
          const stem = safeStem(product.sku ?? product.productId ?? productUrl);
          if (product.primaryImageUrl) {
            const fileName = `${stem}${extensionForUrl(product.primaryImageUrl)}`;
            await downloadImage(product.primaryImageUrl, path.join(dataImagesDir, fileName));
            product.localImageFile = `images/${fileName}`;
          }
          if (category.mode === 'texture' && product.textureImageUrl && product.tileWidthCm && product.tileHeightCm) {
            const textureFileName = `elsallab-${stem}${extensionForUrl(product.textureImageUrl)}`;
            const dataTexturePath = path.join(dataImagesDir, `texture-${textureFileName}`);
            await downloadImage(product.textureImageUrl, dataTexturePath);
            await copyFile(dataTexturePath, path.join(textureImagesDir, textureFileName));
            product.textureFileName = textureFileName;
          }
          products.push(product);
          completed += 1;
          console.log(`  [${completed}/${productLinks.length}] ${product.sku ?? product.productId ?? 'no-id'} ${product.name}`);
        } catch (error) {
          errors.push({ categoryId: category.id, productUrl, error: error.message });
          console.error(`  Failed ${productUrl}: ${error.message}`);
        }
        await sleep(plan.requestDelayMs);
      }
      categorySummary.push({ id: category.id, requested: category.sampleLimit, discovered: productLinks.length, completed });
    } catch (error) {
      errors.push({ categoryId: category.id, categoryUrl: category.url, error: error.message });
      categorySummary.push({ id: category.id, requested: category.sampleLimit, discovered: 0, completed: 0 });
      console.error(`Failed category ${category.id}: ${error.message}`);
    }
  }

  const scrapedAt = new Date().toISOString();
  const textures = products.filter((product) => product.textureFileName);
  if (textures.length === 0) throw new Error('No usable tile textures were produced; refusing to replace the texture catalog.');

  const dataset = {
    schemaVersion: 1,
    scrapedAt,
    sourceSite: plan.sourceSite,
    scope: 'Small internal proof-of-concept sample; not a full store mirror.',
    products,
  };
  const summary = {
    scrapedAt,
    productCount: products.length,
    textureCount: textures.length,
    errorCount: errors.length,
    categories: categorySummary,
    errors,
  };

  await writeFile(path.join(dataDir, 'products.json'), `${JSON.stringify(dataset, null, 2)}\n`, 'utf8');
  await writeFile(path.join(dataDir, 'products.csv'), buildCsv(products), 'utf8');
  await writeFile(path.join(dataDir, 'run-summary.json'), `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
  await writeFile(textureCatalogPath, buildTextureCatalog(textures, scrapedAt), 'ascii');

  console.log(`Done: ${products.length} products, ${textures.length} Sweet Home 3D textures, ${errors.length} errors.`);
  console.log(`Dataset: ${path.join(dataDir, 'products.json')}`);
  console.log(`Texture catalog: ${textureCatalogPath}`);
  if (errors.length > 0) process.exitCode = 2;
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
