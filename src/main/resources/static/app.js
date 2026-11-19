/**
 * AutoSemantik Automotive Catalog
 * Semantic Web & Ontology Explorer
 * Zero breaking changes to existing API contracts
 */

const state = {
  cars: [],
  results: [],
  selected: null,
  tab: 'summary',
  request: 0,
  controller: null,
  debounceTimer: null
};

const $ = selector => document.querySelector(selector);
const $$ = selector => document.querySelectorAll(selector);

// Fallback Automotive SVG Generator
function getFallbackImage(name = 'Kendaraan') {
  const safeName = (name || 'Kendaraan').replace(/</g, '').replace(/>/g, '');
  return `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="240" viewBox="0 0 400 240" fill="none"><rect width="400" height="240" fill="%23f1f5f9"/><g fill="%2394a3b8"><path d="M80 150l25-50c6-12 18-20 32-22l110-14c16-2 32 6 40 20l35 66h30c10 0 18 8 18 18v22H60v-22c0-10 8-18 18-18h2z" opacity="0.3"/><circle cx="130" cy="190" r="26" fill="%2364748b"/><circle cx="130" cy="190" r="12" fill="%23f8fafc"/><circle cx="275" cy="190" r="26" fill="%2364748b"/><circle cx="275" cy="190" r="12" fill="%23f8fafc"/></g><text x="200" y="230" font-family="system-ui,sans-serif" font-size="12" font-weight="600" fill="%2364748b" text-anchor="middle">${encodeURIComponent(safeName)}</text></svg>`;
}

// Rupiah Currency Formatter
function formatRupiah(val) {
  if (!val) return '';
  const num = parseInt(val, 10);
  if (isNaN(num)) return val;
  return new Intl.NumberFormat('id-ID', {
    style: 'currency',
    currency: 'IDR',
    maximumFractionDigits: 0
  }).format(num);
}

// Format Name Helper (e.g. 'BlindVan' -> 'Blind Van')
function formatName(text) {
  if (!text || typeof text !== 'string') return text;
  if (text.startsWith('/') || text.startsWith('http://') || text.startsWith('https://')) return text;
  if (/^\d+$/.test(text.trim())) return text;

  let s = text.replace(/_/g, ' ');
  s = s.replace(/([a-z0-9])([A-Z])/g, '$1 $2');
  s = s.replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2');
  s = s.replace(/\s+/g, ' ').trim();
  if (s.length > 0) {
    s = s.charAt(0).toUpperCase() + s.slice(1);
  }
  return s;
}

// Remove internal filter class
const cleanCategories = cats => (cats || []).filter(c => c !== 'MobilBerdasarkanJenis' && c !== 'Mobil');

// Fuel CSS class variant
function fuelClass(fuel) {
  const f = (fuel || '').toLowerCase();
  if (f.includes('listrik') || f.includes('ev') || f.includes('electric')) return 'fuel-listrik';
  if (f.includes('hybrid') || f.includes('hibrida')) return 'fuel-hybrid';
  if (f.includes('solar') || f.includes('diesel')) return 'fuel-solar';
  return 'fuel-bensin';
}

// Fuel Icon Helper
function fuelIcon(fuel) {
  const f = (fuel || '').toLowerCase();
  if (f.includes('listrik') || f.includes('ev')) return '⚡';
  if (f.includes('hybrid')) return '🔋';
  if (f.includes('solar') || f.includes('diesel')) return '🚜';
  return '⛽';
}

