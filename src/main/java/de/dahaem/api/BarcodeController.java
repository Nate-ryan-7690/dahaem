package de.dahaem.api;

import de.dahaem.barcode.BarcodeLookup;
import de.dahaem.barcode.BarcodeService;
import de.dahaem.mealplanner.MealPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Barcode numbers: what a number is, and remembering it. Numbers can be typed; scanning in the app comes later. */
@RestController
@RequestMapping("/api/barcodes")
public class BarcodeController {

    private final BarcodeService barcodeService;
    private final MealPlanService mealPlanService;

    public BarcodeController(BarcodeService barcodeService, MealPlanService mealPlanService) {
        this.barcodeService = barcodeService;
        this.mealPlanService = mealPlanService;
    }

    /**
     * What a barcode is. The number may be typed with spaces or hyphens; 8, 12, 13 or 14 digits with a fitting check
     * digit (a 12-digit UPC-A is returned as its 13-digit form). {@code status}: KNOWN (see {@code product}: the
     * catalogue item, brand, pack and the store items that carry it), FOUND (not known, an open data source describes it:
     * see {@code suggestion}, nothing is saved), NOT_FOUND, DISABLED (outside lookup switched off) or UNAVAILABLE
     * (outside lookup failed). A wrong length or non-digits is a 400 with code INVALID_BARCODE, a check digit that does
     * not fit (a typing error) a 400 with code BARCODE_CHECKSUM.
     */
    @GetMapping("/{code}")
    public BarcodeLookup lookup(@PathVariable("code") String code) {
        return barcodeService.lookup(code);
    }

    /**
     * Remembers what the barcode is, or replaces it ({@link AssignBarcodeRequest}). The user chooses the catalogue item;
     * nothing is guessed. Returns the barcode as it is now (status KNOWN). 400 for an unknown item or store item, a store
     * item of another catalogue item, a bad pack, or a pack that cannot be stocked in the item's unit (400
     * PACK_UNIT_MISMATCH: an item counted in g takes a pack in g or kg, not in ml).
     */
    @PutMapping("/{code}")
    public BarcodeLookup assign(@PathVariable("code") String code, @RequestBody AssignBarcodeRequest request) {
        if (request.inventoryItemId() == null || request.packSize() == null) {
            throw new BadRequestException("'inventoryItemId' and 'packSize' are required");
        }
        return barcodeService.assign(code, request.inventoryItemId(), request.brand(), request.packSize(),
                request.packUnit(), request.name(), request.storeItemId(), Boolean.TRUE.equals(request.createBrand()),
                mealPlanService.today());
    }
}
