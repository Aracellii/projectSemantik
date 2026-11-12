package com.ontologimobil;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MobilController {

    // ── URI standar RDF/OWL/RDFS ─────────────────────────────────────────────
    private static final String RDF_TYPE            = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_SUBCLASS       = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String RDFS_LABEL          = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String SKOS_ALTLABEL       = "http://www.w3.org/2004/02/skos/core#altLabel";
    private static final String OWL_CLASS           = "http://www.w3.org/2002/07/owl#Class";
    private static final String OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty";
    private static final String OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty";

    // ── Konfigurasi dari application.properties ───────────────────────────────
    @Value("${ontologi.file:/mobil.owl}")
    private String ontologiFile;

    @Value("${ontologi.root-class:Mobil}")
    private String rootClassName;

    @Value("${ontologi.ignored-classes:NamedIndividual}")
    private String ignoredClassesRaw;

    @Value("${ontologi.property.nama:namaMobil}")
    private String propNama;

    @Value("${ontologi.property.merk:memilikiMerk}")
    private String propMerk;

    @Value("${ontologi.property.bahan-bakar:memilikiBahanBakar}")
    private String propBahanBakar;

    @Value("${ontologi.property.penumpang:jumlahPenumpang}")
    private String propPenumpang;

    // ── State internal ────────────────────────────────────────────────────────
    private Model baseModel;
    private InfModel model;
    private Resource mobilClass;
    private Set<String> ignoredClasses;
    private final Map<String, Resource> cars = new LinkedHashMap<>();
    private Property nameProperty, brandProperty, fuelProperty, passengersProperty;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    void loadOntology() {
        try (InputStream input = getClass().getResourceAsStream(ontologiFile)) {

            if (input == null)
                throw new IllegalStateException("Resource " + ontologiFile + " tidak ditemukan");

            baseModel = ModelFactory.createDefaultModel();
            RDFDataMgr.read(baseModel, input, Lang.RDFXML);

            Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
            model = ModelFactory.createInfModel(reasoner, baseModel);
            model.prepare();

            // Ambil root class dari konfigurasi
            mobilClass = findClass(rootClassName)
                    .orElseThrow(() -> new IllegalStateException("Class '" + rootClassName + "' tidak ditemukan di OWL"));

            // Parse daftar class yang diabaikan dari konfigurasi
            ignoredClasses = new HashSet<>(Arrays.asList(ignoredClassesRaw.split("\\s*,\\s*")));

            // Temukan property dari OWL berdasarkan nama di konfigurasi
            nameProperty       = findProperty(propNama);
            brandProperty      = findProperty(propMerk);
            fuelProperty       = findProperty(propBahanBakar);
            passengersProperty = findProperty(propPenumpang);

            discoverCars();

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

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping("/mobil")
    public List<Mobil> all() { return search(null, null, null, null, null); }

    @GetMapping("/mobil/search")
    public List<Mobil> searchEndpoint(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String brand,
                                      @RequestParam(required = false) String fuel,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String passengers) {
        return search(q, brand, fuel, category, passengers);
    }

    @GetMapping("/mobil/{id}")
    public ResponseEntity<Mobil> one(@PathVariable String id) {
        Resource resource = resourceFor(id);
        return resource == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(summary(resource));
    }

    @GetMapping("/mobil/{id}/properties")
    public Map<String, List<String>> propertiesEndpoint(@PathVariable String id) {
        return properties(required(id));
    }

    @GetMapping("/mobil/{id}/relations")
    public List<TripleData> relations(@PathVariable String id) {
        Resource resource = required(id);
        List<TripleData> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(s -> {
            if (s.getObject().isResource() && !RDF_TYPE.equals(s.getPredicate().getURI()))
                result.add(triple(s));
        });
        return result;
    }

    @GetMapping("/mobil/{id}/triples")
    public List<TripleData> triples(@PathVariable String id) {
        Resource resource = required(id);
        List<TripleData> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(s -> result.add(triple(s)));
        return result;
    }

    @GetMapping("/ontology/statistics")
    public Map<String, Object> statistics() {
        return Map.of(
                "cars", cars.size(),
                "explicitTriples", baseModel.size(),
                "inferredTriples", model.size(),
                "classes", typedResources(OWL_CLASS).size(),
                "objectProperties", typedResources(OWL_OBJECT_PROPERTY).size(),
                "datatypeProperties", typedResources(OWL_DATATYPE_PROPERTY).size(),
                "reasoner", "Jena OWL rule reasoner"
        );
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    private List<Mobil> search(String q, String brand, String fuel, String category, String passengers) {
        StringBuilder sparqlString = new StringBuilder();
        sparqlString.append("SELECT DISTINCT ?car WHERE { ?car a ?carType . ?carType rdfs:subClassOf* ?root . ");
        sparqlString.append("FILTER(isIRI(?car)) FILTER NOT EXISTS { ?car a owl:Class } ");

        List<String> tokens = Arrays.stream(blank(q).toLowerCase().split("\\s+"))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

        for (int i = 0; i < tokens.size(); i++) {
            sparqlString.append("FILTER EXISTS { ")
                    .append("?car ?p").append(i).append(" ?v").append(i).append(" . ")
                    .append("OPTIONAL { ?v").append(i).append(" (rdfs:label|skos:altLabel) ?lbl").append(i).append(" } ")
                    .append("OPTIONAL { ?p").append(i).append(" (rdfs:label|skos:altLabel) ?propLbl").append(i).append(" } ")
                    .append("FILTER(CONTAINS(LCASE(STR(?car)), ?q").append(i).append(") || ")
                    .append("CONTAINS(LCASE(STR(?p").append(i).append(")), ?q").append(i).append(") || ")
                    .append("CONTAINS(LCASE(STR(?propLbl").append(i).append(")), ?q").append(i).append(") || ")
                    .append("CONTAINS(LCASE(STR(?v").append(i).append(")), ?q").append(i).append(") || ")
                    .append("CONTAINS(LCASE(STR(?lbl").append(i).append(")), ?q").append(i).append(")) ")
                    .append("} ");
        }

        sparqlString.append("FILTER (?brand = '' || EXISTS { ?car ?brandPredicate ?brandValue . FILTER(?brandPredicate = ?brandProperty) OPTIONAL { ?brandValue (rdfs:label|skos:altLabel) ?brandLabel } FILTER(LCASE(REPLACE(STR(?brandValue), '^.*[#/]', '')) = LCASE(?brand) || LCASE(STR(?brandLabel)) = LCASE(?brand)) }) ");
        sparqlString.append("FILTER (?fuel = '' || EXISTS { ?car ?fuelPredicate ?fuelValue . FILTER(?fuelPredicate = ?fuelProperty) OPTIONAL { ?fuelValue (rdfs:label|skos:altLabel) ?fuelLabel } FILTER(LCASE(REPLACE(STR(?fuelValue), '^.*[#/]', '')) = LCASE(?fuel) || LCASE(STR(?fuelLabel)) = LCASE(?fuel)) }) ");
        sparqlString.append("FILTER (?passengers = '' || EXISTS { ?car ?passengersPredicate ?passengersValue . FILTER(?passengersPredicate = ?passengersProperty) FILTER(STR(?passengersValue) = ?passengers) }) ");
        sparqlString.append("FILTER (?category = '' || EXISTS { ?car a ?selectedCategory . OPTIONAL { ?selectedCategory (rdfs:label|skos:altLabel) ?categoryLabel } FILTER(LCASE(REPLACE(STR(?selectedCategory), '^.*[#/]', '')) = LCASE(?category) || LCASE(STR(?categoryLabel)) = LCASE(?category)) }) ");
        sparqlString.append("} ORDER BY ?car");

        ParameterizedSparqlString sparql = new ParameterizedSparqlString(sparqlString.toString());
        sparql.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        sparql.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#");
        sparql.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
        sparql.setIri("root", mobilClass.getURI());
        for (int i = 0; i < tokens.size(); i++) sparql.setLiteral("q" + i, tokens.get(i));
        sparql.setLiteral("brand", blank(brand));
        sparql.setLiteral("fuel", blank(fuel));
        sparql.setLiteral("category", blank(category));
        sparql.setLiteral("passengers", blank(passengers));
        sparql.setIri("brandProperty", propertyUri(brandProperty));
        sparql.setIri("fuelProperty", propertyUri(fuelProperty));
        sparql.setIri("passengersProperty", propertyUri(passengersProperty));

        List<Mobil> result = new ArrayList<>();
        try (QueryExecution execution = QueryExecutionFactory.create(sparql.asQuery(), model)) {
            ResultSet results = execution.execSelect();
            while (results.hasNext()) {
                Resource car = results.next().getResource("car");
                if (cars.containsKey(car.getURI())) result.add(summary(car));
            }
        }
        return result;
    }

    private void discoverCars() {
        ParameterizedSparqlString sparql = new ParameterizedSparqlString(
                "SELECT DISTINCT ?car WHERE { ?car a ?type . ?type rdfs:subClassOf* ?root . FILTER(isIRI(?car)) FILTER NOT EXISTS { ?car a owl:Class } } ORDER BY ?car");
        sparql.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        sparql.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
        sparql.setIri("root", mobilClass.getURI());
        try (QueryExecution execution = QueryExecutionFactory.create(sparql.asQuery(), model)) {
            ResultSet results = execution.execSelect();
            while (results.hasNext()) {
                Resource car = results.next().getResource("car");
                cars.put(car.getURI(), car);
            }
        }
    }

    private Mobil summary(Resource resource) {
        Map<String, List<String>> values = properties(resource);
        // Nama, merk, dan bahan bakar diambil dari properti yang dikonfigurasi
        String name  = firstFromMap(values, propNama)
                .orElseGet(() -> nameProperty == null ? label(resource) : firstValue(resource, nameProperty).orElse(label(resource)));
        String brand = firstFromMap(values, propMerk)
                .orElseGet(() -> brandProperty == null ? null : firstValue(resource, brandProperty).orElse(null));
        String fuel  = firstFromMap(values, propBahanBakar)
                .orElseGet(() -> fuelProperty == null ? null : firstValue(resource, fuelProperty).orElse(null));
        return new Mobil(encode(resource.getURI()), resource.getURI(), name, types(resource), brand, fuel, values);
    }

    private Map<String, List<String>> properties(Resource resource) {
        Map<String, List<String>> result = new TreeMap<>();
        resource.listProperties().forEachRemaining(s -> {
            if (!RDF_TYPE.equals(s.getPredicate().getURI())) {
                String val = value(s.getObject());
                String localKey = s.getPredicate().getLocalName();
                if (localKey != null) {
                    result.computeIfAbsent(localKey, ignored -> new ArrayList<>()).add(val);
                }
                String labelKey = label(s.getPredicate());
                if (labelKey != null && !labelKey.equals(localKey)) {
                    result.computeIfAbsent(labelKey, ignored -> new ArrayList<>()).add(val);
                }
            }
        });
        return result;
    }

    private List<String> types(Resource resource) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        resource.listProperties(model.createProperty(RDF_TYPE))
                .forEachRemaining(s -> collectType(s.getObject(), result, seen));
        return result;
    }

    private void collectType(RDFNode node, List<String> result, Set<String> seen) {
        if (!node.isURIResource() || !seen.add(node.asResource().getURI())) return;
        Resource type = node.asResource();
        String lbl = label(type);
        // Class diabaikan jika ada di daftar ignoredClasses (dari konfigurasi)
        if (!type.equals(mobilClass) && !ignoredClasses.contains(lbl) && !ignoredClasses.contains(type.getLocalName()) && isUnderMobil(type, new HashSet<>()))
            result.add(lbl);
        type.listProperties(model.createProperty(RDFS_SUBCLASS))
                .forEachRemaining(s -> collectType(s.getObject(), result, seen));
    }

    private boolean isUnderMobil(Resource type, Set<String> visited) {
        if (type.equals(mobilClass)) return true;
        if (!type.isURIResource() || !visited.add(type.getURI())) return false;
        return type.listProperties(model.createProperty(RDFS_SUBCLASS)).toList().stream()
                .filter(s -> s.getObject().isResource())
                .map(Statement::getResource)
                .anyMatch(parent -> isUnderMobil(parent, visited));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Optional<Resource> findClass(String localName) {
        return typedResources(OWL_CLASS).stream()
                .filter(r -> localName.equals(local(r.getURI())) || localName.equals(label(r)))
                .findFirst();
    }

    private Property findProperty(String localName) {
        return StreamSupport.stream(typedResources(OWL_OBJECT_PROPERTY).spliterator(), false)
                .map(Resource::getURI).filter(uri -> localName.equals(local(uri))).findFirst()
                .map(model::createProperty)
                .orElseGet(() -> StreamSupport.stream(typedResources(OWL_DATATYPE_PROPERTY).spliterator(), false)
                        .map(Resource::getURI).filter(uri -> localName.equals(local(uri))).findFirst()
                        .map(model::createProperty).orElse(null));
    }

    private Optional<String> firstFromMap(Map<String, List<String>> map, String key) {
        List<String> list = map.get(key);
        return (list != null && !list.isEmpty()) ? Optional.of(list.get(0)) : Optional.empty();
    }

    private String local(String uri) {
        int i = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/'));
        return uri.substring(i + 1);
    }

    private Optional<String> firstValue(Resource resource, Property property) {
        return resource.listProperties(property).toList().stream()
                .map(Statement::getObject).map(this::value).findFirst();
    }

    private Set<Resource> typedResources(String typeUri) {
        Set<Resource> result = new LinkedHashSet<>();
        baseModel.listResourcesWithProperty(baseModel.createProperty(RDF_TYPE), baseModel.createResource(typeUri))
                .forEachRemaining(result::add);
        return result;
    }

    private Resource required(String id) {
        Resource resource = resourceFor(id);
        if (resource == null) throw new NoSuchElementException("Mobil tidak ditemukan");
        return resource;
    }

    private Resource resourceFor(String id) {
        try {
            String decoded = URLDecoder.decode(id, StandardCharsets.UTF_8);
            if (cars.containsKey(decoded)) return cars.get(decoded);
            return cars.get(new String(Base64.getUrlDecoder().decode(decoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private TripleData triple(Statement s) {
        RDFNode object = s.getObject();
        return new TripleData(s.getSubject().getURI(), s.getPredicate().getURI(), value(object), object.isURIResource());
    }

    private String label(Resource resource) {
        List<Statement> labels = resource.listProperties(model.createProperty(RDFS_LABEL)).toList();
        if (!labels.isEmpty()) {
            String localName = resource.getLocalName();
            if (localName != null) {
                // 1. Prioritaskan label yang cocok dengan nama asli entitas (localName)
                for (Statement s : labels) {
                    if (s.getObject().isLiteral() && s.getString().equalsIgnoreCase(localName)) {
                        return s.getString();
                    }
                }
            }
            // 2. Prioritaskan label yang memiliki tag bahasa Indonesia @id
            for (Statement s : labels) {
                if (s.getObject().isLiteral() && "id".equalsIgnoreCase(s.getLanguage())) {
                    return s.getString();
                }
            }
            // 3. Fallback ke label pertama yang tersedia
            for (Statement s : labels) {
                if (s.getObject().isLiteral()) {
                    return s.getString();
                }
            }
        }
        return resource.getLocalName() == null ? resource.getURI() : resource.getLocalName();
    }

    private String value(RDFNode node) {
        return node.isResource() ? label(node.asResource()) : node.asLiteral().getString();
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String blank(String value) { return value == null ? "" : value.trim(); }

    private String propertyUri(Property property) {
        return property == null ? "urn:missing-property" : property.getURI();
    }

    // ── Records ───────────────────────────────────────────────────────────────

    public record Mobil(String id, String uri, String name, List<String> categories, String brand, String fuel, Map<String, List<String>> properties) {}
    public record TripleData(String subject, String predicate, String object, boolean objectUri) {}
}
