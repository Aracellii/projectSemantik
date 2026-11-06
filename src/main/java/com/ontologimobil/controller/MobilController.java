package com.ontologimobil.controller;

import com.ontologimobil.model.Mobil;
import com.ontologimobil.model.TripleData;
import com.ontologimobil.service.OntologyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class MobilController {
    private final OntologyService service;
    public MobilController(OntologyService service) { this.service = service; }

    @GetMapping("/mobil") public List<Mobil> all() { return service.all(); }
    @GetMapping("/mobil/{id}") public ResponseEntity<Mobil> one(@PathVariable String id) { return ResponseEntity.of(java.util.Optional.ofNullable(service.find(id))); }
    @GetMapping("/mobil/{id}/properties") public Map<String, List<String>> properties(@PathVariable String id) { return service.properties(id); }
    @GetMapping("/mobil/{id}/relations") public List<TripleData> relations(@PathVariable String id) { return service.relations(id); }
    @GetMapping("/mobil/{id}/triples") public List<TripleData> triples(@PathVariable String id) { return service.triples(id); }
    @GetMapping("/ontology/statistics") public Map<String, Object> statistics() { return service.statistics(); }
}
