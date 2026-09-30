package com.example.bridge;

import com.example.bridge.mapping.OrderV1ToV2;
import com.example.bridge.mapping.OrderV2ToV1;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// The 1.0 model is imported; everything from 2.0 is written fully qualified.
import v1.com.acme.model.Customer;
import v1.com.acme.model.Item;
import v1.com.acme.model.Order;
import v1.com.acme.model.Status;

/**
 * Runs a complete 1.0 -> 2.0 pipeline inside one JVM:
 *
 * <pre>
 *   upstream (v1 sender) --TCP, ACME 1.0--> OrderBridge --TCP, ACME 2.0--> downstream (v2 receiver)
 * </pre>
 *
 * Both ACME bundles are on the classpath at once, relocated to v1.com.acme.*
 * and v2.com.acme.* by the shade wrappers. Results are printed as numbered
 * blocks: {@code [section.step] BridgeDemo.java:line}, the code, and its result.
 */
public class BridgeDemo {

    /** One order as the downstream 2.0 system received it. */
    record Received(v2.com.acme.model.Order order, byte[] payload) {
    }

    public static void main(String[] args) throws Exception {
        bothVersionsLoaded();

        BlockingQueue<OrderBridge.Hop> hops = new LinkedBlockingQueue<>();
        BlockingQueue<Received> downstreamInbox = new LinkedBlockingQueue<>();

        section("Start the pipeline: downstream (2.0), bridge, upstream (1.0)");
        try (var downstream = show("var downstream = v2.com.acme.wire.OrderReceiver.listen(0, ...)",
                     () -> v2.com.acme.wire.OrderReceiver.listen(0,
                             (order, payload) -> downstreamInbox.add(new Received(order, payload))));
             var bridge = show("var bridge = OrderBridge.start(\"localhost\", downstream.port(), hops::add)",
                     () -> OrderBridge.start("localhost", downstream.port(), hops::add));
             var upstream = show("var upstream = v1.com.acme.wire.OrderSender.connect(\"localhost\", bridge.port())",
                     () -> v1.com.acme.wire.OrderSender.connect("localhost", bridge.port()))) {

            show("downstream.port()", downstream::port);
            show("bridge.port()", bridge::port);

            section("Send 1.0 orders through the bridge");
            List<Order> orders = List.of(
                    order("ORD-1001", 129900, Status.NEW, "Ada Lovelace", "ada@example.com",
                            new Item("KB-01", 1), new Item("MS-02", 2)),
                    order("ORD-1002", 4999, Status.SENT, "Alan Turing", "alan@example.com",
                            new Item("CBL-7", 3)),
                    order("ORD-1003", 5, Status.NEW, "Grace Hopper", "grace@example.com"));

            List<v2.com.acme.model.Order> received = new ArrayList<>();
            for (Order order : orders) {
                show("upstream.send(" + order.getId() + ")", () -> upstream.send(order));
                received.add(trace(hops.poll(5, TimeUnit.SECONDS), downstreamInbox.poll(5, TimeUnit.SECONDS)));
            }

            translateBack(received.get(1));
        }
    }

    /** Both bundles, same class names, one ClassLoader. */
    static void bothVersionsLoaded() {
        section("Both ACME bundles in one JVM");
        show("v1.com.acme.wire.WireProtocol.version()", v1.com.acme.wire.WireProtocol::version);
        show("v2.com.acme.wire.WireProtocol.version()", v2.com.acme.wire.WireProtocol::version);

        // Found through ServiceLoader. Works only because the shade wrappers'
        // ServicesResourceTransformer rewrote META-INF/services for the new names.
        show("v1.com.acme.wire.Codecs.load()", v1.com.acme.wire.Codecs::load);
        show("v2.com.acme.wire.Codecs.load()", v2.com.acme.wire.Codecs::load);

        show("Order.class  // imported: v1", () -> Order.class);
        show("v2.com.acme.model.Order.class", () -> v2.com.acme.model.Order.class);
        show("Order.class.getClassLoader() == v2.com.acme.model.Order.class.getClassLoader()",
                () -> Order.class.getClassLoader() == v2.com.acme.model.Order.class.getClassLoader());

        // The mapper implementation MapStruct generated at compile time:
        show("OrderV1ToV2.INSTANCE.getClass()", () -> OrderV1ToV2.INSTANCE.getClass());
    }

