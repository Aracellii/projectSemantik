Spesifikasi Pencarian Ontologi Mobil Berbahasa Indonesia
1. Tujuan dan ruang lingkup
Dokumen ini menggantikan usulan penambahan ratusan owl:sameAs dan owl:equivalentProperty pada dokumen sebelumnya. Sasaran utama: pengguna dapat mencari mobil berdasarkan istilah Indonesia, singkatan, atau istilah otomotif Inggris yang umum, tanpa mengubah identitas individual dan properti dalam ontologi.

Sumber struktur: daftar individual dan properti dalam saran-owl-equivalen.md. File mobil.owl asli belum diperiksa, sehingga nama dan tipe entitas di bawah perlu dicocokkan dengan file OWL aktual sebelum diterapkan.
- Bahasa utama label: Indonesia (@id).
- Bahasa Inggris: hanya istilah yang lazim atau nama teknis (@en jika perlu).
- Nama URI asli dipertahankan agar relasi dan aplikasi yang sudah ada tidak rusak.
- Pencarian: rdfs:label + skos:altLabel + SPARQL di Apache Jena.
- owl:equivalentClass hanya untuk klasifikasi berbasis syarat yang benar, bukan sinonim pencarian.
2. Pemilihan kosakata
Kebutuhan	Kosakata	Catatan
Nama utama yang ditampilkan	rdfs:label	Satu label utama Indonesia per entitas
Sinonim dan singkatan pencarian	skos:altLabel	Bisa lebih dari satu
Deskripsi	rdfs:comment	Opsional
Mengelompokkan mobil dari kriteria	owl:equivalentClass	Butuh reasoner
Menyatukan individual yang identik	owl:sameAs	Tidak dipakai untuk kata sinonim
Menyamakan relasi yang betul-betul identik	owl:equivalentProperty	Tidak diperlukan hanya untuk search


Penting: skos:altLabel adalah anotasi. Jena tidak otomatis mengubah label menjadi filter relasi. Backend harus mencari entitas dari label, lalu menelusuri properti dari entitas tersebut ke mobil.
3. Kamus label untuk individual
Tabel ini adalah spesifikasi anotasi yang disarankan. Nama pada kolom pertama adalah identifier yang dipertahankan.
3.1 Bahan bakar
Individual	rdfs:label (@id)	skos:altLabel
Bensin	Bensin	bensin mobil; gasoline; petrol
Solar	Solar	diesel; bahan bakar diesel
Listrik	Listrik	tenaga listrik; elektrik
Hybrid	Hibrida	hybrid; bensin listrik (hanya sebagai istilah pencarian umum, cek jenis hibrida aktual)


Jangan disamakan: Pertamax dengan Bensin; EV dengan Listrik sebagai bahan bakar; ZeroEmission dengan Listrik; BBM dengan Bensin saja.
3.2 Transmisi
Individual	Label utama	Sinonim
Automatic	Transmisi Otomatis	matic; matik; otomatis; AT; automatic
Manual	Transmisi Manual	manual; MT
CVT	Transmisi CVT	CVT; continuously variable transmission


Catatan: CVT adalah tipe transmisi otomatis. Jangan menyamakan CVT dan Automatic memakai owl:sameAs. Untuk pencarian luas “otomatis” yang mencakup CVT, tambahkan aturan klasifikasi atau perluas filter pada backend sesuai pemodelan aktual.
3.3 Mesin
Individual	Label utama	Sinonim
GasolineEngine	Mesin Bensin	mesin gasoline; petrol engine
DieselEngine	Mesin Diesel	mesin solar; diesel engine
ElectricMotor	Motor Listrik	penggerak listrik; electric motor
HybridEngine	Sistem Penggerak Hibrida	mesin hybrid; hybrid powertrain


3.4 Rem dan keselamatan pengereman
Individual	Label utama	Sinonim
DiskBrake	Rem Cakram	disc brake; disk brake; cakram
DrumBrake	Rem Tromol	drum brake; tromol
FullAirBrake	Rem Udara Penuh	full air brake; rem pneumatik
HydraulicVacuumServoBrake	Rem Hidrolik Berbantuan Vakum	hydraulic vacuum servo brake; rem servo vakum
ABS	Sistem Rem ABS	ABS; anti-lock braking system
ABS_EBD	Sistem Rem ABS dan EBD	ABS EBD; rem ABS dengan EBD


