package com.ontologimobil;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
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
    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    private static final String RDFS_SUBCLASS = "http://www.w3.org/2000/01/rdf-schema#subClassOf";
    private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String OWL_CLASS = "http://www.w3.org/2002/07/owl#Class";
    private static final String OWL_OBJECT_PROPERTY = "http://www.w3.org/2002/07/owl#ObjectProperty";
    private static final String OWL_DATATYPE_PROPERTY = "http://www.w3.org/2002/07/owl#DatatypeProperty";

    private Model baseModel;
    private InfModel model;
    private Resource mobilClass;
    private final Map<String, Resource> cars = new LinkedHashMap<>();
    private Property nameProperty, brandProperty, fuelProperty, passengersProperty;

private Map<String, List<String>> getAllProperties(
        Resource mobil) {

    Map<String, List<String>> properties =
        new LinkedHashMap<>();

    mobil.listProperties().forEachRemaining(statement -> {

        String property =
            statement.getPredicate().getLocalName();

        RDFNode object = statement.getObject();

        String value;

        if (object.isLiteral()) {
            value = object.asLiteral().getString();
        } else if (object.isURIResource()) {
            value = object.asResource().getLocalName();
        } else {
            value = object.toString();
        }

        properties
            .computeIfAbsent(
                property,
                key -> new ArrayList<>()
            )
            .add(value);
    });

    return properties;
}

    @PostConstruct
    
