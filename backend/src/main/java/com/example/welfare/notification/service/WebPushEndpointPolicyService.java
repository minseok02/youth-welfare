package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class WebPushEndpointPolicyService {

    private final WebPushEndpointAddressResolver addressResolver;
    private final String allowedEndpointHostSuffixes;

    public WebPushEndpointPolicyService(
            WebPushEndpointAddressResolver addressResolver,
            @Value("${notification.web-push.allowed-endpoint-host-suffixes:fcm.googleapis.com,updates.push.services.mozilla.com,push.apple.com,notify.windows.com}")
            String allowedEndpointHostSuffixes
    ) {
        this.addressResolver = addressResolver;
        this.allowedEndpointHostSuffixes = allowedEndpointHostSuffixes;
    }

    public void validateSubscriptionEndpoint(String rawEndpoint) {
        parseAndValidate(rawEndpoint);
    }

    public boolean isAllowedSubscriptionEndpoint(String rawEndpoint) {
        try {
            parseAndValidate(rawEndpoint);
            return true;
        } catch (CustomException e) {
            return false;
        }
    }

    public String describeEndpointForLog(String rawEndpoint) {
        try {
            URI endpoint = new URI(rawEndpoint);
            String host = endpoint.getHost();
            if (StringUtils.hasText(host)) {
                return host;
            }
        } catch (URISyntaxException ignored) {
            // fall through
        }
        return "invalid-endpoint";
    }

    private void parseAndValidate(String rawEndpoint) {
        String normalized = StringUtils.trimWhitespace(rawEndpoint);
        if (!StringUtils.hasText(normalized)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        URI endpoint;
        try {
            endpoint = new URI(normalized);
        } catch (URISyntaxException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String host = endpoint.getHost();
        if (!StringUtils.hasText(host) || endpoint.getPort() == 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (!isAllowedHost(host)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        try {
            List<InetAddress> resolvedAddresses = addressResolver.resolve(host);
            if (resolvedAddresses.isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            for (InetAddress address : resolvedAddresses) {
                if (isBlockedAddress(address)) {
                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }
            }
        } catch (UnknownHostException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean isAllowedHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return allowedHostSuffixes().stream()
                .anyMatch(suffix -> normalizedHost.equals(suffix) || normalizedHost.endsWith("." + suffix));
    }

    private List<String> allowedHostSuffixes() {
        return Arrays.stream(allowedEndpointHostSuffixes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet4Address ipv4) {
            byte[] octets = ipv4.getAddress();
            int first = octets[0] & 0xff;
            int second = octets[1] & 0xff;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || (first == 100 && second >= 64 && second <= 127);
        }

        if (address instanceof Inet6Address ipv6) {
            byte[] octets = ipv6.getAddress();
            int first = octets[0] & 0xff;
            int second = octets[1] & 0xff;
            return first == 0xfc
                    || first == 0xfd
                    || (first == 0xfe && (second & 0xc0) == 0x80);
        }

        return false;
    }
}
