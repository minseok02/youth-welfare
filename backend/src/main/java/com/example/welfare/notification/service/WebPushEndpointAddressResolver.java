package com.example.welfare.notification.service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public interface WebPushEndpointAddressResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;
}
