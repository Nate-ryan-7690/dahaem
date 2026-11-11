package de.dahaem.api;

import de.dahaem.shared.PageRequest;
import de.dahaem.stores.BrandPage;
import de.dahaem.stores.StoreCatalogService;
import de.dahaem.stores.StoreRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The stores and brands DAHÄM has, for pickers (so nobody types a store or brand name that does not exist). */
@RestController
public class StoreCatalogController {

    private final StoreCatalogService service;

    public StoreCatalogController(StoreCatalogService service) {
        this.service = service;
    }

    /** Every store, by name: {@code [{id, name}]}. */
    @GetMapping("/api/stores")
    public List<StoreRef> stores() {
        return service.stores();
    }

    /**
     * Brands, by name, one page: {@code q} matches the name as a case-insensitive substring (optional); {@code page}
     * (from 1, default 1) and {@code pageSize} (1 - 100, default 24) like {@code GET /api/recipes/search}.
     */
    @GetMapping("/api/brands")
    public BrandPageDto brands(@RequestParam(name = "q", required = false) String q,
                               @RequestParam(name = "page", required = false) Integer page,
                               @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        PageRequest request = PageRequest.of(page, pageSize);
        BrandPage result = service.brands(q, request.offset(), request.pageSize());
        return new BrandPageDto(result.items(), result.total(), request.page(), request.pageSize(),
                request.totalPages(result.total()));
    }
}