    /** Prints what happened to one message at each hop. */
    static v2.com.acme.model.Order trace(OrderBridge.Hop hop, Received received) {
        if (hop == null) {
            System.out.println("    !! bridge reported nothing within 5s");
            return null;
        }
        System.out.println("    1 bridge rx     (v1 wire):   " + describe(hop.v1Payload()));
        System.out.println("    2 bridge decode (v1 codec):  " + format(hop.v1Order()));
        System.out.println("    3 bridge map    (MapStruct): " + format(hop.v2Order()));
        if (hop.error() != null) {
            System.out.println("    !! bridge failed: " + hop.error());
            return null;
        }
        System.out.println("    4 bridge tx     (v2 wire):   " + describe(hop.v2Payload()));
        if (received == null) {
            System.out.println("    !! downstream received nothing within 5s");
            return null;
        }
        System.out.println("    5 downstream    (v2 codec):  " + format(received.order()));
        return received.order();
    }

    /** The reverse mapper: lossy, and the losses are declared in OrderV2ToV1. */
    static void translateBack(v2.com.acme.model.Order fromDownstream) {
        section("Translating back: 2.0 -> 1.0");
        show("fromDownstream  // ORD-1002 as the 2.0 system received it", () -> fromDownstream);
        show("OrderV2ToV1.INSTANCE.toV1(fromDownstream)  // SHIPPED -> SENT, channel dropped",
                () -> OrderV2ToV1.INSTANCE.toV1(fromDownstream));

        v2.com.acme.model.Order cancelled = new v2.com.acme.model.Order();
        cancelled.setId("ORD-2001");
        cancelled.setAmount(new BigDecimal("15.00"));
        cancelled.setStatus(v2.com.acme.model.Status.CANCELLED);
        cancelled.setCustomer(new v2.com.acme.model.Customer("Katherine Johnson", "kj@example.com"));
        cancelled.setChannel("WEB");
        show("OrderV2ToV1.INSTANCE.toV1(cancelled)  // 1.0 has no CANCELLED",
                () -> OrderV2ToV1.INSTANCE.toV1(cancelled));
    }

    static Order order(String id, long cents, Status status, String name, String email, Item... items) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        Order order = new Order();
        order.setId(id);
        order.setAmountCents(cents);
        order.setStatus(status);
        order.setCustomer(customer);
        order.setItems(List.of(items));
        return order;
    }

    // ---------------------------------------------------------------------

    /** Like Supplier, but may throw, so failures can be shown too. */
    @FunctionalInterface
    interface Code<T> {
        T run() throws Exception;
    }

    static int sectionNo = 0;
    static int stepNo = 0;

    /**
     * Prints a numbered block with the calling line and {@code source}, then
     * runs {@code code} and prints its result or the exception it threw.
     * Returns the result, or null if it threw.
     */
    static <T> T show(String source, Code<T> code) {
        String where = StackWalker.getInstance()
                .walk(frames -> frames.skip(1).findFirst())
                .map(f -> f.getFileName() + ":" + f.getLineNumber())
                .orElse("?");
        System.out.println();
        System.out.println("[" + sectionNo + "." + (++stepNo) + "] " + where);
        System.out.println("    code:      " + source);
        try {
            T result = code.run();
            System.out.println("    result:    " + format(result));
            return result;
        } catch (Exception e) {
            System.out.println("    threw:     " + e.getClass().getName() + ": " + e.getMessage());
            return null;
        }
    }

    static String format(Object value) {
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        if (value instanceof String s) {
            return '"' + s + '"';
        }
        if (value instanceof byte[] bytes) {
            return describe(bytes);
        }
        if (value instanceof Class<?> c) {
            return "class " + c.getName() + " " + origin(c);
        }
        String className = value.getClass().getName();
        if (className.startsWith("v1.") || className.startsWith("v2.")) {
            // ACME objects: tag with the version, e.g. [v2] Order{...}
            return "[" + className.substring(0, 2) + "] " + value;
        }
        return "instance of " + className + " " + origin(value.getClass());
    }

    /** Printable payloads as text, binary ones as hex. */
    static String describe(byte[] payload) {
        boolean printable = true;
        for (byte b : payload) {
            printable &= b >= 0x20 && b < 0x7f;
        }
        if (printable) {
            return '"' + new String(payload, StandardCharsets.US_ASCII) + "\" (" + payload.length + " bytes)";
        }
        int shown = Math.min(payload.length, 24);
        return HexFormat.ofDelimiter(" ").formatHex(payload, 0, shown)
                + (payload.length > shown ? " ..." : "") + " (" + payload.length + " bytes)";
    }

    /** Where a class came from, e.g. {@code [acme-v1-shaded.jar]}. */
    static String origin(Class<?> c) {
        CodeSource src = c.getProtectionDomain().getCodeSource();
        if (src == null) {
            return "[JDK]";
        }
        String path = src.getLocation().getPath();
        return "[" + path.substring(path.lastIndexOf('/', path.length() - 2) + 1) + "]";
    }

    static void section(String title) {
        sectionNo++;
        stepNo = 0;
        System.out.println();
        System.out.println();
        System.out.println("=".repeat(90));
        System.out.println(sectionNo + ". " + title);
        System.out.println("=".repeat(90));
    }
}
