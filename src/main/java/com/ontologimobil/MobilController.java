package com.ontologimobil;

import jakarta.annotation.PostConstruct;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MobilController {
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";
    private static final String OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty";
    private static final String OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty";

    private Model model;
    private final Map<String, Resource> cars = new LinkedHashMap<>();
    private Property nameProperty;

    @PostConstruct
    void loadOntology() {
        try (InputStream input = getClass().getResourceAsStream("/mobil.owl")) {
            if (input == null) throw new IllegalStateException("Resource mobil.owl tidak ditemukan");
            model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, input, Lang.RDFXML);
            nameProperty = findNameProperty();
            discoverCars();
        } catch (Exception exception) {
            throw new IllegalStateException("Gagal membaca ontology mobil", exception);
        }
    }

    @GetMapping("/mobil")
    public List<Mobil> all() {
        return cars.values().stream().map(this::summary).toList();
    }

    @GetMapping("/mobil/{id}")
    public ResponseEntity<Mobil> one(@PathVariable String id) {
        Resource resource = resourceFor(id);
        return resource == null || !cars.containsKey(resource.getURI())
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(summary(resource));
    }

    @GetMapping("/mobil/{id}/properties")
    public Map<String, List<String>> properties(@PathVariable String id) {
        return properties(required(id));
    }

    @GetMapping("/mobil/{id}/relations")
    public List<TripleData> relations(@PathVariable String id) {
        Resource resource = required(id);
        List<TripleData> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(statement -> {
            if (statement.getObject().isResource() && !RDF_TYPE.equals(statement.getPredicate().getURI())) {
                result.add(triple(statement));
            }
        });
        return result;
    }

    @GetMapping("/mobil/{id}/triples")
    public List<TripleData> triples(@PathVariable String id) {
        Resource resource = required(id);
        List<TripleData> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(statement -> result.add(triple(statement)));
        return result;
    }

    @GetMapping("/ontology/statistics")
    public Map<String, Object> statistics() {
        return Map.of("cars", cars.size(), "triples", model.size(),
                "classes", typedResources(OWL_CLASS).size(),
                "objectProperties", typedResources(OWL_OBJECT_PROPERTY).size(),
                "datatypeProperties", typedResources(OWL_DATATYPE_PROPERTY).size());
    }

    private void discoverCars() {
        String query = "SELECT DISTINCT ?car WHERE { ?car <" + RDF_TYPE + "> ?type . FILTER(isIRI(?car)) } ORDER BY ?car";
        try (QueryExecution execution = QueryExecutionFactory.create(QueryFactory.create(query), model)) {
            ResultSet results = execution.execSelect();
            while (results.hasNext()) {
                Resource car = results.next().getResource("car");
                if (isCarIndividual(car)) cars.put(car.getURI(), car);
            }
        }
    }

    private boolean isCarIndividual(Resource resource) {
        Set<String> visited = new HashSet<>();
        List<Resource> types = resource.listProperties(model.createProperty(RDF_TYPE)).toList().stream()
                .map(Statement::getObject).filter(RDFNode::isURIResource).map(RDFNode::asResource).toList();
        return types.stream().anyMatch(type -> isCarClass(type, visited));
    }

    private boolean isCarClass(Resource type, Set<String> visited) {
        if (!type.isURIResource() || !visited.add(type.getURI())) return false;
        if ("Mobil".equals(label(type))) return true;
        return type.listProperties(model.createProperty(RDFS_SUBCLASS)).toList().stream()
                .map(Statement::getObject).filter(RDFNode::isURIResource).map(RDFNode::asResource)
                .anyMatch(parent -> isCarClass(parent, visited));
    }

    private Property findNameProperty() {
        return typedResources(OWL_DATATYPE_PROPERTY).stream()
                .filter(property -> label(property).toLowerCase().contains("nama"))
                .findFirst().map(resource -> model.createProperty(resource.getURI())).orElse(null);
    }

    private Mobil summary(Resource resource) {
        Map<String, List<String>> values = properties(resource);
        String name = nameProperty == null ? label(resource) : firstValue(resource, nameProperty).orElse(label(resource));
        String brand = objectValue(resource, "merk", "merek").orElse(null);
        String fuel = objectValue(resource, "bahanbakar").orElse(null);
        List<String> categories = types(resource).stream().filter(type -> !"Mobil".equals(type)).toList();
        return new Mobil(encode(resource.getURI()), resource.getURI(), name, categories, brand, fuel, values);
    }

    private Map<String, List<String>> properties(Resource resource) {
        Map<String, List<String>> result = new TreeMap<>();
        resource.listProperties().forEachRemaining(statement -> {
            if (!RDF_TYPE.equals(statement.getPredicate().getURI())) {
                result.computeIfAbsent(label(statement.getPredicate()), ignored -> new ArrayList<>())
                        .add(value(statement.getObject()));
            }
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
        Resource type = node.asResource();
        if (!"NamedIndividual".equals(label(type)) && !"Mobil".equals(label(type))) result.add(label(type));
        type.listProperties(model.createProperty(RDFS_SUBCLASS)).forEachRemaining(statement -> collectType(statement.getObject(), result, seen));
    }

    private Optional<String> firstValue(Resource resource, Property property) {
        return resource.listProperties(property).toList().stream().map(Statement::getObject).map(this::value).findFirst();
    }

    private Optional<String> objectValue(Resource resource, String... fragments) {
        return resource.listProperties().toList().stream()
                .filter(statement -> statement.getObject().isResource())
                .filter(statement -> containsAny(label(statement.getPredicate()), fragments))
                .map(statement -> value(statement.getObject())).findFirst();
    }

    private boolean containsAny(String text, String... fragments) {
        String normalized = text.toLowerCase();
        for (String fragment : fragments) if (normalized.contains(fragment)) return true;
        return false;
    }

    private Set<Resource> typedResources(String typeUri) {
        Set<Resource> result = new LinkedHashSet<>();
        model.listResourcesWithProperty(model.createProperty(RDF_TYPE), model.createResource(typeUri)).forEachRemaining(result::add);
        return result;
    }

    private Resource required(String id) {
        Resource resource = resourceFor(id);
        if (resource == null || !cars.containsKey(resource.getURI())) throw new NoSuchElementException("Mobil tidak ditemukan");
        return resource;
    }

    private Resource resourceFor(String id) {
        try {
            String decoded = URLDecoder.decode(id, StandardCharsets.UTF_8);
            if (cars.containsKey(decoded)) return cars.get(decoded);
            return cars.get(new String(Base64.getUrlDecoder().decode(decoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private TripleData triple(Statement statement) {
        RDFNode object = statement.getObject();
        return new TripleData(statement.getSubject().getURI(), statement.getPredicate().getURI(), value(object), object.isURIResource());
    }

    private String label(Resource resource) {
        return resource.getLocalName() == null ? resource.getURI() : resource.getLocalName();
    }

    private String value(RDFNode node) {
        return node.isResource() ? label(node.asResource()) : node.asLiteral().getString();
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public record Mobil(String id, String uri, String name, List<String> categories,
                        String brand, String fuel, Map<String, List<String>> properties) {}

    public record TripleData(String subject, String predicate, String object, boolean objectUri) {}
}
