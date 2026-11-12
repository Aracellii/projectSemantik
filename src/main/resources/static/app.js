const state={cars:[],results:[],selected:null,tab:'summary',request:0,controller:null};
const $=selector=>document.querySelector(selector);

// Fungsi 'formatName': Ubah Format 
// Contoh: 'BlindVan' jadi 'Blind Van', 'ChasisMonokok' jadi 'Chasis Monokok',
function formatName(text){
  // Kalau bukan teks, angka murni (tahun/penumpang), atau path foto (/Foto/...), lewati aja
  if(!text||typeof text!=='string')return text;
  if(text.startsWith('/')||text.startsWith('http://')||text.startsWith('https://'))return text;
  if(/^\d+$/.test(text.trim()))return text;

  // 1. Ganti underscore (_) jadi spasi biasa 
  let s=text.replace(/_/g,' ');

  // 2. Selipkan spasi tiap ada huruf kecil nempel huruf besar (contoh: BlindVan -> Blind Van)
  s=s.replace(/([a-z0-9])([A-Z])/g,'$1 $2');

  // 3. Jaga singkatan huruf besar biar tetap rapi (contoh: ABSTank -> ABS Tank)
  s=s.replace(/([A-Z]+)([A-Z][a-z])/g,'$1 $2');

  // 4. Bersihkan spasi dobel dan huruf paling depan tetap kapital
  s=s.replace(/\s+/g,' ').trim();
  if(s.length>0){s=s.charAt(0).toUpperCase()+s.slice(1);}
  return s;
}

async function api(path,options={}){
  const response=await fetch(path,options);
  if(!response.ok)throw new Error(`HTTP ${response.status}`);
  return response.json();
}

// Isi pilihan di dropdown filter
function fillSelect(id,values){
  const select=$(id);
  [...new Set(values.filter(Boolean))].sort().forEach(value=>{
    const option=document.createElement('option');
    option.value=value; // Nilai asli ontologi dikirim ke backend (misal: "BlindVan")
    option.textContent=formatName(value); // Tapi teks yang dibaca user ada spasinya ("Blind Van")
    select.append(option);
  });
}

// Filter untuk menyembunyikan kelas perantara/induk yang tidak perlu ditampilkan ke user
const cleanCategories = cats => (cats || []).filter(c => c !== 'MobilBerdasarkanJenis');

function renderList(cars){
  state.results=cars;
  $('#shown').textContent=cars.length;
  const box=$('#car-list'); box.replaceChildren();
  if(!cars.length){const empty=document.createElement('p');empty.className='muted';empty.textContent='Tidak ada mobil yang cocok.';box.append(empty);return;}
  cars.forEach(car=>{
    const item=document.createElement('article'); item.className='car'+(state.selected?.id===car.id?' selected':''); item.onclick=()=>selectCar(car);
    const name=document.createElement('div'); name.className='car-name'; name.textContent=formatName(car.name);
    const cats=cleanCategories(car.categories);
    const meta=document.createElement('div'); meta.className='car-meta'; meta.textContent=[formatName(car.brand),formatName(cats[0])].filter(Boolean).join(' · ');
    item.append(name,meta); box.append(item);
  });
}

function searchParams(){
  const params=new URLSearchParams();
  [['q','#search'],['category','#category'],['brand','#brand'],['fuel','#fuel']].forEach(([key,id])=>{if($(id).value)params.set(key,$(id).value);});
  return params;
}

async function refreshResults(){
  const request=++state.request;
  if(state.controller)state.controller.abort();
  state.controller=new AbortController();
  $('#car-list').replaceChildren(Object.assign(document.createElement('p'),{className:'muted',textContent:'Mencari di ontology...'}));
  try{
    const cars=await api(`/api/mobil/search?${searchParams()}`,{signal:state.controller.signal});
    if(request===state.request)renderList(cars);
  }catch(error){
    if(error.name!=='AbortError'&&request===state.request){const message=document.createElement('p');message.className='error';message.textContent='Pencarian ontology gagal.';$('#car-list').replaceChildren(message);}
  }
}

async function selectCar(car){
  state.selected=car; state.tab='summary'; renderList(state.results);
  const loading=document.createElement('p'); loading.className='muted'; loading.textContent='Memuat detail...'; $('#detail').replaceChildren(loading);
  try{renderDetail(await api(`/api/mobil/${encodeURIComponent(car.id)}`));}catch(error){const message=document.createElement('p');message.className='error';message.textContent='Detail tidak dapat dimuat.';$('#detail').replaceChildren(message);}
}

function renderDetail(car){
  const detail=$('#detail'); detail.replaceChildren();
  const hero=document.createElement('div'); hero.className='hero';
  const heroContent=document.createElement('div'); heroContent.className='hero-content';
  const pill=document.createElement('span'); pill.className='pill'; pill.textContent='RDF Individual';
  const title=document.createElement('h2'); title.textContent=formatName(car.name);
  const subtitle=document.createElement('div'); subtitle.textContent=[formatName(car.brand),cleanCategories(car.categories).map(formatName).join(', ')].filter(Boolean).join(' · ')||'Informasi ontology';
  heroContent.append(pill,title,subtitle); hero.append(heroContent);
  const photoPath=car.properties.foto?.[0]||car.properties.gambar?.[0];
  if(photoPath){
    const img=document.createElement('img');
    img.src=photoPath;
    img.alt=car.name;
    img.className='hero-photo';
    hero.append(img);
  }
  detail.append(hero);
  const tabs=document.createElement('nav'); tabs.className='tabs';
  [['summary','Ringkasan'],['properties','Spesifikasi'],['components','Komponen'],['relations','Relasi Ontologi'],['triples','Triple RDF']].forEach(([key,label])=>{const button=document.createElement('button');button.className='tab'+(state.tab===key?' active':'');button.textContent=label;button.onclick=()=>{state.tab=key;renderDetail(car);};tabs.append(button);});
  detail.append(tabs);
  if(state.tab==='summary')renderSummary(detail,car);
  if(state.tab==='properties')loadProperties(detail,car);
  if(state.tab==='components'||state.tab==='relations')loadRows(detail,car,'/relations');
  if(state.tab==='triples')loadRows(detail,car,'/triples');
}

