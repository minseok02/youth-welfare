package com.example.welfare.notification.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class WebPushEndpointAddressResolverImpl implements WebPushEndpointAddressResolver {

    @Override
    public List<InetAddress> resolve(String host) throws UnknownHostException {
        return List.of(InetAddress.getAllByName(host));
    }
}
