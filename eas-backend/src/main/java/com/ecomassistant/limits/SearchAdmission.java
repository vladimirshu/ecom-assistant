package com.ecomassistant.limits;

import org.springframework.stereotype.Component;

/**
 * Admits a validated search: the per-client limits are checked first, then the daily
 * budget. When the daily budget rejects a search the client token is returned, so a
 * request rejected by the budget does not eat the client's quota. A rejected search
 * throws {@link SearchRejectedException} before any LLM, embedding or vector call.
 */
@Component
public class SearchAdmission {

    private final ClientRateLimiter clientRateLimiter;
    private final DailySearchBudget dailySearchBudget;

    public SearchAdmission(ClientRateLimiter clientRateLimiter, DailySearchBudget dailySearchBudget) {
        this.clientRateLimiter = clientRateLimiter;
        this.dailySearchBudget = dailySearchBudget;
    }

    public void admit(String clientKey) {
        ClientRateLimiter.Result clientResult = clientRateLimiter.tryAcquire(clientKey);
        if (!clientResult.allowed()) {
            throw new SearchRejectedException(
                    SearchRejectedException.CODE_RATE_LIMITED, clientResult.retryAfterSeconds());
        }

        DailySearchBudget.Result budgetResult = dailySearchBudget.tryAcquire();
        if (!budgetResult.allowed()) {
            clientRateLimiter.release(clientKey);
            throw new SearchRejectedException(
                    SearchRejectedException.CODE_DAILY_BUDGET_EXHAUSTED, budgetResult.retryAfterSeconds());
        }
    }
}