Periksa tipe: ABS dan ABS_EBD adalah fitur/sistem pengereman, bukan jenis rem cakram/tromol. Jangan beri alias AntiLockBrakingSystem untuk ABS_EBD saja.
3.5 Suspensi
Individual	Label utama	Sinonim
IndependentSuspension	Suspensi Independen	independent suspension; suspensi bebas
DependentSuspension	Suspensi Dependen	dependent suspension; suspensi poros kaku
SemiIndependentSuspension	Suspensi Semiindependen	semi-independent suspension; suspensi semiindependen


MacPherson, double wishbone, leaf spring, dan torsion beam adalah desain/komponen spesifik, bukan sinonim universal kategori di atas.
3.6 Sasis
Individual	Label utama	Sinonim
ChasisMonokok	Sasis Monokok	monokok; monocoque; unibody
ChasisLadderFrame	Sasis Tangga	ladder frame; rangka tangga; body-on-frame (istilah terkait, tidak selalu identik secara teknis)


3.7 Roda/pelek
Individual	Label utama	Sinonim
AlloyWheel	Pelek Paduan Logam	pelek alloy; velg alloy; alloy wheel
SteelWheel	Pelek Baja	velg besi; pelek besi; steel wheel
StandardWheel	Roda Standar	roda bawaan; standard wheel


Jangan menyamakan StandardWheel dengan SteelWheel: standar/bawaan tidak menentukan bahan pelek.
3.8 Kemudi
Individual	Label utama	Sinonim
ElectricPowerSteering	Kemudi Bantu Elektrik	EPS; electric power steering; power steering elektrik
PowerSteering	Kemudi Bantu	power steering; kemudi berbantuan
ManualSteering	Kemudi Manual	manual steering; tanpa power steering


PowerSteering tidak selalu hidrolik. Jika pada OWL asli individual itu khusus hidrolik, ubah label setelah verifikasi.
3.9 Fitur kenyamanan
Individual	Label utama	Sinonim
AC_Double_Blower	AC Dua Blower	AC double blower; AC ganda
AC_SingleBlower	AC Satu Blower	AC single blower; AC satu blower
Apple_CarPlay	Apple CarPlay	CarPlay; konektivitas Apple CarPlay
Audio_System	Sistem Audio	audio mobil; sound system
Keyless_Entry	Akses Tanpa Anak Kunci	keyless entry; buka pintu tanpa kunci
PowerWindow	Jendela Elektrik	power window; kaca elektrik
Sunroof	Sunroof	atap bukaan; atap kaca buka
Wireless_Charger	Pengisi Daya Nirkabel	wireless charger; cas nirkabel; pengisian daya nirkabel


Jangan disamakan: double blower dengan dual-zone AC; keyless entry dengan keyless start; sunroof dengan semua panoramic roof.
3.10 Fitur keselamatan tambahan
Individual	Label utama	Sinonim
Airbag_System	Kantong Udara	airbag; sistem airbag; SRS airbag
Blind_Spot_Monitoring	Pemantauan Titik Buta	BSM; blind spot monitoring; sensor titik buta
Lane_Keep_Assist	Bantuan Penjaga Jalur	LKA; lane keep assist; bantuan menjaga jalur
Sensor_Parkir	Sensor Parkir	parking sensor; sensor bantuan parkir


ABS dan ABS_EBD tetap menggunakan label dari bagian 3.4, tidak perlu dibuat dua entitas.
4. Kamus label untuk Object Property
Properti asli	Label utama	Sinonim pencarian
memilikiMerk	merek	merk; merek mobil; brand
memilikiBahanBakar	bahan bakar	BBM; jenis bahan bakar; tenaga penggerak (perlu konteks)
memilikiTransmisi	transmisi	girboks; gearbox; jenis transmisi
memilikiMesin	mesin	jenis mesin; penggerak
memilikiChasis	sasis	rangka; chassis; chasis
memilikiRoda	roda	jenis roda; pelek; velg (jika nilai properti memang jenis pelek)
memilikiSuspensi	suspensi	sistem suspensi
memilikiRemDepan	rem depan	pengereman depan
memilikiRemBelakang	rem belakang	pengereman belakang
memilikiFiturKemudi	sistem kemudi	fitur kemudi; setir
memilikiFiturKenyamanan	fitur kenyamanan	kenyamanan; fasilitas kabin
memilikiFiturKeselamatan	fitur keselamatan	keselamatan; fitur keamanan berkendara
memilikiKomponen	komponen	bagian mobil; komponen kendaraan