void loadOntology() {
    try (InputStream input =
            getClass().getResourceAsStream("/mobil.owl")) {

        if (input == null) {
            throw new IllegalStateException(
                "Resource mobil.owl tidak ditemukan"
            );
        }

        baseModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(baseModel, input, Lang.RDFXML);

        Reasoner reasoner =
            ReasonerRegistry.getOWLReasoner();

        model = ModelFactory.createInfModel(
            reasoner, baseModel
        );

        model.prepare();

        mobilClass = findClass("Mobil")
            .orElseThrow(() ->
                new IllegalStateException(
                    "Class Mobil tidak ditemukan"
                )
            );

        discoverCars();

    } catch (Exception exception) {
        closeModels();
        throw new IllegalStateException(
            "Gagal membaca ontology",
            exception
        );
    }
}


    @PreDestroy
    void closeModels() {
        if (model != null) model.close();
        if (baseModel != null) baseModel.close();
    }

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
    public Map<String, List<String>> properties(@PathVariable String id) { return properties(required(id)); }

    @GetMapping("/mobil/{id}/relations")
    public List<TripleData> relations(@PathVariable String id) {
        Resource resource = required(id);
        List<TripleData> result = new ArrayList<>();
        resource.listProperties().forEachRemaining(s -> {
            if (s.getObject().isResource() && !RDF_TYPE.equals(s.getPredicate().getURI())) result.add(triple(s));
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
        return Map.of("cars", cars.size(), "explicitTriples", baseModel.size(), "inferredTriples", model.size(),
                "classes", typedResources(OWL_CLASS).size(), "objectProperties", typedResources(OWL_OBJECT_PROPERTY).size(),
                "datatypeProperties", typedResources(OWL_DATATYPE_PROPERTY).size(), "reasoner", "Jena OWL rule reasoner");
    }

    private List<Mobil> search(String q, String brand, String fuel, String category, String passengers) {
        StringBuilder sparqlString = new StringBuilder();
        sparqlString.append("SELECT DISTINCT ?car WHERE { ?car a ?carType . ?carType rdfs:subClassOf* ?root . ");
        sparqlString.append("FILTER(isIRI(?car)) FILTER NOT EXISTS { ?car a owl:Class } ");

        List<String> tokens = Arrays.stream(blank(q).toLowerCase().split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        for (int i = 0; i < tokens.size(); i++) {
            sparqlString.append("FILTER EXISTS { ")
                    .append("?car ?p").append(i).append(" ?v").append(i).append(" . ")
                    .append("OPTIONAL { ?v").append(i).append(" rdfs:label ?lbl").append(i).append(" } ")
                    .append("FILTER(CONTAINS(LCASE(STR(?car)), ?q").append(i).append(") || ")
                    .append("CONTAINS(LCASE(STR(?p").append(i).append(")), ?q").append(i).append(") || ")
                    .append("CONTAINS(LCASE(STR(?v").append(i).append(")), ?q").append(i).append(") || ")
                    .append("CONTAINS(LCASE(STR(?lbl").append(i).append(")), ?q").append(i).append(")) ")
                    .append("} ");
        }

        sparqlString.append("FILTER (?brand = '' || EXISTS { ?car ?brandPredicate ?brandValue . FILTER(?brandPredicate = ?brandProperty) OPTIONAL { ?brandValue rdfs:label ?brandLabel } FILTER(LCASE(REPLACE(STR(?brandValue), '^.*[#/]', '')) = LCASE(?brand) || LCASE(STR(?brandLabel)) = LCASE(?brand)) }) ");
        sparqlString.append("FILTER (?fuel = '' || EXISTS { ?car ?fuelPredicate ?fuelValue . FILTER(?fuelPredicate = ?fuelProperty) OPTIONAL { ?fuelValue rdfs:label ?fuelLabel } FILTER(LCASE(REPLACE(STR(?fuelValue), '^.*[#/]', '')) = LCASE(?fuel) || LCASE(STR(?fuelLabel)) = LCASE(?fuel)) }) ");
        sparqlString.append("FILTER (?passengers = '' || EXISTS { ?car ?passengersPredicate ?passengersValue . FILTER(?passengersPredicate = ?passengersProperty) FILTER(STR(?passengersValue) = ?passengers) }) ");
        sparqlString.append("FILTER (?category = '' || EXISTS { ?car a ?selectedCategory . OPTIONAL { ?selectedCategory rdfs:label ?categoryLabel } FILTER(LCASE(REPLACE(STR(?selectedCategory), '^.*[#/]', '')) = LCASE(?category) || LCASE(STR(?categoryLabel)) = LCASE(?category)) }) ");
        sparqlString.append("} ORDER BY ?car");

        ParameterizedSparqlString sparql = new ParameterizedSparqlString(sparqlString.toString());
        sparql.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        sparql.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
        sparql.setIri("root", mobilClass.getURI());
        for (int i = 0; i < tokens.size(); i++) {
            sparql.setLiteral("q" + i, tokens.get(i));
        }
        sparql.setLiteral("brand", blank(brand)); sparql.setLiteral("fuel", blank(fuel));
        sparql.setLiteral("category", blank(category)); sparql.setLiteral("passengers", blank(passengers));
        sparql.setIri("brandProperty", propertyUri(brandProperty)); sparql.setIri("fuelProperty", propertyUri(fuelProperty));
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
        sparql.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#"); sparql.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
        sparql.setIri("root", mobilClass.getURI());
        try (QueryExecution execution = QueryExecutionFactory.create(sparql.asQuery(), model)) {
            ResultSet results = execution.execSelect();
            while (results.hasNext()) { Resource car = results.next().getResource("car"); cars.put(car.getURI(), car); }
        }
    }

    private Mobil summary(Resource resource) {
        Map<String, List<String>> values = properties(resource);
        String name = nameProperty == null ? label(resource) : firstValue(resource, nameProperty).orElse(label(resource));
        String brand = brandProperty == null ? null : firstValue(resource, brandProperty).orElse(null);
        String fuel = fuelProperty == null ? null : firstValue(resource, fuelProperty).orElse(null);
        return new Mobil(encode(resource.getURI()), resource.getURI(), name, types(resource), brand, fuel, values);
    }

    private Map<String, List<String>> properties(Resource resource) {
        Map<String, List<String>> result = new TreeMap<>();
        resource.listProperties().forEachRemaining(s -> { if (!RDF_TYPE.equals(s.getPredicate().getURI())) result.computeIfAbsent(label(s.getPredicate()), ignored -> new ArrayList<>()).add(value(s.getObject())); });
        return result;
    }

    private List<String> types(Resource resource) {
        Set<String> seen = new LinkedHashSet<>(); List<String> result = new ArrayList<>();
        resource.listProperties(model.createProperty(RDF_TYPE)).forEachRemaining(s -> collectType(s.getObject(), result, seen));
        return result;
    }

    private void collectType(RDFNode node, List<String> result, Set<String> seen) {
        if (!node.isURIResource() || !seen.add(node.asResource().getURI())) return;
        Resource type = node.asResource();
        if (!type.equals(mobilClass) && !"NamedIndividual".equals(label(type)) && isUnderMobil(type, new HashSet<>())) result.add(label(type));
        type.listProperties(model.createProperty(RDFS_SUBCLASS)).forEachRemaining(s -> collectType(s.getObject(), result, seen));
    }

    private boolean isUnderMobil(Resource type, Set<String> visited) {
        if (type.equals(mobilClass)) return true;
        if (!type.isURIResource() || !visited.add(type.getURI())) return false;
        return type.listProperties(model.createProperty(RDFS_SUBCLASS)).toList().stream()
                .filter(s -> s.getObject().isResource())
                .map(Statement::getResource)
                .anyMatch(parent -> isUnderMobil(parent, visited));
    }

    private Optional<Resource> findClass(String localName) { return typedResources(OWL_CLASS).stream().filter(r -> localName.equals(label(r))).findFirst(); }

    private Property findProperty(String localName) {
        return StreamSupport.stream(typedResources(OWL_OBJECT_PROPERTY).spliterator(), false).map(Resource::getURI)
                .filter(uri -> localName.equals(local(uri))).findFirst().map(model::createProperty).orElseGet(() ->
                        StreamSupport.stream(typedResources(OWL_DATATYPE_PROPERTY).spliterator(), false).map(Resource::getURI)
                                .filter(uri -> localName.equals(local(uri))).findFirst().map(model::createProperty).orElse(null));
    }

    private String local(String uri) { int index = Math.max(uri.lastIndexOf('#'), uri.lastIndexOf('/')); return uri.substring(index + 1); }
    private Optional<String> firstValue(Resource resource, Property property) { return resource.listProperties(property).toList().stream().map(Statement::getObject).map(this::value).findFirst(); }
    private Set<Resource> typedResources(String typeUri) { Set<Resource> result = new LinkedHashSet<>(); baseModel.listResourcesWithProperty(baseModel.createProperty(RDF_TYPE), baseModel.createResource(typeUri)).forEachRemaining(result::add); return result; }
    private Resource required(String id) { Resource resource = resourceFor(id); if (resource == null) throw new NoSuchElementException("Mobil tidak ditemukan"); return resource; }
    private Resource resourceFor(String id) { try { String decoded = URLDecoder.decode(id, StandardCharsets.UTF_8); if (cars.containsKey(decoded)) return cars.get(decoded); return cars.get(new String(Base64.getUrlDecoder().decode(decoded), StandardCharsets.UTF_8)); } catch (IllegalArgumentException exception) { return null; } }
    private TripleData triple(Statement statement) { RDFNode object = statement.getObject(); return new TripleData(statement.getSubject().getURI(), statement.getPredicate().getURI(), value(object), object.isURIResource()); }
    private String label(Resource resource) { Statement statement = resource.getProperty(model.createProperty(RDFS_LABEL)); return statement != null && statement.getObject().isLiteral() ? statement.getString() : resource.getLocalName() == null ? resource.getURI() : resource.getLocalName(); }
    private String value(RDFNode node) { return node.isResource() ? label(node.asResource()) : node.asLiteral().getString(); }
    private String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private String blank(String value) { return value == null ? "" : value.trim(); }
    private String propertyUri(Property property) { return property == null ? "urn:missing-property" : property.getURI(); }

    public record Mobil(String id, String uri, String name, List<String> categories, String brand, String fuel, Map<String, List<String>> properties) {}
    public record TripleData(String subject, String predicate, String object, boolean objectUri) {}
}
