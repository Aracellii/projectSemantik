# Semantic Web Ontologi Mobil

Aplikasi web sederhana untuk mengeksplorasi data mobil dari ontology OWL menggunakan Spring Boot, Apache Jena, SPARQL, dan JavaScript vanilla.

## Menjalankan aplikasi

Pastikan Java 21 dan Maven tersedia, kemudian jalankan:

```bash
mvn spring-boot:run
```

Buka `http://localhost:8080/` untuk menggunakan dashboard.

## Struktur proyek

```text
src/main/java/com/ontologimobil/
├── Application.java
└── MobilController.java
src/main/resources/
├── mobil.owl
└── static/
    ├── index.html
    ├── style.css
    └── app.js
```

`MobilController.java` memuat ontology dari classpath, menemukan individu mobil beserta subclass-nya, dan menyediakan seluruh REST API:

- `GET /api/mobil`
- `GET /api/mobil/{id}`
- `GET /api/mobil/{id}/properties`
- `GET /api/mobil/{id}/relations`
- `GET /api/mobil/{id}/triples`
- `GET /api/ontology/statistics`

Frontend mengambil data langsung dari API, sehingga pencarian, filter, detail, spesifikasi, relasi, dan triple RDF berasal dari `mobil.owl`, bukan data hardcoded.