// Tampilkan kartu ringkasan detail mobil (tab pertama)
function renderSummary(detail,car){
  const panel=document.createElement('div');panel.className='panel grid';
  // Helper kecil biar data array (bisa lebih dari satu nilai) otomatis dirapikan
  const formatList=arr=>arr?.map(formatName).join(', ');
  [
    ['Kategori',cleanCategories(car.categories).map(formatName).join(', ')],
    ['Merk',formatName(car.brand)],
    ['Bahan bakar',formatName(car.fuel)],
    ['Penumpang',car.properties.jumlahPenumpang?.join(', ')],
    ['Tahun produksi',car.properties.tahunProduksi?.join(', ')],
    ['Kapasitas mesin',car.properties.kapasitasMesin?.map(v=>v+' cc').join(', ')],
    ['Transmisi',formatList(car.properties.memilikiTransmisi)],
    ['Chassis',formatList(car.properties.memilikiChasis)],
    ['Sistem Kemudi',formatList(car.properties.memilikiFiturKemudi)],
    ['Rem Depan',formatList(car.properties.memilikiRemDepan)],
    ['Rem Belakang',formatList(car.properties.memilikiRemBelakang)],
    ['Fitur Keselamatan',formatList(car.properties.memilikiFiturKeselamatan)],
    ['Fitur Kenyamanan',formatList(car.properties.memilikiFiturKenyamanan)]
  ].filter(([,value])=>value).forEach(([label,value])=>{
    const fact=document.createElement('div');fact.className='fact';
    const small=document.createElement('small');small.textContent=label;
    const strong=document.createElement('strong');strong.textContent=value;
    fact.append(small,strong);panel.append(fact);
  });
  detail.append(panel);
}

async function loadProperties(detail,car){
  const panel=document.createElement('div');panel.className='panel';panel.textContent='Memuat spesifikasi...';detail.append(panel);
  try{
    const rows=await api(`/api/mobil/${encodeURIComponent(car.id)}/properties`);
    panel.replaceChildren(makeTable(['Properti','Nilai'],Object.entries(rows).flatMap(([key,values])=>values.map(value=>[formatName(key),formatName(value)]))));
  }catch(error){panel.textContent='Spesifikasi tidak tersedia.';}
}

async function loadRows(detail,car,path){
  const panel=document.createElement('div');panel.className='panel';panel.textContent='Memuat data...';detail.append(panel);
  try{
    const rows=await api(`/api/mobil/${encodeURIComponent(car.id)}${path}`);
    if(path==='/triples'){
      panel.replaceChildren(makeTable(['Subjek','Predikat','Objek'],rows.map(row=>[row.subject,row.predicate,row.object])));
    }else{
      panel.replaceChildren(makeTable(['Subjek','Predikat','Objek'],rows.map(row=>[
        formatName(row.subject.replace(/^.*[#/]/,'')),
        formatName(row.predicate.replace(/^.*[#/]/,'')),
        formatName(row.object.replace(/^.*[#/]/,''))
      ])));
    }
  }catch(error){panel.textContent='Data relasi tidak tersedia.';}
}

function makeTable(headers,rows){
  const wrap=document.createElement('div');wrap.className='table-wrap';
  const table=document.createElement('table'),head=document.createElement('thead'),headRow=document.createElement('tr');
  headers.forEach(header=>{const cell=document.createElement('th');cell.textContent=header;headRow.append(cell);});
  head.append(headRow);
  const body=document.createElement('tbody');
  rows.forEach(row=>{
    const rowElement=document.createElement('tr');
    row.forEach((value,index)=>{const cell=document.createElement('td');cell.textContent=value;cell.className=index===0?'uri':'';rowElement.append(cell);});
    body.append(rowElement);
  });
  table.append(head,body);wrap.append(table);return wrap;
}

async function init(){
  try{
    state.cars=await api('/api/mobil'); $('#total').textContent=state.cars.length;
    // Kategori difilter agar MobilBerdasarkanJenis tidak masuk ke dropdown
    fillSelect('#category',state.cars.flatMap(car=>cleanCategories(car.categories))); fillSelect('#brand',state.cars.map(car=>car.brand)); fillSelect('#fuel',state.cars.map(car=>car.fuel));
    renderList(state.cars);
  }catch(error){const message=document.createElement('p');message.className='error';message.textContent='Ontology gagal dimuat.';$('#car-list').replaceChildren(message);}
}

['#search','#category','#brand','#fuel'].forEach(selector=>$(selector).addEventListener('input',refreshResults));
$('#reset').onclick=()=>{['#search','#category','#brand','#fuel'].forEach(selector=>$(selector).value='');refreshResults();};
init();
