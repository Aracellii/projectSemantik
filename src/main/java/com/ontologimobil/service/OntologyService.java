package com.ontologimobil.service;

import com.ontologimobil.model.Mobil;
import com.ontologimobil.model.TripleData;
import jakarta.annotation.PostConstruct;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;
import java.util.stream.Collectors;

@Service
public class OntologyService {
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private Model model;
    private final Map<String, Resource> cars = new LinkedHashMap<>();

    @PostConstruct
    void load() {
        try (InputStream input = getClass().getResourceAsStream("/mobil.owl")) {
            if (input == null) throw new IllegalStateException("Resource mobil.owl tidak ditemukan");
            model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, input, org.apache.jena.riot.Lang.RDFXML);
            discoverCars();
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membaca ontology mobil", e);
        }
    }

    private void discoverCars() {
        String sparql = """
                SELECT DISTINCT ?car WHERE {
                    ?car ?nameProperty ?nameValue .
                    FILTER(isIRI(?car) && isLiteral(?nameValue))
                    FILTER(STRENDS(STR(?nameProperty), "#namaMobil"))
                }
                ORDER BY ?car
                """;
        try (QueryExecution execution = QueryExecutionFactory.create(QueryFactory.create(sparql), model)) {
            ResultSet results = execution.execSelect();
            while (results.hasNext()) {
                Resource resource = results.next().getResource("car");
                if (resource.isURIResource()) cars.put(resource.getURI(), resource);
            }
        }
    }

    public List<Mobil> all() { return cars.values().stream().map(this::summary).toList(); }

    public Mobil find(String id) {
        Resource resource = resourceFor(id);
        if (resource == null || !cars.containsKey(resource.getURI())) return null;
        return summary(resource);
    }

    public List<TripleData> triples(String id) {
        Resource resource = required(id);
        List<TripleData> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(statement -> result.add(triple(statement)));
        return result;
    }

    public List<TripleData> relations(String id) {
        Resource resource = required(id);
        List<TripleData> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(statement -> {
            if (statement.getObject().isResource() && !RDF_TYPE.equals(statement.getPredicate().getURI()))
                result.add(triple(statement));
        });
        return result;
    }

    public Map<String, Object> statistics() {
        long classes = model.listStatements(null, model.createProperty(RDF_TYPE), model.createResource("http://www.w3.org/2002/07/owl#Class")).toList().size();
        long objectProperties = model.listStatements(null, model.createProperty(RDF_TYPE), model.createResource("http://www.w3.org/2002/07/owl#ObjectProperty")).toList().size();
        long datatypeProperties = model.listStatements(null, model.createProperty(RDF_TYPE), model.createResource("http://www.w3.org/2002/07/owl#DatatypeProperty")).toList().size();
        return Map.of("cars", cars.size(), "triples", model.size(), "classes", classes,
                "objectProperties", objectProperties, "datatypeProperties", datatypeProperties);
    }

    public Map<String, List<String>> properties(String id) {
        Resource resource = required(id);
        Map<String, List<String>> result = new TreeMap<>();
        resource.listProperties().forEachRemaining(statement -> {
            if (!RDF_TYPE.equals(statement.getPredicate().getURI())) {
                result.computeIfAbsent(label(statement.getPredicate()), ignored -> new ArrayList<>()).add(value(statement.getObject()));
            }
        });
        return result;
    }

    private Mobil summary(Resource resource) {
        Map<String, List<String>> values = properties(resource.getURI());
        String name = first(values, "namaMobil").orElse(label(resource));
        String brand = objectValues(resource, "memilikiMerk", "memilikiMerek").stream().findFirst().orElse(null);
        String fuel = objectValues(resource, "memilikiBahanBakar").stream().findFirst().orElse(null);
        List<String> categories = types(resource).stream().filter(type -> !Set.of("Mobil", "MobilBerdasarkanJenis", "MobilPenumpang", "MobilBarang").contains(type)).toList();
        return new Mobil(encode(resource.getURI()), resource.getURI(), name, categories, brand, fuel, values);
    }

    private List<String> objectValues(Resource resource, String... propertyNames) {
        Set<String> wanted = Set.of(propertyNames);
        List<String> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(statement -> {
            if (wanted.contains(label(statement.getPredicate()))) result.add(value(statement.getObject()));
        });
        return result;
    }

    private List<String> types(Resource resource) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        resource.listProperties(model.createProperty(RDF_TYPE)).forEachRemaining(statement -> collectType(statement.getObject(), result, seen));
        return result;
    }

    private void collectType(RDFNode node, List<String> result, Set<String> seen) {
        if (!node.isURIResource() || !seen.add(node.asResource().getURI())) return;
        String name = label(node.asResource());
        if (!name.equals("NamedIndividual")) result.add(name);
        Property parent = model.createProperty("http://www.w3.org/2000/01/rdf-schema#subClassOf");
        node.asResource().listProperties(parent).forEachRemaining(s -> collectType(s.getObject(), result, seen));
    }

    private TripleData triple(Statement statement) {
        RDFNode object = statement.getObject();
        return new TripleData(statement.getSubject().getURI(), statement.getPredicate().getURI(), value(object), object.isURIResource());
    }

    private Resource required(String id) {
        Resource result = resourceFor(id);
        if (result == null || !cars.containsKey(result.getURI())) throw new NoSuchElementException("Mobil tidak ditemukan");
        return result;
    }

    private Resource resourceFor(String id) {
        try {
            String decoded = URLDecoder.decode(id, StandardCharsets.UTF_8);
            if (cars.containsKey(decoded)) return cars.get(decoded);
            return cars.get(new String(Base64.getUrlDecoder().decode(decoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) { return null; }
    }

    private Property property(String localName) {
        return model.listStatements(null, model.createProperty(RDF_TYPE), model.createResource("http://www.w3.org/2002/07/owl#DatatypeProperty"))
                .toList().stream().map(Statement::getSubject).filter(Resource::isURIResource)
                .map(Resource::getURI).filter(uri -> uri.endsWith("#" + localName)).map(model::createProperty).findFirst().orElse(null);
    }

    private String label(Resource resource) { return resource.getLocalName() == null ? resource.getURI() : resource.getLocalName(); }
    private String value(RDFNode node) { return node.isResource() ? label(node.asResource()) : node.asLiteral().getString(); }
    private Optional<String> first(Map<String, List<String>> values, String key) { return values.entrySet().stream().filter(e -> e.getKey().equals(key)).flatMap(e -> e.getValue().stream()).findFirst(); }
    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
}