Catatan: sinonim properti dipakai untuk memahami kolom/filter, bukan langsung menghasilkan hasil mobil tanpa pencocokan nilainya.
5. Kamus label untuk Data Property
Properti asli	Label utama	Sinonim pencarian
namaMobil	nama mobil	nama kendaraan; model mobil
harga	harga	harga mobil; banderol
jumlahPenumpang	jumlah penumpang	kapasitas penumpang; kapasitas orang
kapasitasMesin	kapasitas mesin	cc; kubikasi mesin
HP	tenaga mesin (HP)	horsepower; daya mesin dalam HP
tahunProduksi	tahun produksi	tahun pembuatan (cek definisi data)
kapasitasAngkutan	kapasitas angkutan	kapasitas muatan; payload (jika satuannya sesuai)
negaraAsal	negara asal	asal negara
tahunBerdiri	tahun berdiri	tahun pendirian
foto	foto	foto kendaraan
gambar	gambar	gambar kendaraan; tautan gambar (jika berisi URL)


Jangan memakai kursi sebagai sinonim pasti jumlahPenumpang, modelYear sebagai sinonim pasti tahun produksi, atau daya tanpa satuan sebagai sinonim pasti HP. foto dan gambar mungkin menyimpan tipe berbeda: jangan disatukan tanpa inspeksi.
6. Cara menambahkan anotasi ke mobil.owl
Gunakan namespace ontologi yang tercantum pada dokumen awal:
@prefix : <http://www.semanticweb.org/lenovo/ontologies/2026/9/untitled-ontology-8#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

:Automatic rdfs:label "Transmisi Otomatis"@id ;
    skos:altLabel "matic"@id, "matik"@id, "otomatis"@id,
                  "AT", "automatic"@en .

:Bensin rdfs:label "Bensin"@id ;
    skos:altLabel "gasoline"@en, "petrol"@en .

:memilikiTransmisi rdfs:label "transmisi"@id ;
    skos:altLabel "girboks"@id, "gearbox"@en .
Turtle di atas adalah contoh, bukan fragmen XML untuk ditempel langsung ke RDF/XML. Jika mobil.owl berformat RDF/XML, tambahkan anotasi melalui Protégé (Annotations) atau gunakan sintaks RDF/XML berikut di dalam elemen rdf:RDF dengan namespace skos sudah dideklarasikan:
<owl:NamedIndividual rdf:about="http://www.semanticweb.org/lenovo/ontologies/2026/9/untitled-ontology-8#Automatic">
  <rdfs:label xml:lang="id">Transmisi Otomatis</rdfs:label>
  <skos:altLabel xml:lang="id">matic</skos:altLabel>
  <skos:altLabel xml:lang="id">matik</skos:altLabel>
  <skos:altLabel xml:lang="en">automatic</skos:altLabel>
