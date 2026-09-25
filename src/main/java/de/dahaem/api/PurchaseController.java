package de.dahaem.api;

import de.dahaem.finance.PurchaseService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class PurchaseController {

    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @GetMapping("/api/purchases")
    public List<PurchaseDto> all() {
        return purchaseService.allPurchases().stream().map(PurchaseDto::from).toList();
    }

    /**
     * Records a purchase of a known store_item and applies it to inventory (one transaction, in the service).
     * An unknown storeItemId/discountId violates a foreign key and comes back as 409.
     */
    @PostMapping("/api/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseDto create(@RequestBody CreatePurchaseRequest request) {
        if (request.storeItemId() == null) {
            throw new BadRequestException("'storeItemId' is required");
        }
        if (request.quantityPurchased() == null || request.quantityPurchased() <= 0) {
            throw new BadRequestException("'quantityPurchased' is required and must be greater than 0");
        }
        if (request.pricePaid() == null || request.pricePaid() < 0 || request.pricePaid().isNaN()
                || request.pricePaid().isInfinite()) {
            throw new BadRequestException("'pricePaid' is required and must be 0 or greater");
        }
        if (request.expirationDate() == null) {
            throw new BadRequestException("'expirationDate' is required (YYYY-MM-DD)");
        }
        boolean wasDiscounted = Boolean.TRUE.equals(request.wasDiscounted());
        LocalDate purchaseDate = request.purchaseDate() != null ? request.purchaseDate() : LocalDate.now();

        int id = purchaseService.recordPurchase(request.storeItemId(), request.discountId(), wasDiscounted,
                purchaseDate, request.quantityPurchased(), request.pricePaid(), request.expirationDate());
        return new PurchaseDto(id, request.storeItemId(), request.discountId(), wasDiscounted, purchaseDate,
                request.quantityPurchased(), request.pricePaid(), request.expirationDate());
    }

    /** Total spending: over all purchases, or between {@code from} and {@code to} (inclusive) when both are given. */
    @GetMapping("/api/analytics/spending")
    public SpendingDto spending(
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        if (from == null && to == null) {
            return new SpendingDto(null, null, purchaseService.spendingTotal());
        }
        if (from == null || to == null) {
            throw new BadRequestException("'from' and 'to' must be given together (YYYY-MM-DD)");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' must not be after 'to'");
        }
        return new SpendingDto(from, to, purchaseService.spendingBetween(from, to));
    }
}
