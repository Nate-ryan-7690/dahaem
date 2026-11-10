package de.dahaem.api;

import de.dahaem.finance.StockService;
import de.dahaem.inventory.InventoryService;
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
    public ItemDto use(@PathVariable int id, @RequestBody UseStockRequest request) {
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new BadRequestException("'quantity' must be greater than 0");
        }
        return stockService.use(id, request.quantity()).map(ItemDto::from)
                .orElseThrow(() -> new NotFoundException("No inventory item with id " + id));
    }

    @GetMapping
    public List<ItemDto> all() {
        return inventoryService.allItems().stream().map(ItemDto::from).toList();
    }

    /** Items expiring within the next {@code days} days (default 7). */
    @GetMapping("/expiring")
    public List<ItemDto> expiring(@RequestParam(name = "days", defaultValue = "7") int days) {
        if (days < 0) {
            throw new BadRequestException("'days' must be 0 or greater");
        }
        return inventoryService.expiringWithin(days).stream().map(ItemDto::from).toList();
    }

    /** Items at or below {@code threshold} units in stock (default 1). */
    @GetMapping("/low-stock")
    public List<ItemDto> lowStock(@RequestParam(name = "threshold", defaultValue = "1") int threshold) {
        if (threshold < 0) {
            throw new BadRequestException("'threshold' must be 0 or greater");
        }
        return inventoryService.lowStock(threshold).stream().map(ItemDto::from).toList();
    }
}