</owl:NamedIndividual>
Jangan menambahkan deklarasi duplikat secara membabi buta: edit anotasi entitas yang sudah ada di Protégé dan pastikan namespace skos (http://www.w3.org/2004/02/skos/core#) dikenali. Jika memakai RDF/XML manual, tambahkan xmlns:skos pada root. Untuk menampilkan bahasa Indonesia di Protégé, atur renderer agar memakai rdfs:label.
7. Arsitektur pencarian Apache Jena
Input pengguna (misalnya "matic")
           |
Normalisasi teks (trim, lowercase, spasi)
           |
SPARQL cari rdfs:label / skos:altLabel
           |
Entitas ditemukan (:Automatic)
           |
Cari mobil yang memilikiTransmisi :Automatic
           |
Tampilkan mobil dan propertinya
7.1 Query menemukan entitas dari kata
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

SELECT DISTINCT ?entitas ?label
WHERE {
  VALUES ?predikat { rdfs:label skos:altLabel }
  ?entitas ?predikat ?label .
  FILTER(CONTAINS(LCASE(STR(?label)), LCASE("matic")))
}
Untuk input pengguna, jangan menyusun SPARQL dengan konkatenasi string mentah. Gunakan ParameterizedSparqlString atau binding parameter yang sesuai.
7.2 Query mencari mobil dari transmisi yang ditemukan
Contoh di bawah mengasumsikan mobil merupakan individual bertipe :Mobil atau subclass-nya yang sudah terinferensi. Sesuaikan dengan class dan hasil reasoner sebenarnya.
PREFIX : <http://www.semanticweb.org/lenovo/ontologies/2026/9/untitled-ontology-8#>
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT DISTINCT ?mobil
WHERE {
  ?mobil rdf:type/rdfs:subClassOf* :Mobil .
  ?mobil :memilikiTransmisi :Automatic .
}
Jika reasoner tidak aktif, rdf:type/rdfs:subClassOf* hanya mengikuti subclass yang tersimpan eksplisit; untuk klasifikasi hasil inferensi, gunakan model inferensi Jena yang tepat.
7.3 Query langsung menghubungkan kata dan mobil
PREFIX : <http://www.semanticweb.org/lenovo/ontologies/2026/9/untitled-ontology-8#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

SELECT DISTINCT ?mobil ?transmisi
WHERE {
  ?mobil :memilikiTransmisi ?transmisi .
  VALUES ?predikat { rdfs:label skos:altLabel }
  ?transmisi ?predikat ?teks .
  FILTER(CONTAINS(LCASE(STR(?teks)), "matic"))
}
Pola ini bisa diperluas ke memilikiBahanBakar, memilikiMerk, memilikiMesin, memilikiFiturKeselamatan, dan properti lain. Pencarian gabungan seperti “Toyota matic 7 penumpang” tidak otomatis dipahami hanya dari label: backend perlu tokenisasi, pemetaan entitas, parsing angka, dan penyusunan filter SPARQL yang sesuai.
7.4 Contoh filter kapasitas penumpang
PREFIX : <http://www.semanticweb.org/lenovo/ontologies/2026/9/untitled-ontology-8#>
PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

SELECT DISTINCT ?mobil
WHERE {
  ?mobil :jumlahPenumpang ?jumlah .
  FILTER(xsd:integer(?jumlah) = 7)
}
Pastikan nilai jumlahPenumpang berupa literal numerik yang valid.
8. Equivalent To yang tetap berguna (opsional)
Di Protégé, definisikan class MobilBensin dengan:
Mobil and (memilikiBahanBakar value Bensin)
Untuk MobilTransmisiOtomatis:
Mobil and (memilikiTransmisi value Automatic)
Ini berlaku hanya jika individual bahan bakar/transmisi benar-benar dipakai dalam relasi OWL dan reasoner mendukung inferensi yang diperlukan. Jangan menyamakan Automatic dengan CVT lewat sameAs; jika keduanya ingin dicakup, buat ekspresi union atau model hierarki yang tepat.
9. Rencana implementasi
1. Cadangkan mobil.owl.
2. Periksa URI, class, individual, object property, data property, domain/range, dan tipe literal pada file asli.
3. Tambahkan rdfs:label dan skos:altLabel pada entitas yang benar-benar ada; jangan buat individual alias baru.
4. Uji konsistensi ontologi dan hasil inferensi di Protégé.
5. Pada Jena, indeks/cari label dan sinonim, lalu ikuti relasi dari mobil ke entitas yang cocok.
6. Uji input matic, matik, automatic, bensin, rem cakram, Toyota, 7 penumpang dan gabungannya; periksa hasil berdasarkan data aktual.
7. Jika pencarian belum mencakup typo (misalnya matc), tambahkan fuzzy matching di aplikasi, bukan owl:sameAs.
10. Kriteria penerimaan
- Pengguna bisa mencari dengan nama Indonesia dan alias yang tercantum.
- Alias Inggris yang lazim tetap didukung.
- URI lama, nilai properti, dan hubungan mobil tidak berubah akibat penambahan anotasi.
- Automatic dan CVT tidak dianggap individu identik.
- Bensin dan Pertamax tidak dianggap individu identik.
- Hasil pencarian mobil diturunkan dari relasi ontologi, bukan sekadar kecocokan teks label.
- Pencarian beberapa kriteria hanya dinyatakan berhasil setelah logika parsing/filter backend diimplementasikan dan diuji.
Status: spesifikasi rancangan berbasis daftar entitas dalam dokumen saran. Belum merupakan modifikasi file mobil.owl asli atau bukti bahwa seluruh nama entitas sudah cocok dengan OWL yang sedang dipakai.