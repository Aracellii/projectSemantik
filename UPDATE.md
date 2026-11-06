UPDATE.md — Simplifikasi Struktur Project Ontologi Mobil
Tujuan
Sederhanakan struktur project Semantic Web Ontologi Mobil agar mudah dipahami, dijalankan, dan dipresentasikan sebagai project kuliah. Pertahankan semua fitur yang sudah berfungsi. Instruksi ini mengubah struktur, bukan mengurangi kemampuan aplikasi.
Struktur Target
ontologi-mobil/
├── AGENTS.md
├── UPDATE.md
├── README.md
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── ontologimobil/
        │           ├── Application.java
        │           └── MobilController.java
        └── resources/
            ├── mobil.owl
            └── static/
                ├── index.html
                ├── style.css
                └── app.js
Aturan Refactoring
1. Periksa struktur dan kode yang ada sebelum mengubahnya. Sesuaikan package aktual jika berbeda; jangan memindahkan class tanpa memperbarui deklarasi package.
2. Pertahankan Application.java sebagai entry point Spring Boot.
3. Gabungkan logika REST API dan akses Apache Jena ke MobilController.java. Boleh gunakan private method untuk menghindari duplikasi; jangan buat folder controller, service, repository, model, dto, atau config baru.
4. Tidak perlu DTO terpisah. Gunakan record, Map, atau tipe sederhana dalam file Java yang sama untuk response JSON, sesuai kebutuhan.
5. Gunakan src/main/resources/mobil.owl sebagai sumber data. Jangan mengubah isi ontologi asli atau membuat data mobil hardcoded.
6. Gunakan satu halaman index.html, satu stylesheet style.css, dan satu script app.js. Perbarui path asset agar tetap bekerja dari root /.
7. Hindari framework frontend, database, build tool frontend, dan dependensi tambahan yang tidak perlu.
8. Hapus file atau direktori lama yang sudah tidak digunakan setelah semua referensinya dipindahkan dan diverifikasi. Jangan hapus file pengguna yang tidak terkait.
9. Pertahankan API yang digunakan frontend, pencarian, filter, pemilihan mobil, tab detail, relasi ontologi, dan tampilan triple RDF jika sudah tersedia.
10. Jika AGENTS.md meminta struktur lebih kompleks, prioritaskan struktur sederhana dalam UPDATE.md untuk refactoring ini. Instruksi lain di AGENTS.md tetap berlaku.
Persyaratan Teknis
- Java 21, Spring Boot 3.x, Apache Jena 5.x, Maven.
- File OWL dimuat dari classpath, bukan absolute path.
- Namespace dan properti ontologi dibaca dari file asli, tidak ditebak atau di-hardcode.
- Query harus aman untuk input pengguna, dan resources Jena ditutup dengan benar.
- Daftar mobil mencakup instance subclass bila didukung oleh struktur RDF yang ada.
- Nilai yang tidak tersedia ditampilkan sebagai kosong atau Tidak tersedia, bukan dibuat-buat.
- HTML, CSS, dan JavaScript tetap terpisah untuk keterbacaan.
- Desain UI tetap mengikuti screenshot referensi: header terracotta, filter di atas, sidebar daftar mobil, dan panel detail dengan tab.
Langkah Eksekusi untuk Codex
1. Baca AGENTS.md, UPDATE.md, dan struktur project saat ini.
2. Inventarisasi file dan endpoint yang benar-benar digunakan.
3. Refactor secara bertahap ke struktur target dengan perubahan sekecil mungkin.
4. Perbarui import Java, referensi asset frontend, dan README.
5. Jalankan mvn clean test dan mvn package.
6. Bila memungkinkan, jalankan aplikasi dan uji GET /api/mobil serta halaman /.
7. Perbaiki kesalahan yang ditemukan; jangan mengklaim pengujian berhasil jika belum dijalankan.
8. Laporkan daftar file yang dipindahkan/dihapus, fitur yang dipertahankan, hasil pengujian, dan kendala tersisa.
Kriteria Selesai
Project memiliki struktur ringkas seperti target, dapat dibangun dan dijalankan, menampilkan data dari OWL asli, serta mempertahankan fitur frontend dan API yang telah ada.

Kerjakan refactoring langsung. Jangan berhenti pada rencana atau hanya menulis rekomendasi.
