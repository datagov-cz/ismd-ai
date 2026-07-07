package cz.dia.ismd.assistant.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpExchangeLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpExchangeLoggingFilter.class);
    private static final int MAX_BODY_LENGTH = 10_000;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long durationMillis = System.currentTimeMillis() - startedAt;
            log.info(
                    "http_exchange request_id={} method={} path={} status={} duration_ms={} remote_addr={}",
                    requestId,
                    request.getMethod(),
                    requestPath(request),
                    wrappedResponse.getStatus(),
                    durationMillis,
                    request.getRemoteAddr()
            );
            log.debug(
                    "http_exchange_body request_id={} request_body={} response_body={}",
                    requestId,
                    payload(wrappedRequest.getContentAsByteArray(), request.getContentType(), request.getCharacterEncoding()),
                    payload(wrappedResponse.getContentAsByteArray(), response.getContentType(), response.getCharacterEncoding())
            );
            wrappedResponse.copyBodyToResponse();
        }
    }

    private static String requestPath(HttpServletRequest request) {
        if (request.getQueryString() == null || request.getQueryString().isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + request.getQueryString();
    }

    private static String payload(byte[] content, String contentType, String characterEncoding) {
        if (content.length == 0) {
            return "\"\"";
        }
        if (!isVisible(contentType)) {
            return "\"<" + content.length + " bytes>";
        }

        String body = new String(content, charset(characterEncoding));
        body = body.replaceAll("\\s+", " ").trim();
        if (body.length() > MAX_BODY_LENGTH) {
            body = body.substring(0, MAX_BODY_LENGTH) + "...<truncated>";
        }
        return "\"" + body.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean isVisible(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            String subtype = mediaType.getSubtype();
            return MediaType.APPLICATION_JSON.includes(mediaType)
                    || MediaType.APPLICATION_XML.includes(mediaType)
                    || MediaType.TEXT_PLAIN.includes(mediaType)
                    || MediaType.APPLICATION_FORM_URLENCODED.includes(mediaType)
                    || subtype.endsWith("+json")
                    || subtype.endsWith("+xml");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Charset charset(String characterEncoding) {
        if (characterEncoding == null || characterEncoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(characterEncoding);
        } catch (IllegalArgumentException exception) {
            return StandardCharsets.UTF_8;
        }
    }
}
