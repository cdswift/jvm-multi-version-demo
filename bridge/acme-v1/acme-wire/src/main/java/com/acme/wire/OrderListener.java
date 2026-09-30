package com.acme.wire;

import com.acme.model.Order;

/** Called on the receiver's connection thread for every order that arrives. */
@FunctionalInterface
public interface OrderListener {
    void onOrder(Order order, byte[] payload);
}