// Standard API fetch
async function api(path, options = {}) {
  const response = await fetch(path, options);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

// Fill dropdowns
function fillSelect(id, values) {
  const select = $(id);
  if (!select) return;
  const current = select.value;
  // Keep first option ('Semua ...')
  select.length = 1;
  [...new Set(values.filter(Boolean))].sort().forEach(value => {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = formatName(value);
    select.append(option);
  });
  if (current) select.value = current;
}

// Build URLSearchParams
function searchParams() {
  const params = new URLSearchParams();
  [['q', '#search'], ['category', '#category'], ['brand', '#brand'], ['fuel', '#fuel']].forEach(([key, id]) => {
    const el = $(id);
    if (el && el.value.trim()) params.set(key, el.value.trim());
  });
  return params;
}

// Render animated skeleton cards
function renderSkeletons() {
  const box = $('#car-list');
  if (!box) return;
  box.replaceChildren();

  for (let i = 0; i < 6; i++) {
    const card = document.createElement('div');
    card.className = 'skeleton-card';
    card.innerHTML = `
      <div class="skeleton-img skeleton-shimmer"></div>
      <div class="skeleton-body">
        <div class="skeleton-line short skeleton-shimmer"></div>
        <div class="skeleton-line title skeleton-shimmer"></div>
        <div class="skeleton-line chips skeleton-shimmer"></div>
      </div>
    `;
    box.append(card);
  }
}

// Render Car Cards in Catalog Grid
function renderList(cars) {
  state.results = cars;
  const countEl = $('#shown');
  if (countEl) countEl.textContent = cars.length;
  const countInner = $('#shown-inner');
  if (countInner) countInner.textContent = cars.length;

  const box = $('#car-list');
  if (!box) return;
  box.replaceChildren();

  if (!cars.length) {
    const empty = document.createElement('div');
    empty.className = 'empty-state';
    empty.style.gridColumn = '1 / -1';
    empty.innerHTML = `
      <div class="empty-icon-wrap" style="background:#fee2e2; color:#ef4444;">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:30px;height:30px;">
          <circle cx="11" cy="11" r="8"></circle>
          <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
        </svg>
      </div>
      <h3>Tidak ada kendaraan yang cocok</h3>
      <p>Coba gunakan kata kunci lain seperti "pickup", "truk box", "EV", "matic", atau klik tombol "Reset Filter".</p>
    `;
    box.append(empty);
    return;
  }

  cars.forEach(car => {
    const card = document.createElement('article');
    const isSelected = state.selected?.id === car.id;
    card.className = 'car-card' + (isSelected ? ' selected' : '');
    card.onclick = () => selectCar(car);

    // Image container with fallback handling
    const imgWrap = document.createElement('div');
    imgWrap.className = 'car-card-img-wrap';

    const img = document.createElement('img');
    img.className = 'car-card-img';
    const photoPath = car.properties?.foto?.[0] || car.properties?.gambar?.[0];
    img.src = photoPath || getFallbackImage(car.name);
    img.alt = car.name || 'Foto Mobil';
    img.loading = 'lazy';
    img.onerror = function() {
      this.src = getFallbackImage(car.name);
      this.onerror = null;
    };
    imgWrap.append(img);

    // Badges on image
    const cats = cleanCategories(car.categories);
    if (cats[0]) {
      const catBadge = document.createElement('span');
      catBadge.className = 'card-floating-badge';
      catBadge.textContent = formatName(cats[0]);
      imgWrap.append(catBadge);
    }

    if (car.fuel) {
      const fuelBadge = document.createElement('span');
      fuelBadge.className = `card-fuel-badge ${fuelClass(car.fuel)}`;
      fuelBadge.textContent = `${fuelIcon(car.fuel)} ${formatName(car.fuel)}`;
      imgWrap.append(fuelBadge);
    }

    // Card Body
    const body = document.createElement('div');
    body.className = 'car-card-body';

    // Brand row
    const brandRow = document.createElement('div');
    brandRow.className = 'card-brand-row';

    const brandPill = document.createElement('span');
    brandPill.className = 'brand-pill';
    brandPill.textContent = formatName(car.brand) || 'Katalog';
    brandRow.append(brandPill);

    body.append(brandRow);

    // Car Title
    const title = document.createElement('h3');
    title.className = 'car-card-title';
    title.textContent = formatName(car.name);
    body.append(title);

    // Quick specs row
    const specsRow = document.createElement('div');
    specsRow.className = 'car-card-specs';

    const transmisi = car.properties?.memilikiTransmisi?.[0];
    if (transmisi) {
      const transBadge = document.createElement('span');
      transBadge.className = 'spec-badge';
      transBadge.textContent = `⚙️ ${formatName(transmisi)}`;
      specsRow.append(transBadge);
    }

    const penumpang = car.properties?.jumlahPenumpang?.[0];
    if (penumpang) {
      const seatBadge = document.createElement('span');
      seatBadge.className = 'spec-badge';
      seatBadge.textContent = `👥 ${penumpang} Kursi`;
      specsRow.append(seatBadge);
    }

    const mesin = car.properties?.kapasitasMesin?.[0];
    if (mesin) {
      const engineBadge = document.createElement('span');
      engineBadge.className = 'spec-badge';
      engineBadge.textContent = `⚡ ${mesin} cc`;
      specsRow.append(engineBadge);
    }

    body.append(specsRow);

    // Price tag if available
    const harga = car.properties?.harga?.[0];
    if (harga) {
      const priceTag = document.createElement('div');
      priceTag.className = 'price-tag';
      priceTag.textContent = formatRupiah(harga);
      body.append(priceTag);
    }

    card.append(imgWrap, body);
    box.append(card);
  });
}

const searchSynonyms = [
  {
    terms: ['listrik', 'mobil listrik', 'electric', 'electric car', 'electric vehicle', 'ev', 'bev', 'baterai', 'battery electric'],
    matches: car => normalizeSearchText(car.fuel) === 'listrik'
  },
  {
    terms: ['hybrid', 'hibrida', 'mobil hybrid', 'hev', 'plug in hybrid', 'phev'],
    matches: car => normalizeSearchText(car.fuel) === 'hybrid'
  },
  {
    terms: ['keluarga', 'mobil keluarga', 'family car', 'mpv', 'minivan'],
    matches: (car, haystack) => Number(car.properties?.jumlahPenumpang?.[0]) >= 5
      || /\b(mpv|minivan|mobil penumpang)\b/.test(haystack)
  },
  {
    terms: ['matic', 'matik', 'otomatis', 'automatic', 'automatic transmission', 'transmisi otomatis', 'at'],
    matches: (car, haystack) => ['automatic', 'cvt', 'matic', 'otomatis', 'at']
      .some(term => hasSearchTerm(haystack, term))
  },
  { terms: ['manual', 'transmisi manual', 'mt'], matches: (car, haystack) => hasSearchTerm(haystack, 'manual') },
  { terms: ['bensin', 'petrol', 'gasoline', 'pertamax', 'pertalite'], matches: car => normalizeSearchText(car.fuel) === 'bensin' },
  { terms: ['solar', 'diesel', 'mesin diesel', 'minyak solar'], matches: car => normalizeSearchText(car.fuel) === 'solar' },
  {
    terms: ['pickup', 'pick up', 'pick-up', 'pikap'],
    matches: car => matchesCategory(car, 'pick up', 'pickup')
  },
  {
    terms: ['truk box', 'truk boks', 'box truck', 'truck box'],
    matches: car => matchesCategory(car, 'box truck', 'boxtruck')
  },
  { terms: ['dump truck', 'dump', 'truk jungkit', 'truk tambang'], matches: car => matchesCategory(car, 'dump truck', 'dumptruck') },
  { terms: ['tronton', 'truk tronton', 'heavy truck'], matches: car => matchesCategory(car, 'tronton') },
  {
    terms: ['truck', 'truk', 'kendaraan niaga', 'mobil barang', 'mobil angkut', 'angkutan barang'],
    matches: car => matchesCategory(car, 'box truck', 'boxtruck', 'dump truck', 'dumptruck', 'tank truck', 'tanktruck', 'trailer truck', 'trailertruck', 'tronton', 'truck')
  },
  { terms: ['suv', 'sport utility', 'jeep', 'mobil suv'] },
  { terms: ['sedan', 'mobil sedan'] }
];

const ignoredSearchWords = new Set([
  'mobil', 'kendaraan', 'yang', 'untuk', 'dengan', 'ada', 'berapa', 'carikan',
  'cari', 'tolong', 'dong', 'harga', 'harganya', 'punya', 'pakai', 'menggunakan',
  'di', 'buat', 'dan', 'atau', 'saya', 'ingin', 'mau', 'show', 'find', 'car'
]);

function normalizeSearchText(value) {
  return String(value || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+/g, ' ');
}

function hasSearchTerm(text, term) {
  const normalizedText = ` ${normalizeSearchText(text)} `;
  const normalizedTerm = ` ${normalizeSearchText(term)} `;
  return normalizedText.includes(normalizedTerm);
}

function matchesCategory(car, ...categories) {
  const actual = (car.categories || []).map(normalizeSearchText);
  return categories.some(category => actual.includes(normalizeSearchText(category)));
}

function carSearchText(car) {
  const properties = Object.entries(car.properties || {})
    .filter(([key]) => !['sameas', 'differentfrom', 'topdataproperty'].includes(normalizeSearchText(key)))
    .flatMap(([key, values]) => [key, ...values]);
  return ` ${normalizeSearchText([
    car.name, car.brand, car.fuel, ...(car.categories || []),
    ...properties
  ].flat().join(' '))} `;
}

function parsePriceAmount(numberText, unitText = '') {
  const raw = numberText.trim();
  const groupedThousands = /[.,]\d{3}(?:[.,]\d{3})*$/.test(raw);
  const number = Number(groupedThousands ? raw.replace(/[.,]/g, '') : raw.replace(',', '.'));
  const unit = normalizeSearchText(unitText);
  const multiplier = /^(miliar|milyar|m)$/.test(unit) ? 1_000_000_000
    : /^(juta|jt)$/.test(unit) ? 1_000_000
      : /^(ribu|rb|k)$/.test(unit) ? 1_000 : 1;
  return Number.isFinite(number) ? number * multiplier : null;
}

function extractPriceFilter(query) {
  const amountPattern = '([0-9][0-9.,]*)(?:\\s*(miliar|milyar|juta|jt|ribu|rb|k)\\b)?';
  const range = new RegExp(`antara\\s+${amountPattern}\\s+(?:sampai|hingga|dan|-)\\s+${amountPattern}`);
  let match = query.match(range);
  if (match) {
    let min = parsePriceAmount(match[1], match[2]);
    let max = parsePriceAmount(match[3], match[4]);
    if (!match[2] && match[4]) min *= /^(miliar|milyar|m)$/.test(match[4]) ? 1_000_000_000
      : /^(juta|jt)$/.test(match[4]) ? 1_000_000 : 1_000;
    return { min, max, text: match[0] };
  }

  const amount = new RegExp(amountPattern);
  match = amount.exec(query);
  if (!match) return null;
  const value = parsePriceAmount(match[1], match[2]);
  if (value === null) return null;
  const before = query.slice(Math.max(0, match.index - 35), match.index + match[0].length);
  const after = query.slice(match.index + match[0].length, match.index + match[0].length + 25);
  if (!match[2] && !/(harga|rupiah|rp|budget|anggaran|bujet|di bawah|dibawah|kurang dari|maksimal|max|paling banyak|di atas|diatas|lebih dari|minimal|min|paling sedikit|mulai dari|under|below|above|over)/.test(before)) return null;
  if (/(kursi|penumpang|orang|seater)\s*$/.test(before)) return null;
  if (/(di bawah|dibawah|kurang dari|maksimal|max|paling banyak|hingga|sampai|under|below|budget|anggaran|bujet)/.test(before)) {
    return { max: value, text: match[0] };
  }
  if (/(di atas|diatas|lebih dari|minimal|min|paling sedikit|mulai dari|above|over)/.test(before)) {
    return { min: value, text: match[0] };
  }
  if (/^(\s*(ke atas|atau lebih|plus))/.test(after)) return { min: value, text: match[0] };
  return { max: value, text: match[0] };
}

function matchesSearchQuery(car, query) {
  let remaining = normalizeSearchText(query);
  const haystack = carSearchText(car);
  const priceFilter = extractPriceFilter(remaining);
  if (priceFilter) {
    const price = Number(car.properties?.harga?.[0]);
    if (!Number.isFinite(price)
      || (priceFilter.min !== undefined && price < priceFilter.min)
      || (priceFilter.max !== undefined && price > priceFilter.max)) return false;
    remaining = remaining.replace(normalizeSearchText(priceFilter.text), ' ');
    remaining = remaining.replace(/\b(antara|sampai|hingga|dan|di bawah|dibawah|kurang dari|maksimal|max|paling banyak|di atas|diatas|lebih dari|minimal|min|paling sedikit|mulai dari|ke atas|atau lebih|under|below|above|over|budget|anggaran|bujet|juta|jt|miliar|milyar|ribu|rb|rupiah|rp)\b/g, ' ');
  }

  const passengerMatch = remaining.match(/\b(\d+)\s*(kursi|penumpang|orang|seater)\b/);
  if (passengerMatch) {
    const seats = Number(car.properties?.jumlahPenumpang?.[0]);
    if (!Number.isFinite(seats) || seats < Number(passengerMatch[1])) return false;
    remaining = remaining.replace(passengerMatch[0], ' ');
  }

  const wantsCheapest = /\b(murah|termurah|paling murah|harga rendah|terjangkau|budget)\b/.test(remaining);
  const wantsMostExpensive = /\b(termahal|paling mahal)\b/.test(remaining);
  remaining = remaining.replace(/\b(murah|termurah|paling murah|harga rendah|mahal|termahal|paling mahal)\b/g, ' ');
  const groups = [...searchSynonyms].sort((a, b) => Math.max(...b.terms.map(x => x.length)) - Math.max(...a.terms.map(x => x.length)));
  for (const group of groups) {
    const matchedTerms = group.terms.filter(term => new RegExp(`\\b${normalizeSearchText(term).replace(/ /g, '\\s+')}\\b`).test(remaining));
    if (!matchedTerms.length) continue;
    if (group.matches ? !group.matches(car, haystack, matchedTerms)
      : !matchedTerms.some(term => hasSearchTerm(haystack, term))) return false;
    for (const term of matchedTerms) {
      remaining = remaining.replace(new RegExp(`\\b${normalizeSearchText(term).replace(/ /g, '\\s+')}\\b`, 'g'), ' ');
    }
  }

  const words = normalizeSearchText(remaining).split(' ').filter(word => word && !ignoredSearchWords.has(word));
  if (words.some(word => !hasSearchTerm(haystack, word))) return false;
  if (wantsCheapest && !(Number(car.properties?.harga?.[0]) > 0)) return false;
  return !wantsMostExpensive || Number(car.properties?.harga?.[0]) > 0;
}

function refreshResults() {
  const query = $('#search').value.trim();
  const category = normalizeSearchText($('#category').value);
  const brand = normalizeSearchText($('#brand').value);
  const fuel = normalizeSearchText($('#fuel').value);
  const cars = state.cars.filter(car => {
    const categories = (car.categories || []).map(normalizeSearchText);
    return (!category || categories.includes(category))
      && (!brand || normalizeSearchText(car.brand) === brand)
      && (!fuel || normalizeSearchText(car.fuel) === fuel)
      && (!query || matchesSearchQuery(car, query));
  });

  if (/\b(murah|termurah|paling murah|terjangkau)\b/i.test(query)) {
    cars.sort((a, b) => Number(a.properties?.harga?.[0] || Infinity) - Number(b.properties?.harga?.[0] || Infinity));
  } else if (/\b(termahal|paling mahal)\b/i.test(query)) {
    cars.sort((a, b) => Number(b.properties?.harga?.[0] || 0) - Number(a.properties?.harga?.[0] || 0));
  }
  renderList(cars);
}

// Select car and load details
async function selectCar(car) {
  state.selected = car;
  state.tab = 'summary';

  // Highlight selected card
  $$('.car-card').forEach(el => el.classList.remove('selected'));
  renderList(state.results);

  // Detail skeleton loader
  const detail = $('#detail');
  if (detail) {
    detail.innerHTML = `
      <div class="skeleton-card" style="border:none; box-shadow:none;">
        <div class="skeleton-img skeleton-shimmer" style="height: 200px;"></div>
        <div class="skeleton-body" style="padding: 24px;">
          <div class="skeleton-line short skeleton-shimmer"></div>
          <div class="skeleton-line title skeleton-shimmer"></div>
          <div class="skeleton-line medium skeleton-shimmer"></div>
          <div style="display:grid; grid-template-columns: 1fr 1fr; gap:12px; margin-top:20px;">
            <div class="skeleton-line skeleton-shimmer" style="height:45px;"></div>
            <div class="skeleton-line skeleton-shimmer" style="height:45px;"></div>
          </div>
        </div>
      </div>
    `;
  }

  try {
    const fullCar = await api(`/api/mobil/${encodeURIComponent(car.id)}`);
    renderDetail(fullCar);
    // Smooth scroll to detail on smaller screens
    if (window.innerWidth < 1180 && detail) {
      detail.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  } catch (error) {
    if (detail) {
      detail.innerHTML = `
        <div class="empty-state">
          <div class="empty-icon-wrap" style="background:#fee2e2; color:#ef4444;">✕</div>
          <h3>Detail Tidak Dapat Dimuat</h3>
          <p>${error.message}</p>
        </div>
      `;
    }
  }
}

// Render complete Detail Panel
function renderDetail(car) {
  const detail = $('#detail');
  if (!detail) return;
  detail.replaceChildren();

  // Hero Section
  const hero = document.createElement('div');
  hero.className = 'detail-hero';

  const metaRow = document.createElement('div');
  metaRow.className = 'hero-meta-row';

  const rdfPill = document.createElement('span');
  rdfPill.className = 'rdf-pill';
  rdfPill.textContent = 'OWL Individual';

  const fuelBadge = document.createElement('span');
  fuelBadge.className = `card-fuel-badge ${fuelClass(car.fuel)}`;
  fuelBadge.textContent = `${fuelIcon(car.fuel)} ${formatName(car.fuel) || 'N/A'}`;

  metaRow.append(rdfPill, fuelBadge);
  hero.append(metaRow);

  // High-res photo
  const photoPath = car.properties?.foto?.[0] || car.properties?.gambar?.[0];
  const imgWrap = document.createElement('div');
  imgWrap.className = 'hero-img-wrap';
  const img = document.createElement('img');
  img.src = photoPath || getFallbackImage(car.name);
  img.alt = car.name || 'Foto Detail';
  img.className = 'hero-img';
  img.onerror = function() {
    this.src = getFallbackImage(car.name);
    this.onerror = null;
  };
  imgWrap.append(img);
  hero.append(imgWrap);

  // Title & Subtitle
  const title = document.createElement('h2');
  title.textContent = formatName(car.name);

  const cats = cleanCategories(car.categories);
  const subtitle = document.createElement('div');
  subtitle.className = 'detail-hero-subtitle';
  subtitle.textContent = [formatName(car.brand), cats.map(formatName).join(', ')].filter(Boolean).join(' · ') || 'Informasi Ontologi';

  hero.append(title, subtitle);
  detail.append(hero);

  // Tabs Navigation
  const tabs = document.createElement('nav');
  tabs.className = 'tabs-nav';
  [
    ['summary', 'Ringkasan'],
    ['properties', 'Spesifikasi'],
    ['components', 'Komponen'],
    ['relations', 'Relasi Ontologi'],
    ['triples', 'Triple RDF']
  ].forEach(([key, label]) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'tab-btn' + (state.tab === key ? ' active' : '');
    btn.textContent = label;
    btn.onclick = () => {
      state.tab = key;
      renderDetail(car);
    };
    tabs.append(btn);
  });
  detail.append(tabs);

  // Tab Contents
  if (state.tab === 'summary') renderSummary(detail, car);
  if (state.tab === 'properties') loadProperties(detail, car);
  if (state.tab === 'components' || state.tab === 'relations') loadRows(detail, car, '/relations');
  if (state.tab === 'triples') loadRows(detail, car, '/triples');
}

// Summary Tab View
function renderSummary(detail, car) {
  const panel = document.createElement('div');
  panel.className = 'detail-panel';

  const grid = document.createElement('div');
  grid.className = 'summary-grid';

  const formatList = arr => arr?.map(formatName).join(', ');

  const facts = [
    ['Harga', car.properties?.harga?.[0] ? formatRupiah(car.properties.harga[0]) : null, true],
    ['Merk', formatName(car.brand)],
    ['Kategori', cleanCategories(car.categories).map(formatName).join(', ')],
    ['Bahan Bakar', formatName(car.fuel)],
    ['Kapasitas Mesin', car.properties?.kapasitasMesin?.map(v => v + ' cc').join(', ')],
    ['Tenaga Mesin', car.properties?.HP?.map(v => v + ' HP').join(', ')],
    ['Kapasitas Penumpang', car.properties?.jumlahPenumpang?.map(v => v + ' Orang').join(', ')],
    ['Tahun Produksi', car.properties?.tahunProduksi?.join(', ')],
    ['Kapasitas Muatan', car.properties?.kapasitasAngkutan?.map(v => v + ' kg').join(', ')],
    ['Transmisi', formatList(car.properties?.memilikiTransmisi)],
    ['Sasis / Rangka', formatList(car.properties?.memilikiChasis)],
    ['Sistem Kemudi', formatList(car.properties?.memilikiFiturKemudi)],
    ['Rem Depan', formatList(car.properties?.memilikiRemDepan)],
    ['Rem Belakang', formatList(car.properties?.memilikiRemBelakang)],
    ['Fitur Keselamatan', formatList(car.properties?.memilikiFiturKeselamatan)],
    ['Fitur Kenyamanan', formatList(car.properties?.memilikiFiturKenyamanan)]
  ];

  facts.filter(([, value]) => value).forEach(([label, value, isPrice]) => {
    const box = document.createElement('div');
    box.className = 'fact-box';

    const lbl = document.createElement('span');
    lbl.className = 'fact-label';
    lbl.textContent = label;

    const val = document.createElement('span');
    val.className = 'fact-value' + (isPrice ? ' fact-price' : '');
    val.textContent = value;

    box.append(lbl, val);
    grid.append(box);
  });

  panel.append(grid);

  // SKOS Synonyms / altLabels section
  const alts = car.properties?.altLabel;
  if (alts && alts.length) {
    const synBox = document.createElement('div');
    synBox.className = 'synonyms-box';

    const synTitle = document.createElement('div');
    synTitle.className = 'synonyms-title';
    synTitle.innerHTML = `
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="width:14px;height:14px;">
        <path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"></path>
        <line x1="7" y1="7" x2="7.01" y2="7"></line>
      </svg>
      SKOS Synonyms &amp; AltLabels:
    `;

    const synList = document.createElement('div');
    synList.className = 'synonyms-list';

    // Filter duplicates case-insensitively
    const seen = new Set();
    alts.forEach(syn => {
      const lower = syn.toLowerCase();
      if (!seen.has(lower)) {
        seen.add(lower);
        const chip = document.createElement('span');
        chip.className = 'synonym-chip';
        chip.textContent = syn;
        synList.append(chip);
      }
    });

    synBox.append(synTitle, synList);
    panel.append(synBox);
  }

  detail.append(panel);
}

// Properties Tab
async function loadProperties(detail, car) {
  const panel = document.createElement('div');
  panel.className = 'detail-panel';
  panel.innerHTML = '<p class="stat-label">Memuat spesifikasi OWL...</p>';
  detail.append(panel);

  try {
    const rows = await api(`/api/mobil/${encodeURIComponent(car.id)}/properties`);
    const flatRows = Object.entries(rows).flatMap(([key, values]) =>
      values.map(val => [formatName(key), formatName(val)])
    );
    panel.replaceChildren(makeTable(['Properti Ontologi', 'Nilai'], flatRows));
  } catch (error) {
    panel.innerHTML = '<p class="stat-label">Spesifikasi tidak tersedia.</p>';
  }
}

// Relations & Triples Tabs
async function loadRows(detail, car, path) {
  const panel = document.createElement('div');
  panel.className = 'detail-panel';
  panel.innerHTML = '<p class="stat-label">Memuat graph ontologi...</p>';
  detail.append(panel);

  try {
    const rows = await api(`/api/mobil/${encodeURIComponent(car.id)}${path}`);
    if (path === '/triples') {
      panel.replaceChildren(
        makeTable(['Subjek (Subject)', 'Predikat (Predicate)', 'Objek (Object)'],
          rows.map(r => [r.subject, r.predicate, r.object]),
          true
        )
      );
    } else {
      panel.replaceChildren(
        makeTable(['Subjek', 'Hubungan Predikat', 'Entitas Objek'],
          rows.map(r => [
            formatName(r.subject.replace(/^.*[#/]/, '')),
            formatName(r.predicate.replace(/^.*[#/]/, '')),
            formatName(r.object.replace(/^.*[#/]/, ''))
          ])
        )
      );
    }
  } catch (error) {
    panel.innerHTML = '<p class="stat-label">Data relasi tidak tersedia.</p>';
  }
}

// Reusable Semantic Table Generator
function makeTable(headers, rows, isUriTable = false) {
  const wrap = document.createElement('div');
  wrap.className = 'table-wrap';

  const table = document.createElement('table');
  const thead = document.createElement('thead');
  const headRow = document.createElement('tr');

  headers.forEach(h => {
    const th = document.createElement('th');
    th.textContent = h;
    headRow.append(th);
  });
  thead.append(headRow);
  table.append(thead);

  const tbody = document.createElement('tbody');
  if (!rows.length) {
    const emptyRow = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = headers.length;
    td.textContent = 'Tidak ada triple atau relasi terkait.';
    td.style.color = 'var(--text-muted)';
    emptyRow.append(td);
    tbody.append(emptyRow);
  } else {
    rows.forEach(row => {
      const tr = document.createElement('tr');
      row.forEach((val, idx) => {
        const td = document.createElement('td');
        td.textContent = val;
        if (isUriTable) {
          td.className = 'uri-cell';
        } else if (idx === 0) {
          td.className = 'pred-cell';
        }
        tr.append(td);
      });
      tbody.append(tr);
    });
  }

  table.append(tbody);
  wrap.append(table);
  return wrap;
}

// Debounced Search Handler
function handleSearchInput() {
  const searchVal = $('#search').value.trim();
  const clearBtn = $('#clear-search');
  if (clearBtn) {
    clearBtn.style.display = searchVal ? 'block' : 'none';
  }

  clearTimeout(state.debounceTimer);
  state.debounceTimer = setTimeout(() => {
    refreshResults();
  }, 180);
}

// Initialize Application
async function init() {
  renderSkeletons();

  try {
    state.cars = await api('/api/mobil');

    // Populate dropdown filters
    fillSelect('#category', state.cars.flatMap(car => cleanCategories(car.categories)));
    fillSelect('#brand', state.cars.map(car => car.brand));
    fillSelect('#fuel', state.cars.map(car => car.fuel));

    // Update total counter
    const statTotal = $('#stat-total');
    if (statTotal) statTotal.textContent = state.cars.length;

    renderList(state.cars);
  } catch (error) {
    console.error('Gagal memuat daftar mobil:', error);
    const box = $('#car-list');
    if (box) {
      box.innerHTML = `
        <div class="empty-state" style="grid-column: 1 / -1;">
          <div class="empty-icon-wrap" style="background:#fee2e2; color:#ef4444;">✕</div>
          <h3>Daftar Mobil Gagal Dimuat</h3>
          <p>Pastikan backend server berjalan di port lokal dan ontology valid. (${error.message})</p>
        </div>
      `;
    }
  }

  // Bind input listeners
  $('#search').addEventListener('input', handleSearchInput);
  ['#category', '#brand', '#fuel'].forEach(sel => {
    const el = $(sel);
    if (el) el.addEventListener('change', refreshResults);
  });

  // Clear search button
  const clearBtn = $('#clear-search');
  if (clearBtn) {
    clearBtn.onclick = () => {
      $('#search').value = '';
      clearBtn.style.display = 'none';
      $$('.tag-chip').forEach(c => c.classList.remove('active'));
      refreshResults();
    };
  }

  // Reset button
  $('#reset').onclick = () => {
    ['#search', '#category', '#brand', '#fuel'].forEach(sel => {
      const el = $(sel);
      if (el) el.value = '';
    });
    if (clearBtn) clearBtn.style.display = 'none';
    $$('.tag-chip').forEach(c => c.classList.remove('active'));
    refreshResults();
  };

  // Quick Synonym Chips
  $$('.tag-chip').forEach(chip => {
    chip.onclick = () => {
      const query = chip.dataset.query;
      const searchInput = $('#search');
      if (chip.classList.contains('active')) {
        chip.classList.remove('active');
        searchInput.value = '';
      } else {
        $$('.tag-chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        searchInput.value = query;
      }
      handleSearchInput();
    };
  });
}

// Start application
init();
