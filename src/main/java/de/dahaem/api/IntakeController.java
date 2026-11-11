package de.dahaem.api;

import de.dahaem.barcode.IntakeService;
import de.dahaem.mealplanner.MealPlanService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Putting groceries away by barcode number (typed, or from a list): stock, batches by expiry date, store items. */
@RestController
@RequestMapping("/api/intake")
public class IntakeController {

    static final int MAX_LINES = 200;

    private final IntakeService intakeService;
    private final MealPlanService mealPlanService;

    public IntakeController(IntakeService intakeService, MealPlanService mealPlanService) {
        this.intakeService = intakeService;
        this.mealPlanService = mealPlanService;
    }

    /**
     * Body: the store (an existing one, from {@code GET /api/stores}; {@code createStore: true} is needed to add a new
     * one, so a typo cannot create a bogus store), the purchase day (default today, not in the future) and the lines.
     * {@code requestId} (optional, 8 to 64 letters, digits, - or _) makes a repeat safe: see {@link #intake}.
     */
    public record IntakeRequest(String storeName, Boolean createStore, LocalDate purchaseDate,
                                List<IntakeService.Line> lines, String requestId) {
    }

    /**
     * {@code results} has one entry per line, in order; {@code recorded} / {@code failed} count them. {@code replayed} is
     * true when this is the stored answer to a {@code requestId} seen before: nothing was recorded again.
     */
    public record IntakeResult(List<IntakeService.LineResult> results, int recorded, int failed, boolean replayed) {
    }

    /**
     * Puts the lines away. Each line: {@code barcode}, {@code expirationDate} (required), {@code packs} (default 1),
     * {@code pricePaid} (total for the packs; optional if the store item's price is known), and optionally
     * {@code packSize} + {@code packUnit} for a special package. The barcode must have been assigned
     * ({@code PUT /api/barcodes/{code}}). Stock grows by packs x pack size in the item's own unit and the expiry date is
     * kept as a batch (the earliest is used first). An unknown store is a 400 with code UNKNOWN_STORE unless {@code createStore} is true.
     * With a {@code requestId}, sending the same request again (same body) returns the stored answer with {@code replayed}
     * true and records nothing twice; another body with the same id is 409 REQUEST_ID_REUSED, a request still being
     * processed 409 REQUEST_IN_PROGRESS. Always answers 200 when the request itself is fine: a line that could
     * not be recorded has {@code ok: false} with a {@code code} (INVALID_BARCODE, BARCODE_CHECKSUM, BARCODE_UNKNOWN,
     * EXPIRY_REQUIRED, PRICE_REQUIRED, PACK_UNIT_MISMATCH, BAD_REQUEST) and the other lines are still recorded.
     */
    @PostMapping
    public IntakeResult intake(@RequestBody IntakeRequest request) {
        if (request.storeName() == null || request.storeName().isBlank() || request.storeName().length() > 100) {
            throw new BadRequestException("'storeName' is required (at most 100 characters)");
        }
        if (request.lines() == null || request.lines().isEmpty() || request.lines().size() > MAX_LINES) {
            throw new BadRequestException("'lines' must have between 1 and " + MAX_LINES + " entries");
        }
        LocalDate today = mealPlanService.today();   // the household's day, not the server's
        LocalDate purchaseDate = request.purchaseDate() != null ? request.purchaseDate() : today;
        if (purchaseDate.isAfter(today)) {
            throw new BadRequestException("'purchaseDate' must not be in the future");
        }
        String requestId = request.requestId() == null || request.requestId().isBlank() ? null : request.requestId().trim();
        if (requestId != null && !requestId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new BadRequestException("'requestId' must be 8 to 64 letters, digits, - or _");
        }
        IntakeService.Outcome outcome = intakeService.intakeOnce(requestId, request.toString(),
                request.storeName(), Boolean.TRUE.equals(request.createStore()), purchaseDate, request.lines());
        List<IntakeService.LineResult> results = outcome.results();
        int recorded = (int) results.stream().filter(IntakeService.LineResult::ok).count();
        return new IntakeResult(results, recorded, results.size() - recorded, outcome.replayed());
    }
}
