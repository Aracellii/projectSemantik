UPDATE.md — Pencarian Ontologi dan Aktivasi Reasoner Apache Jena
Tujuan
Perbarui project Semantic Web Ontologi Mobil yang sudah ada agar pencarian dan filter dilakukan di backend menggunakan Apache Jena + SPARQL, serta mendukung inferensi dari Jena OWL rule reasoner. Frontend hanya mengirim permintaan dan menampilkan hasil. Jangan hardcode daftar mobil, merek, bahan bakar, atau kategori.
Aturan pengerjaan
1. Baca AGENTS.md, MobilController.java, app.js, dan mobil.owl sebelum mengubah kode.
2. Pertahankan struktur sederhana: Application.java, MobilController.java, mobil.owl, index.html, style.css, dan app.js. Jangan membuat lapisan service/DTO tambahan kecuali benar-benar diperlukan.
3. Jangan mengubah isi ontologi asli. Periksa dahulu IRI, class, object property, datatype property, label, dan hubungan subclass yang benar. Jangan menebak nama properti.
4. Pertahankan endpoint yang sudah berfungsi dan format responsnya sejauh memungkinkan.
A. Aktifkan inferensi
Di MobilController.java, method loadOntology() saat ini membuat Model biasa. Ubah agar:
- Membaca RDF/XML ke baseModel menggunakan RDFDataMgr.read.
- Membuat Reasoner reasoner = ReasonerRegistry.getOWLReasoner();.
- Membuat InfModel inferredModel = ModelFactory.createInfModel(reasoner, baseModel); dan memanggil prepare().
- Menggunakan model inferensi untuk discovery mobil, endpoint pencarian, properti, relasi, dan triple jika sesuai.
- Menjaga baseModel terpisah apabila perlu membedakan triple eksplisit dan hasil inferensi.
- Menambahkan penanganan error loading yang jelas dan lifecycle cleanup model.
- Memperhatikan potensi hasil duplikat dan triple bawaan reasoner yang tidak relevan.
Batasan: Jena OWL rule reasoner bukan OWL 2 DL reasoner lengkap. Jangan mengklaim setara dengan HermiT atau Pellet. Dokumentasikan jenis inferensi yang benar-benar didukung dan teruji.
B. Search dan filter harus berbasis ontologi
Tambahkan/sempurnakan endpoint GET /api/mobil/search?q=... serta filter terstruktur bila UI memilikinya, misalnya brand, fuel, category, dan passengers.
- Query SPARQL harus berjalan di backend atas model inferensi.
- Gunakan ParameterizedSparqlString untuk input pengguna; jangan merangkai input mentah ke string SPARQL.
- Pastikan hasil hanya berisi individual mobil, termasuk instance subclass Mobil yang valid, bukan class, property, atau resource ontologi lain.
- Pencarian bebas boleh memeriksa nama/label, literal, dan resource yang terhubung melalui object property yang relevan.
- Filter bahan bakar, merek, kategori, dan kapasitas harus memakai predikat/relasi ontologi yang sebenarnya, bukan contains() atas seluruh properti secara membabi buta.
- Gunakan relasi subclass dan hasil inferensi jika tersedia. Contoh: MobilPenumpang rdfs:subClassOf Mobil dan Avanza rdf:type MobilPenumpang harus membuat Avanza terdeteksi sebagai mobil.
- Untuk bahan bakar, telusuri struktur ontologi nyata. Jika Pertalite merupakan subclass/instance kategori Bensin, dukung pencarian kategori Bensin hanya bila hubungan itu memang dinyatakan dan inferensinya didukung. Jangan menciptakan hubungan tersebut.
- Bila keyword kosong, kembalikan semua individual mobil.
- Hindari duplikasi hasil dengan DISTINCT atau deduplikasi berbasis URI.
- Gunakan URI sebagai identitas resource, bukan hanya local name.
- Jika ada filter yang tidak didukung oleh skema OWL, jangan memalsukan hasil; jelaskan keterbatasannya.
C. Frontend
- Ubah app.js agar search dan filter memanggil endpoint backend melalui fetch.
- Hapus logika hardcoded seperti keyword === 'mobil', daftar bahan bakar statis, atau pencocokan nilai properti di browser untuk menentukan hasil semantik.
- Ambil opsi dropdown filter dari data/endpoint ontologi bila memungkinkan, bukan array tetap di JavaScript.
- Pertahankan desain dashboard yang sudah dibuat: header, pencarian, sidebar daftar mobil, panel detail, dan tab.
- Tampilkan loading, hasil kosong, dan pesan error dengan baik.
- Pastikan pencarian bertahap tidak menampilkan respons lama yang datang terlambat (gunakan AbortController atau mekanisme setara).
D. Pengujian
Jalankan dan laporkan hasil:
1. mvn clean test (atau minimal mvn clean package jika belum ada test).
2. GET /api/mobil mengembalikan seluruh individual mobil.
3. GET /api/mobil/search?q=bensin hanya mengembalikan mobil yang benar-benar berhubungan dengan bensin menurut ontologi.
4. Filter merek, jenis, dan kapasitas bekerja sesuai properti OWL yang ditemukan.
5. Instance dari subclass Mobil muncul walaupun tidak memiliki rdf:type Mobil eksplisit.
6. Pencarian kosong mengembalikan semua mobil.
7. Hasil tidak duplikat; data frontend konsisten dengan respons API.
8. Jika ontologi tidak memuat relasi tertentu, laporkan bahwa skenario tersebut tidak dapat dibuktikan, bukan membuat data uji palsu di file utama.
Definisi selesai
Aplikasi tetap berjalan dengan struktur file sederhana. Reasoner aktif, seluruh pencarian/filter bersumber dari model ontologi melalui SPARQL di backend, dan frontend tidak menyimpan aturan klasifikasi mobil secara hardcoded. Perbarui README.md untuk menjelaskan endpoint, contoh query, dan keterbatasan reasoning.
Instruksi eksekusi untuk Codex
Baca dokumen ini, inspeksi kode serta OWL yang ada, langsung implementasikan perubahan, jalankan pengujian yang tersedia, perbaiki error, lalu ringkas file yang diubah, hasil tes, dan keterbatasan yang masih ada. Jangan berhenti pada rencana saja.