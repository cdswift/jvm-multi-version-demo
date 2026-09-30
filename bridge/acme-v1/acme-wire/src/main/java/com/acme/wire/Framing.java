package com.acme.wire;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Protocol 1.0 framing: each payload is followed by a newline. */
final class Framing {
    private Framing() {
    }

    static void write(OutputStream out, byte[] payload) throws IOException {
        out.write(payload);
        out.write('\n');
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != '\n') {
            if (b == -1) {
                throw new EOFException();
            }
            frame.write(b);
        }
        return frame.toByteArray();
    }
}
