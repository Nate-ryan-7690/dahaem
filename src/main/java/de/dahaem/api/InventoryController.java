package de.dahaem.api;

import de.dahaem.finance.StockBatch;
import de.dahaem.finance.StockService;
import de.dahaem.inventory.InventoryService;
import de.dahaem.inventory.ItemPage;
import de.dahaem.shared.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final StockService stockService;

    public InventoryController(InventoryService inventoryService, StockService stockService) {
        this.inventoryService = inventoryService;
        this.stockService = stockService;
    }

    /**
     * Takes {@code quantity} out of stock, from the batch that expires first (used or thrown away: the same). Returns the
     * item as it is now, with its earliest expiry date worked out again.
     */
    @PostMapping("/{id}/use")
    public ItemDto use(@PathVariable("id") int id, @RequestBody UseStockRequest request, Caller caller) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("'quantity' must be greater than 0");
        }
        return stockService.use(caller.householdId(), id, request.quantity()).map(ItemDto::from)
                .orElseThrow(() -> new NotFoundException("No inventory item with id " + id));
    }

    /**
     * The batches (purchases) of an item in the order they are used, earliest expiry first: store, brand, purchase date,
     * expiry date, packs and pack size bought, and what is left in the item's own unit ({@code remaining}, {@code unit}).
     * Used-up batches are left out; {@code ?all=true} includes them. 404 if there is no such item.
     */
    @GetMapping("/{id}/batches")
    public List<StockBatch> batches(@PathVariable("id") int id,
                                    @RequestParam(name = "all", defaultValue = "false") boolean all, Caller caller) {
        return stockService.batches(caller.householdId(), id, all)
                .orElseThrow(() -> new NotFoundException("No inventory item with id " + id));
    }

    @GetMapping
    public List<ItemDto> all(Caller caller) {
        return inventoryService.allItems(caller.householdId()).stream().map(ItemDto::from).toList();
    }

    /**
     * Search the catalogue, one page of items plus the total. {@code q} matches the English or German name as a
     * case-insensitive substring, {@code category} must equal the item's category (both optional); {@code page} (from 1,
     * default 1) and {@code pageSize} (1 - 100, default 24) like {@code GET /api/recipes/search}. Out-of-range paging is a 400.
     */
    @GetMapping("/search")
    public ItemPageDto search(@RequestParam(name = "q", required = false) String q,
                              @RequestParam(name = "category", required = false) String category,
                              @RequestParam(name = "page", required = false) Integer page,
                              @RequestParam(name = "pageSize", required = false) Integer pageSize,
                              Caller caller) {
        PageRequest request = PageRequest.of(page, pageSize);
        ItemPage result = inventoryService.search(caller.householdId(), q, category, request.offset(), request.pageSize());
        return new ItemPageDto(result.items().stream().map(ItemDto::from).toList(), result.total(), request.page(),
                request.pageSize(), request.totalPages(result.total()));
    }

    /** Items expiring within the next {@code days} days (default 7). */
    @GetMapping("/expiring")
    public List<ItemDto> expiring(@RequestParam(name = "days", defaultValue = "7") int days, Caller caller) {
        if (days < 0) {
            throw new BadRequestException("'days' must be 0 or greater");
        }
        return inventoryService.expiringWithin(caller.householdId(), days).stream().map(ItemDto::from).toList();
    }

    /** Items at or below {@code threshold} units in stock (default 1). */
    @GetMapping("/low-stock")
    public List<ItemDto> lowStock(@RequestParam(name = "threshold", defaultValue = "1") int threshold, Caller caller) {
        if (threshold < 0) {
            throw new BadRequestException("'threshold' must be 0 or greater");
        }
        return inventoryService.lowStock(caller.householdId(), threshold).stream().map(ItemDto::from).toList();
    }
}
