# AGENTS.md — Semantic Web Ontologi Mobil

## 1. Project Overview

You are a senior full-stack developer specializing in Java, Spring Boot, Apache Jena, RDF, OWL, SPARQL, and modern frontend development.

Your task is to develop a complete Semantic Web application for exploring car ontology data created using Protégé.

The application must provide an intuitive, modern, responsive web interface inspired by the provided reference screenshot.

**Primary objective:** Build a functional ontology explorer where users can search, filter, browse, and inspect car individuals and their semantic relationships.

The application must use actual ontology data rather than hardcoded frontend data.

## 2. Technology Stack

### Backend
- Java 21
- Spring Boot 3.x
- Apache Jena 5.x
- Maven
- REST API
- SPARQL query engine
- RDF/OWL ontology processing

### Frontend
- HTML5
- CSS3
- Vanilla JavaScript
- Fetch API
- Responsive layout
- No frontend framework required

### Data
- Protégé OWL ontology
- RDF/XML
- SPARQL queries
- No relational database required

## 3. Project Structure

```text
ontologi-mobil/
├── AGENTS.md
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/ontologimobil/
    │   │   ├── Application.java
    │   │   ├── controller/
    │   │   │   └── MobilController.java
    │   │   ├── service/
    │   │   │   └── OntologyService.java
    │   │   └── model/
    │   │       └── MobilDTO.java
    │   └── resources/
    │       ├── mobil.owl
    │       ├── application.properties
    │       └── static/
    │           ├── index.html
    │           ├── css/style.css
    │           ├── js/app.js
    │           └── images/
    └── test/
        └── java/
```

Maintain separation of concerns between controllers, ontology services, and frontend presentation.

## 4. UI/UX Requirements

Build a modern semantic web dashboard closely resembling the provided reference.

### Header

- Dark terracotta or reddish-brown background.
- Application title: "Semantic Web Ontologi Mobil".
- Subtitle: "Eksplorasi Data Mobil Berbasis Ontologi".
- Display the total number of car individuals dynamically.

### Search and Filters

Create a horizontal filter section containing:

1. Search input for car names.
2. Vehicle category dropdown.
3. Manufacturer/brand dropdown.
4. Fuel type dropdown.
5. Reset filters button.

Filtering must work with actual ontology values.

### Left Sidebar

Display a scrollable list of cars.

Each entry should show:
- Car name.
- Manufacturer or brand, when available.
- Vehicle category, when available.
- Selected state.

Clicking a car must update the detail panel without refreshing the page.

### Main Detail Panel

Display the selected car's information.

Include:
- Large hero section.
- Car name.
- Manufacturer.
- Vehicle category.
- Description when available.
- Optional vehicle image.

If no image is available, display a professional placeholder rather than an unrelated or fabricated vehicle photo.

### Detail Tabs

Implement the following tabs:

**Ringkasan**
- Vehicle category.
- Brand.
- Passenger capacity.
- Fuel type.
- Chassis.
- Braking system.
- Transmission.
- Production year.

Only show values that actually exist in the ontology.

**Spesifikasi**
- Display all available datatype properties.
- Use readable Indonesian labels.
- Support numeric and textual values.

**Komponen**
- Display components connected through object properties.
- Show component names and their relationships.

**Relasi Ontologi**
- Show object property relationships.
- Display subject, predicate, and object.
- Allow users to inspect related resources.

**Triple RDF**
- Display actual RDF triples from Apache Jena.
- Show subject, predicate, and object.
- Distinguish URI resources from literal values.

## 5. Backend Requirements

Implement a REST API using Spring Boot.

Required endpoints:

```http
GET /api/mobil
GET /api/mobil/{id}
GET /api/mobil/{id}/properties
GET /api/mobil/{id}/relations
GET /api/mobil/{id}/triples
GET /api/ontology/statistics
```

Use URL-safe identifiers or encoded resource URIs.

