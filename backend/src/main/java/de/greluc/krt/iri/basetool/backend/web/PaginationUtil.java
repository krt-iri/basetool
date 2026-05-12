package de.greluc.krt.iri.basetool.backend.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.*;
import java.util.stream.Collectors;

public final class PaginationUtil {

    private PaginationUtil() {}

    public static Pageable createPageRequest(Integer pageParam,
                                             Integer sizeParam,
                                             String sortParam,
                                             Set<String> allowedSortFields,
                                             String defaultSortField) {
        int page = pageParam == null || pageParam < 0 ? 0 : pageParam;
        int size = sizeParam == null || sizeParam <= 0 ? 50 : Math.min(sizeParam, 100000);

        Sort sort = resolveSort(sortParam, allowedSortFields, defaultSortField);
        // Ensure stability: always add a secondary sort by id if not already included
        if (!containsProperty(sort, "id") && allowedSortFields.contains("id")) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(page, size, sort);
    }

    private static boolean containsProperty(Sort sort, String property) {
        for (Sort.Order order : sort) {
            if (order.getProperty().equalsIgnoreCase(property)) {
                return true;
            }
        }
        return false;
    }

    private static Sort resolveSort(String sortParam, Set<String> allowed, String defaultField) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(defaultField).ascending();
        }
        List<Sort.Order> orders = new ArrayList<>();
        // Support multiple fields separated by semicolon or repeated commas: field,asc;other,desc
        String[] parts = sortParam.split("[;]");
        for (String part : parts) {
            String[] tokens = part.split(",");
            String field = tokens[0].trim();
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unsupported sort field: " + field);
            }
            Sort.Direction dir = Sort.Direction.ASC;
            if (tokens.length > 1) {
                String d = tokens[1].trim();
                if (d.equalsIgnoreCase("desc")) dir = Sort.Direction.DESC;
            }
            orders.add(new Sort.Order(dir, field));
        }
        if (orders.isEmpty()) {
            return Sort.by(defaultField).ascending();
        }
        return Sort.by(orders);
    }

    public static List<String> toSortStrings(Sort sort) {
        return sort.stream()
                .map(o -> o.getProperty() + "," + o.getDirection().name().toLowerCase())
                .collect(Collectors.toList());
    }
}
