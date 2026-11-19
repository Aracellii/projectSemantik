package com.ontologimobil;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MobilController {

    private static final String MISSING = "urn:missing-property";
    private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";

    // ── Konfigurasi (application.properties) ──────────────────────────────────
    @Value("${ontologi.file:/mobil.owl}")                       private String ontologiFile;
    @Value("${ontologi.root-class:Mobil}")                      private String rootClassName;
    @Value("${ontologi.ignored-classes:NamedIndividual}")       private Set<String> ignoredClasses;
    @Value("${ontologi.property.nama:namaMobil}")               private String propNama;
    @Value("${ontologi.property.merk:memilikiMerk}")            private String propMerk;
    @Value("${ontologi.property.bahan-bakar:memilikiBahanBakar}") private String propBahanBakar;
    @Value("${ontologi.property.penumpang:jumlahPenumpang}")    private String propPenumpang;

    // Model asli dan model hasil inferensi dari ontology.
    private Model baseModel;
    private InfModel model;
    // Class utama yang menjadi induk semua kendaraan.
    private Resource mobilClass;
    // Daftar mobil yang ditemukan saat ontology pertama kali dibaca.
    private final Map<String, Resource> cars = new LinkedHashMap<>();
    // Menghubungkan nama filter dari API dengan property di ontology.
    private final Map<String, String> facetProps = new HashMap<>();

    @PostConstruct
    void loadOntology() {
        // Method ini berjalan otomatis saat aplikasi mulai.
        try (InputStream input = getClass().getResourceAsStream(ontologiFile)) {
            if (input == null) {
                throw new IllegalStateException("Resource " + ontologiFile + " tidak ditemukan");
            }

            // 1. Baca file OWL/RDF.
            baseModel = ModelFactory.createDefaultModel();
            RDFDataMgr.read(baseModel, input, Lang.RDFXML);
            // 2. Buat model dengan OWL reasoner.
            model = ModelFactory.createInfModel(ReasonerRegistry.getOWLReasoner(), baseModel);
            model.prepare();

            // 3. Cari class induk bernama "Mobil".
            mobilClass = typedResources(OWL.Class).stream()
                    .filter(r -> rootClassName.equals(r.getLocalName()) || rootClassName.equals(label(r)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Class '" + rootClassName + "' tidak ditemukan di OWL"));

            facetProps.put("brand", findProperty(propMerk));
            facetProps.put("fuel", findProperty(propBahanBakar));
            facetProps.put("passengers", findProperty(propPenumpang));

            queryCars(null, null, null, null, null).forEach(c -> cars.put(c.getURI(), c));
        } catch (Exception e) {
            closeModels();
            throw new IllegalStateException("Gagal membaca ontology", e);
        }
    }

    @PreDestroy
    void closeModels() {
        if (model != null) model.close();
        if (baseModel != null) baseModel.close();
    }

    // Endpoint untuk mengambil semua mobil.

    @GetMapping("/mobil")
    public List<Mobil> all() {
        return search(null, null, null, null, null);
    }

    @GetMapping("/mobil/search")
    public List<Mobil> search(@RequestParam(required = false) String q,
                              @RequestParam(required = false) String brand,
                              @RequestParam(required = false) String fuel,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String passengers) {
        return queryCars(q, brand, fuel, category, passengers).stream().map(this::summary).toList();
    }

    @GetMapping("/mobil/{id}")
    public ResponseEntity<Mobil> one(@PathVariable String id) {
        Resource r = resourceFor(id);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(summary(r));
    }

    @GetMapping("/mobil/{id}/properties")
    public Map<String, List<String>> propertiesEndpoint(@PathVariable String id) { return properties(required(id)); }

    @GetMapping("/mobil/{id}/relations")
    public List<TripleData> relations(@PathVariable String id) { return triplesFor(id, true); }

    @GetMapping("/mobil/{id}/triples")
    public List<TripleData> triples(@PathVariable String id) { return triplesFor(id, false); }

    @GetMapping("/ontology/statistics")
    public Map<String, Object> statistics() {
        return Map.of(
                "cars", cars.size(),
                "explicitTriples", baseModel.size(),
                "inferredTriples", model.size(),
                "classes", typedResources(OWL.Class).size(),
                "objectProperties", typedResources(OWL.ObjectProperty).size(),
                "datatypeProperties", typedResources(OWL.DatatypeProperty).size(),
                "reasoner", "Jena OWL rule reasoner");
    }

    // Mengambil triple RDF entitas mobil.
    private List<TripleData> triplesFor(String id, boolean relationsOnly) {
        return required(id).listProperties().toList().stream()
                .filter(s -> !relationsOnly || (s.getObject().isResource() && !RDF.type.equals(s.getPredicate())))
                .map(s -> new TripleData(s.getSubject().getURI(), s.getPredicate().getURI(),
                        value(s.getObject()), s.getObject().isURIResource()))
                .toList();
    }

    // * Membuat potongan query untuk satu filter, misalnya filter bahan bakar.  
    private String facet(String filterName, String predicate) {
        return ("FILTER (?%1$s = '' || EXISTS { ?car %2$s ?%1$sValue . "
                + "OPTIONAL { ?%1$sValue (rdfs:label|skos:altLabel) ?%1$sLabel } "
                + "FILTER(LCASE(REPLACE(STR(?%1$sValue), '^.*[#/]', '')) = LCASE(?%1$s) "
                + "|| LCASE(STR(?%1$sLabel)) = LCASE(?%1$s)) }) ")
                .formatted(filterName, predicate);
    }

    private List<Resource> queryCars(String q, String brand, String fuel, String category, String passengers) {
        ParameterizedSparqlString sparql = new ParameterizedSparqlString(
                "SELECT DISTINCT ?car WHERE { ?car a ?carType . ?carType rdfs:subClassOf* ?root . FILTER(isIRI(?car)) FILTER NOT EXISTS { ?car a owl:Class } ");
        sparql.setNsPrefixes(PrefixMapping.Standard);
        sparql.setNsPrefix("skos", SKOS);
        sparql.setIri("root", mobilClass.getURI());

        // Pencarian bebas
        List<String> tokens = Arrays.stream(blank(q).toLowerCase().split("\\s+"))
                .filter(token -> !token.isEmpty())
                .toList();
        for (int i = 0; i < tokens.size(); i++) {
            String fields = Stream.of("?car", "?p%1$d", "?pl%1$d", "?v%1$d", "?l%1$d")
                .map(x -> "REGEX(LCASE(STR(" + x + ")), ?q%1$d)").collect(Collectors.joining(" || "));
            sparql.append(("FILTER (EXISTS { ?car skos:altLabel ?alt%1$d . "
                + "FILTER(REGEX(LCASE(STR(?alt%1$d)), ?q%1$d)) } "
                + "|| EXISTS { ?car ?p%1$d ?v%1$d . "
                + "FILTER(?p%1$d != owl:sameAs && ?p%1$d != owl:differentFrom) "
                + "OPTIONAL { ?v%1$d rdfs:label ?l%1$d } "
                + "OPTIONAL { ?p%1$d rdfs:label ?pl%1$d } FILTER(" + fields + ") }) ").formatted(i));
            sparql.setLiteral("q" + i, wordPattern(tokens.get(i)));
        }

        // Filter pilihan
        Map<String, String> params = Map.of("brand", blank(brand), "fuel", blank(fuel),
                "passengers", blank(passengers), "category", blank(category));
        params.forEach((name, value) -> {
            boolean byProperty = facetProps.containsKey(name);
            sparql.append(facet(name, byProperty ? "?" + name + "Prop" : "a"));
            sparql.setLiteral(name, value);
            if (byProperty) sparql.setIri(name + "Prop", facetProps.get(name));
        });
        sparql.append("} ORDER BY ?car");

        List<Resource> result = new ArrayList<>();
        try (QueryExecution ex = QueryExecutionFactory.create(sparql.asQuery(), model)) {
            ex.execSelect().forEachRemaining(s -> result.add(s.getResource("car")));
        }
        return result;
    }

    private String wordPattern(String token) {
        StringBuilder escaped = new StringBuilder();
        for (char character : token.toCharArray()) {
            if ("\\.^$|?*+()[]{}".indexOf(character) >= 0) escaped.append('\\');
            escaped.append(character);
        }
        return "(^|[^a-z0-9])" + escaped + "([^a-z0-9]|$)";
    }

    private Mobil summary(Resource r) {
        Map<String, List<String>> values = properties(r);
        return new Mobil(encode(r.getURI()), r.getURI(),
                first(values, propNama).orElse(label(r)), types(r),
                first(values, propMerk).orElse(null), first(values, propBahanBakar).orElse(null), values);
    }

    // Mengambil semua property 
    private Map<String, List<String>> properties(Resource r) {
        Map<String, List<String>> result = new TreeMap<>();
        r.listProperties().forEachRemaining(s -> {
            if (RDF.type.equals(s.getPredicate())) return;
            String val = value(s.getObject());
            Stream.of(s.getPredicate().getLocalName(), label(s.getPredicate())).filter(Objects::nonNull).distinct()
                    .forEach(k -> result.computeIfAbsent(k, x -> new ArrayList<>()).add(val));
        });
        return result;
    }

    // Mengambil kategori/class yang dimiliki mobil.
    private List<String> types(Resource r) {
        return model.listObjectsOfProperty(r, RDF.type).filterKeep(RDFNode::isURIResource)
                .mapWith(RDFNode::asResource).toList().stream()
                .filter(t -> !t.equals(mobilClass) && model.contains(t, RDFS.subClassOf, mobilClass)
                        && !ignoredClasses.contains(t.getLocalName()))
                .map(this::label).filter(l -> !ignoredClasses.contains(l)).distinct().toList();
    }

    // Mencari URI property berdasarkan nama lokalnya.
    private String findProperty(String name) {
        return Stream.of(OWL.ObjectProperty, OWL.DatatypeProperty).flatMap(t -> typedResources(t).stream())
                .filter(r -> name.equals(r.getLocalName())).map(Resource::getURI).findFirst().orElse(MISSING);
    }

    private Set<Resource> typedResources(Resource type) {
        return baseModel.listResourcesWithProperty(RDF.type, type).toSet();
    }

    private Optional<String> first(Map<String, List<String>> map, String key) {
        return Optional.ofNullable(map.get(key)).flatMap(l -> l.stream().findFirst());
    }

    private Resource required(String id) {
        Resource r = resourceFor(id);
        if (r == null) throw new NoSuchElementException("Mobil tidak ditemukan");
        return r;
    }

    private Resource resourceFor(String id) {
        try {
            String decoded = URLDecoder.decode(id, StandardCharsets.UTF_8);
            return cars.containsKey(decoded) ? cars.get(decoded)
                    : cars.get(new String(Base64.getUrlDecoder().decode(decoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Label yang ditampilkan
    private String label(Resource r) {
        String ln = r.getLocalName();
        List<Literal> ls = model.listObjectsOfProperty(r, RDFS.label).filterKeep(RDFNode::isLiteral)
                .mapWith(RDFNode::asLiteral).toList();
        return ls.stream().filter(l -> l.getString().equalsIgnoreCase(ln)).findFirst()
                .or(() -> ls.stream().filter(l -> "id".equalsIgnoreCase(l.getLanguage())).findFirst())
                .or(() -> ls.stream().findFirst())
                .map(Literal::getString).orElse(ln == null ? r.getURI() : ln);
    }

    private String value(RDFNode n) { return n.isResource() ? label(n.asResource()) : n.asLiteral().getString(); }

    private String encode(String v) { return Base64.getUrlEncoder().withoutPadding().encodeToString(v.getBytes(StandardCharsets.UTF_8)); }

    private String blank(String v) { return v == null ? "" : v.trim(); }

    // ── Records ───────────────────────────────────────────────────────────────

    public record Mobil(String id, String uri, String name, List<String> categories, String brand, String fuel, Map<String, List<String>> properties) {}
    public record TripleData(String subject, String predicate, String object, boolean objectUri) {}
}