Never assume local names are globally unique.

### Apache Jena Integration

- Load the OWL file from classpath resources.
- Use Apache Jena for RDF processing.
- Use SPARQL for querying ontology individuals.
- Detect namespaces from the ontology.
- Do not hardcode the namespace.
- Handle direct and indirect subclass relationships.
- Do not assume all individuals are directly typed as `Mobil`.
- Support datatype and object properties.
- Handle missing property values gracefully.
- Use parameterized SPARQL queries where appropriate.
- Close query execution resources correctly.

### Ontology Compatibility

The ontology may contain:

- Mobil
- MobilPenumpang
- MobilBarang
- Merk
- Jenis
- Komponen
- Mesin
- JenisBahanBakar

These names are examples, not guaranteed schema definitions.

Inspect the actual OWL file before implementing queries.

Do not invent ontology classes, properties, or individuals.

### Reasoning

Start with explicit RDF triples and RDFS subclass traversal.

If OWL reasoning is required, explain its limitations and introduce a compatible reasoner separately.

Do not claim that ordinary SPARQL queries perform complete OWL DL reasoning.

## 6. Frontend Behavior

- Fetch data from the Spring Boot API.
- Never hardcode vehicle records in JavaScript.
- Implement real-time search.
- Combine search and dropdown filters.
- Update the selected vehicle dynamically.
- Support loading, empty, and error states.
- Use safe DOM APIs such as `textContent` for ontology-derived values.
- Avoid injecting untrusted ontology values using `innerHTML`.
- Ensure mobile and desktop responsiveness.

## 7. Visual Design

Follow the reference screenshot's layout and visual hierarchy.

Design direction:
- Warm terracotta header.
- Cream and off-white backgrounds.
- White cards.
- Soft shadows.
- Rounded corners.
- Serif headings with readable sans-serif body text.
- Compact left sidebar.
- Spacious right detail panel.
- Subtle hover effects.
- Clear active tab indicator.

The interface must look like a polished university Semantic Web project rather than a generic admin dashboard.

## 8. Development Rules

1. Inspect existing files before modifying them.
2. Preserve working functionality.
3. Do not rewrite unrelated code.
4. Do not fabricate ontology data.
5. Do not hardcode ontology namespaces.
6. Avoid unnecessary dependencies.
7. Keep code readable and maintainable.
8. Use Indonesian for UI labels.
9. Handle errors gracefully.
10. Ensure the application works on Fedora Linux.
11. Do not modify the original OWL ontology unless explicitly instructed.
12. Never claim a test passed unless it was executed successfully.

## 9. Testing Requirements

Verify:

- Maven builds successfully.
- Spring Boot starts.
- OWL file loads correctly.
- API returns valid JSON.
- Subclass individuals are discovered.
- Search works.
- Filters work.
- Selecting a vehicle updates the details.
- Tabs show appropriate ontology information.
- RDF triples match the underlying model.
- Empty and missing values are handled correctly.

Use:

```bash
mvn clean test
mvn spring-boot:run
```

## 10. Execution Workflow

Follow this sequence:

1. Inspect the project structure and OWL file.
2. Identify namespaces, classes, properties, and individuals.
3. Implement or repair the Apache Jena ontology service.
4. Implement the REST API.
5. Build the frontend layout.
6. Connect frontend components to real API data.
7. Implement search and filtering.
8. Implement the detail tabs.
9. Test backend and frontend.
10. Update README with setup and execution instructions.

Do not stop after producing a plan. Implement the requested functionality.

## 11. Definition of Done

The task is complete when:

- The application runs locally.
- The frontend resembles the provided reference.
- All vehicle records originate from the OWL ontology.
- Users can browse and inspect vehicles.
- Filters and search work.
- Detail tabs display actual semantic data.
- RDF triples are accessible.
- The application builds without errors.
- README explains how to run the project.

When finished, summarize the implemented changes, executed tests, and any remaining limitations.
