package com.ontologimobil.model;

import java.util.List;
import java.util.Map;

public record Mobil(String id, String uri, String name, List<String> categories,
                    String brand, String fuel, Map<String, List<String>> properties) {}
