package de.dahaem.api;

import de.dahaem.auth.AccountService;
import de.dahaem.auth.HouseholdMembership;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Fills a controller parameter of type {@link Caller}: the acting user from {@code X-Acting-User} (believed only with the
 * internal token, see {@link ActingUser}) and that user's household. 400 if the header is missing, 401 for a user that
 * does not exist, 403 NO_HOUSEHOLD for a user in no household.
 */
@Component
public class CallerArgumentResolver implements HandlerMethodArgumentResolver {

    private final InternalTokenFilter internalToken;
    private final AccountService accounts;

    public CallerArgumentResolver(InternalTokenFilter internalToken, AccountService accounts) {
        this.internalToken = internalToken;
        this.accounts = accounts;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Caller.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        int userId = ActingUser.require(request, internalToken);
        accounts.findById(userId).orElseThrow(() -> new UnauthorizedException("Unknown user"));
        HouseholdMembership membership = accounts.membershipFor(userId).orElseThrow(NoHouseholdException::new);
        return new Caller(userId, membership.household().getId(), membership.role());
    }
}
